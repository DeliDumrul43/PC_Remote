package com.example.pcremote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var hid: HidController

    /** True if the background service couldn't start; shown as a warning card. */
    private var backgroundFailed by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) turnOn(fresh = false)
        else hid.permissionDenied()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (hid.isBluetoothOn()) startBluetooth(fresh = true) else hid.start() }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // For this dialog, Android returns the visible duration in seconds, or RESULT_CANCELED.
        if (result.resultCode == RESULT_CANCELED) hid.note("Visibility request was declined")
        else hid.note("Phone is visible for ${result.resultCode} s — add it from Windows now")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        hid = HidController.get(this)
        setContent {
            RemoteTheme {
                App(
                    hid = hid,
                    backgroundFailed = backgroundFailed,
                    onPairNew = ::makeDiscoverable,
                    onTurnOn = { turnOn(fresh = true) },
                )
            }
        }
        if (savedInstanceState == null) turnOn(fresh = false)
    }

    override fun onResume() {
        super.onResume()
        // Back in the app: continue from wherever we are (re-registers if Android dropped us).
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) startBluetooth(fresh = false)
    }

    /** What the Turn on button does. Each missing piece leads to a visible step. */
    private fun turnOn(fresh: Boolean) {
        when {
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT) ->
                permissionLauncher.launch(requiredPermissions())
            !hid.isBluetoothOn() ->
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> startBluetooth(fresh)
        }
    }

    private fun startBluetooth(fresh: Boolean) {
        // 1) Start Bluetooth right here: the app is on screen, so Android allows it.
        if (fresh) hid.restart() else hid.start()
        // 2) The service only keeps it alive after you leave the app.
        backgroundFailed = !HidService.start(this)
    }

    private fun makeDiscoverable() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        discoverableLauncher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        )
    }

    /** Bluetooth runtime permissions only exist on Android 12+. */
    private fun hasPermission(permission: String): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

@Composable
private fun App(
    hid: HidController,
    backgroundFailed: Boolean,
    onPairNew: () -> Unit,
    onTurnOn: () -> Unit,
) {
    val state by hid.state.collectAsState()
    var speed by remember { mutableFloatStateOf(hid.pointerSpeed) }
    var showSettings by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Crossfade(targetState = state.phase == HidController.Phase.CONNECTED, label = "screen") { connected ->
            if (connected) {
                ControlScreen(hid, state.arg ?: "PC", speed, onOpenSettings = { showSettings = true })
            } else {
                ConnectScreen(
                    hid, state, backgroundFailed, onPairNew, onTurnOn,
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            hid = hid,
            speed = speed,
            onSpeedChange = { speed = it; hid.pointerSpeed = it },
            onDismiss = { showSettings = false }
        )
    }
}