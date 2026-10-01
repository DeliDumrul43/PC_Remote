package com.example.pcremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Owns the Bluetooth HID connection. One instance for the whole app.
 *
 * Rule of this class: every action ends in a visible state. Nothing may wait forever;
 * every wait has a timeout that turns into a message the user can act on.
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT first
class HidController private constructor(private val context: Context) {

    companion object {
        const val BTN_LEFT = 1
        const val BTN_RIGHT = 2
        private const val TAG = "PCRemote"           // filter Logcat with  tag:PCRemote
        private const val START_TIMEOUT_MS = 6_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L

        @Volatile private var instance: HidController? = null
        fun get(context: Context): HidController =
            instance ?: synchronized(this) {
                instance ?: HidController(context.applicationContext).also { instance = it }
            }
    }

    enum class Phase { OFF, STARTING, READY, CONNECTING, CONNECTED, UNSUPPORTED }

    /** message is a string resource so the UI can show it in the app's language. */
    data class State(val phase: Phase, @StringRes val message: Int, val arg: String? = null)

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = context.getSharedPreferences("pc_remote", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var pendingDevice: BluetoothDevice? = null
    @Volatile private var registered = false
    @Volatile private var proxyRequested = false
    @Volatile private var held = 0
    @Volatile private var wantRunning = false   // false after "Turn off"
    private var receiverRegistered = false

    private val _devicesVersion = MutableStateFlow(0)
    /** Bumped whenever the paired-device list may have changed; the UI re-reads the list. */
    val devicesVersion: StateFlow<Int> = _devicesVersion

    fun refreshDevices() {
        log("Refreshing the device list")
        _devicesVersion.update { it + 1 }
    }

    /** Lets the screen add lines to the event log (e.g. the visibility dialog result). */
    fun note(msg: String) = log(msg)

    private val _state = MutableStateFlow(State(Phase.OFF, R.string.msg_tap_turn_on))
    val state: StateFlow<State> = _state

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Last 30 events, shown under "Show details". */
    val log: StateFlow<List<String>> = _log

    var pointerSpeed: Float
        get() = prefs.getFloat("pointer_speed", 1f)
        set(value) = prefs.edit().putFloat("pointer_speed", value).apply()

    var layout: PcLayout
        get() = runCatching { PcLayout.valueOf(prefs.getString("pc_layout", null) ?: "US") }.getOrDefault(PcLayout.US)
        set(value) = prefs.edit().putString("pc_layout", value.name).apply()

    fun isBluetoothOn() = adapter?.isEnabled == true

    // ------------------------------------------------------------------ start / stop

    /** Safe to call any number of times; continues from wherever we are. */
    fun start() {
        val a = adapter ?: return set(Phase.UNSUPPORTED, R.string.msg_no_bluetooth)
        if (!a.isEnabled) {
            log("start(): Bluetooth is off")
            return set(Phase.OFF, R.string.msg_bt_off)
        }
        wantRunning = true
        registerReceiverOnce()
        val h = hid
        when {
            h != null && registered -> {
                log("start(): already registered")
                if (_state.value.phase == Phase.OFF) set(Phase.READY, R.string.msg_ready)
            }
            h != null -> register(h)
            !proxyRequested -> openProxy(a)
            else -> log("start(): still waiting for the HID service")
        }
    }

    /** The Turn on button: throw away any half-broken state and start from zero. */
    fun restart() {
        log("restart()")
        release()
        start()
    }

    /** Called when the background service stops ("Turn off" in the notification). */
    fun stop() {
        log("stop()")
        wantRunning = false
        release()
        set(Phase.OFF, R.string.msg_tap_turn_on)
    }

    fun permissionDenied() {
        log("Nearby devices permission denied")
        set(Phase.OFF, R.string.msg_need_permission)
    }

    private fun release() {
        main.removeCallbacks(startTimeout)
        main.removeCallbacks(connectTimeout)
        val h = hid
        try {
            host?.let { h?.disconnect(it) }
            if (registered) h?.unregisterApp()
            h?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (e: Exception) {
            log("release() error: ${e.message}")
        }
        hid = null; host = null; pendingDevice = null
        registered = false; proxyRequested = false; held = 0
    }

    private fun openProxy(a: BluetoothAdapter) {
        log("Opening the Bluetooth HID profile…")
        runCatching { log("This phone shows up on the PC as \"${a.name}\"") }
        proxyRequested = a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        if (proxyRequested) {
            set(Phase.STARTING, R.string.msg_starting)
            armStartTimeout()
        } else {
            log("getProfileProxy() refused: phone has no HID Device profile")
            set(Phase.UNSUPPORTED, R.string.msg_unsupported)
        }
    }

    private val sdp by lazy {
        BluetoothHidDeviceAppSdpSettings(
            "PC Remote", "Phone touchpad and keyboard", "PC Remote",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.DESCRIPTOR
        )
    }

    private fun register(h: BluetoothHidDevice, retry: Boolean = true) {
        log("Registering as keyboard + mouse…")
        set(Phase.STARTING, R.string.msg_starting)
        armStartTimeout()
        val ok = try {
            h.registerApp(sdp, null, null, executor, callback)
        } catch (e: Exception) {
            log("registerApp() error: ${e.message}"); false
        }
        if (ok) return

        main.removeCallbacks(startTimeout)
        if (retry) {
            // Usual cause: an old registration from our app is still stuck. Clear it and try once more.
            log("registerApp() refused — clearing old registration and retrying")
            runCatching { h.unregisterApp() }
            main.postDelayed({ hid?.let { register(it, retry = false) } }, 600)
        } else {
            log("registerApp() refused twice — another HID app may be active")
            set(Phase.OFF, R.string.msg_register_failed)
        }
    }

    private fun armStartTimeout() {
        main.removeCallbacks(startTimeout)
        main.postDelayed(startTimeout, START_TIMEOUT_MS)
    }

    private val startTimeout = Runnable {
        if (_state.value.phase == Phase.STARTING) {
            log("Timeout: Bluetooth HID never answered")
            proxyRequested = false
            set(Phase.OFF, R.string.msg_no_answer)
        }
    }

    private val connectTimeout = Runnable {
        if (_state.value.phase == Phase.CONNECTING) {
            val d = pendingDevice
            log("Timeout: ${d?.let(::label)} did not accept the connection")
            d?.let { runCatching { hid?.disconnect(it) } }
            pendingDevice = null
            set(Phase.READY, R.string.msg_pc_no_answer, d?.let(::label))
        }
    }

    // ------------------------------------------------------------------ devices

    fun pairedDevices(computersOnly: Boolean): List<BluetoothDevice> = try {
        adapter?.bondedDevices.orEmpty()
            .filter { !computersOnly || isComputer(it) }
            .sortedByDescending { isComputer(it) }
    } catch (e: SecurityException) {
        emptyList()
    }

    fun isComputer(d: BluetoothDevice) =
        d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER

    fun lastHost(): BluetoothDevice? {
        val address = prefs.getString("last_host", null) ?: return null
        return pairedDevices(computersOnly = false).firstOrNull { it.address == address }
    }

    fun label(d: BluetoothDevice): String = try { d.name ?: d.address } catch (e: SecurityException) { d.address }

    fun connect(device: BluetoothDevice) {
        val h = hid
        if (h == null || !registered) {
            log("connect(): not registered yet — starting first")
            start()
            return
        }
        pendingDevice = device
        set(Phase.CONNECTING, R.string.msg_connecting, label(device))
        val ok = runCatching { h.connect(device) }.getOrDefault(false)
        log("connect(${label(device)}) -> $ok")
        if (!ok) {
            set(Phase.READY, R.string.msg_connect_failed)
            return
        }
        main.removeCallbacks(connectTimeout)
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    fun disconnect() {
        log("disconnect()")
        host?.let { hid?.disconnect(it) }
    }

    // ------------------------------------------------------------------ system Bluetooth events

    /** Watches pairing and radio events so the device list and log stay live. */
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = intent.device() ?: return
                    val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    log("Pairing with ${label(d)}: ${bondName(bond)}")
                    _devicesVersion.update { it + 1 }
                    // Windows normally connects by itself right after pairing. If it hasn't after 3 s, we do.
                    if (bond == BluetoothDevice.BOND_BONDED && isComputer(d)) {
                        main.postDelayed({ if (_state.value.phase == Phase.READY) connect(d) }, 3_000)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED ->
                    intent.device()?.let { log("Radio link up with ${label(it)}") }
                BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    intent.device()?.let { log("Radio link down with ${label(it)}") }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> {
                            log("Bluetooth was turned off")
                            release()
                            set(Phase.OFF, R.string.msg_bt_off)
                        }
                        BluetoothAdapter.STATE_ON -> if (wantRunning) {
                            log("Bluetooth was turned on — starting again")
                            start()
                        }
                    }
                }
            }
        }
    }

    private fun registerReceiverOnce() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(btReceiver, filter)
        }
        receiverRegistered = true
    }

    @Suppress("DEPRECATION")
    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun bondName(b: Int) = when (b) {
        BluetoothDevice.BOND_BONDING -> "in progress…"
        BluetoothDevice.BOND_BONDED -> "paired ✓"
        BluetoothDevice.BOND_NONE -> "not paired"
        else -> "state $b"
    }

    // ------------------------------------------------------------------ callbacks

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("HID profile opened")
            val h = proxy as BluetoothHidDevice
            hid = h
            register(h)
        }

        override fun onServiceDisconnected(profile: Int) {
            log("HID profile closed by the system")
            hid = null; host = null; registered = false; proxyRequested = false
            set(Phase.OFF, R.string.msg_bt_stopped)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            log("Registered = $registered" + (pluggedDevice?.let { ", last PC = ${label(it)}" } ?: ""))
            this@HidController.registered = registered
            if (!registered) {
                host = null
                // During a restart this "false" is expected; the start timeout still guards us.
                if (_state.value.phase != Phase.STARTING) set(Phase.OFF, R.string.msg_tap_turn_on)
                return
            }
            main.removeCallbacks(startTimeout)
            set(Phase.READY, R.string.msg_ready)
            (pluggedDevice ?: lastHost())?.let { d -> main.post { connect(d) } } // auto-reconnect
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            log("${label(device)}: ${stateName(state)}")
            when (state) {
                BluetoothProfile.STATE_CONNECTING ->
                    set(Phase.CONNECTING, R.string.msg_connecting, label(device))
                BluetoothProfile.STATE_CONNECTED -> {
                    main.removeCallbacks(connectTimeout)
                    host = device; pendingDevice = null
                    prefs.edit().putString("last_host", device.address).apply()
                    set(Phase.CONNECTED, R.string.msg_connected, label(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (host == null || host == device) {
                    val wasConnecting = _state.value.phase == Phase.CONNECTING
                    main.removeCallbacks(connectTimeout)
                    host = null; pendingDevice = null; held = 0
                    if (registered) {
                        set(
                            Phase.READY,
                            if (wasConnecting) R.string.msg_pc_no_answer else R.string.msg_not_connected,
                            label(device)
                        )
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) {
                HidDescriptor.ID_KEYBOARD -> 8
                HidDescriptor.ID_MOUSE -> 4
                HidDescriptor.ID_MEDIA -> 2
                else -> 0
            }
            if (size > 0) hid?.replyReport(device, type, id, ByteArray(size))
            else hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        }
    }

    private fun stateName(s: Int) = when (s) {
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
        else -> "state $s"
    }

    private fun set(phase: Phase, @StringRes message: Int, arg: String? = null) {
        _state.value = State(phase, message, arg)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (it + "$time  $msg").takeLast(30) }
    }

    // ------------------------------------------------------------------ sending

    private fun send(id: Int, data: ByteArray) {
        val h = host ?: return
        hid?.sendReport(h, id, data)
    }

    private fun mouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        var x = dx; var y = dy; var w = wheel
        do { // one report carries only -127..127, so split big moves
            val sx = x.coerceIn(-127, 127)
            val sy = y.coerceIn(-127, 127)
            val sw = w.coerceIn(-127, 127)
            send(HidDescriptor.ID_MOUSE, byteArrayOf(buttons.toByte(), sx.toByte(), sy.toByte(), sw.toByte()))
            x -= sx; y -= sy; w -= sw
        } while (x != 0 || y != 0 || w != 0)
    }

    fun move(dx: Int, dy: Int) = mouseReport(held, dx, dy, 0)

    /** Positive = scroll up, negative = scroll down. */
    fun scroll(steps: Int) = mouseReport(held, 0, 0, steps)

    fun click(button: Int) {
        mouseReport(held or button, 0, 0, 0)
        mouseReport(held, 0, 0, 0)
    }

    fun setLeftHeld(on: Boolean) {
        held = if (on) BTN_LEFT else 0
        mouseReport(held, 0, 0, 0)
    }

    fun key(code: Int, modifiers: Int = 0) {
        send(HidDescriptor.ID_KEYBOARD, byteArrayOf(modifiers.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0))
        send(HidDescriptor.ID_KEYBOARD, ByteArray(8))
    }

    /** Types text using the PC keyboard layout chosen in Settings. */
    fun type(text: String) {
        val l = layout
        for (c in text) KeyMap.strokes(c, l).forEach { (mods, code) -> key(code, mods) }
    }

    fun media(usage: Int) {
        send(HidDescriptor.ID_MEDIA, byteArrayOf((usage and 0xFF).toByte(), (usage shr 8).toByte()))
        send(HidDescriptor.ID_MEDIA, ByteArray(2))
    }
}