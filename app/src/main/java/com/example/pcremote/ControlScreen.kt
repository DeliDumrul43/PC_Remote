package com.example.pcremote

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class RemoteTab(@StringRes val label: Int, val icon: String) {
    PAD(R.string.tab_touchpad, "🖱️"), KEYS(R.string.tab_keyboard, "⌨️"), MEDIA(R.string.tab_media, "🎵")
}

@Composable
fun ControlScreen(hid: HidController, hostName: String, speed: Float, onOpenSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(RemoteTab.PAD) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding() // includes the on-screen keyboard, so nothing hides behind it
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBar(hostName, onOpenSettings, onDisconnect = { hid.disconnect() })
        TabBar(tab) { tab = it }
        when (tab) {
            RemoteTab.PAD -> PadTab(hid, speed, Modifier.weight(1f))
            RemoteTab.KEYS -> KeysTab(hid, speed, Modifier.weight(1f))
            RemoteTab.MEDIA -> MediaTab(hid, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun TopBar(hostName: String, onSettings: () -> Unit, onDisconnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusDot(ConnectedGreen, pulsing = false)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.status_connected), style = MaterialTheme.typography.labelSmall, color = ConnectedGreen)
            Text(
                hostName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        KeyButton("⚙", Modifier.width(52.dp), height = 40.dp, onClick = onSettings)
        KeyButton(stringResource(R.string.disconnect), height = 40.dp, onClick = onDisconnect)
    }
}

@Composable
private fun TabBar(selected: RemoteTab, onSelect: (RemoteTab) -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surfaceContainer,
        border = BorderStroke(1.dp, c.outlineVariant)
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteTab.entries.forEach { t ->
                val active = t == selected
                Surface(
                    onClick = { haptic(); onSelect(t) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) c.primaryContainer else Color.Transparent,
                    contentColor = if (active) c.onPrimaryContainer else c.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${t.icon}  ${stringResource(t.label)}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- touchpad tab

@Composable
private fun PadTab(hid: HidController, speed: Float, modifier: Modifier) {
    var dragging by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Trackpad(hid, speed, Modifier.fillMaxWidth().weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(stringResource(R.string.btn_left), Modifier.weight(1f), height = 60.dp) {
                hid.click(HidController.BTN_LEFT)
            }
            KeyButton(
                stringResource(if (dragging) R.string.btn_release else R.string.btn_drag),
                Modifier.weight(0.7f), selected = dragging, height = 60.dp
            ) { dragging = !dragging; hid.setLeftHeld(dragging) }
            KeyButton(stringResource(R.string.btn_right), Modifier.weight(1f), height = 60.dp) {
                hid.click(HidController.BTN_RIGHT)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                // Alt+Left / Alt+Right = previous / next page in every browser and File Explorer
                stringResource(R.string.key_page_back) to { hid.key(KeyMap.LEFT, KeyMap.MOD_ALT) },
                stringResource(R.string.key_page_forward) to { hid.key(KeyMap.RIGHT, KeyMap.MOD_ALT) },
                "Esc" to { hid.key(KeyMap.ESC) },
                "Win" to { hid.key(0, KeyMap.MOD_WIN) },
                "Alt+Tab" to { hid.key(KeyMap.TAB, KeyMap.MOD_ALT) },
                stringResource(R.string.key_full_screen) to { hid.key(KeyMap.F11) },
                stringResource(R.string.key_space) to { hid.key(KeyMap.SPACE) },
                stringResource(R.string.key_refresh) to { hid.key(KeyMap.F5) },
                "Alt+F4" to { hid.key(KeyMap.F4, KeyMap.MOD_ALT) },
            ).forEach { (label, action) ->
                KeyButton(label, Modifier.widthIn(min = 72.dp), height = 44.dp, onClick = action)
            }
        }
    }
}

// ---------------------------------------------------------------- keyboard tab

@Composable
private fun KeysTab(hid: HidController, speed: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Trackpad(hid, speed, Modifier.fillMaxWidth().weight(1f), compact = true)
        LiveKeyboardField(hid)
        KeyRow(
            "Esc" to { hid.key(KeyMap.ESC) },
            "Tab" to { hid.key(KeyMap.TAB) },
            "⌫" to { hid.key(KeyMap.BACKSPACE) },
            "Del" to { hid.key(KeyMap.DELETE) },
            "⏎" to { hid.key(KeyMap.ENTER) },
        )
        KeyRow(
            "Ctrl+A" to { hid.key(KeyMap.letter('a'), KeyMap.MOD_CTRL) },
            "Ctrl+C" to { hid.key(KeyMap.letter('c'), KeyMap.MOD_CTRL) },
            "Ctrl+V" to { hid.key(KeyMap.letter('v'), KeyMap.MOD_CTRL) },
            "Ctrl+Z" to { hid.key(KeyMap.letter('z'), KeyMap.MOD_CTRL) },
            "Win" to { hid.key(0, KeyMap.MOD_WIN) },
        )
        KeyRow(
            "Home" to { hid.key(KeyMap.HOME) },
            "←" to { hid.key(KeyMap.LEFT) },
            "↑" to { hid.key(KeyMap.UP) },
            "↓" to { hid.key(KeyMap.DOWN) },
            "→" to { hid.key(KeyMap.RIGHT) },
            "End" to { hid.key(KeyMap.END) },
        )
    }
}

@Composable
private fun KeyRow(vararg keys: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { (label, action) ->
            KeyButton(label, Modifier.weight(1f), height = 46.dp, onClick = action)
        }
    }
}

/**
 * Whatever you type here is typed on the PC live.
 * Compares old vs new text, so typing, deleting and autocorrect replacements all work.
 */
@Composable
private fun LiveKeyboardField(hid: HidController) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val common = text.commonPrefixWith(new).length
            repeat(text.length - common) { hid.key(KeyMap.BACKSPACE) }
            hid.type(new.substring(common))
            text = if (new.length > 200) "" else new
        },
        placeholder = { Text(stringResource(R.string.type_here)) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { hid.key(KeyMap.ENTER); text = "" }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
}

// ---------------------------------------------------------------- media tab

@Composable
private fun MediaTab(hid: HidController, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            RoundButton("⏮", 72.dp) { hid.media(KeyMap.PREV_TRACK) }
            RoundButton("⏯", 124.dp, primary = true, fontSize = 42.sp) { hid.media(KeyMap.PLAY_PAUSE) }
            RoundButton("⏭", 72.dp) { hid.media(KeyMap.NEXT_TRACK) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                RoundButton("−", 72.dp, fontSize = 34.sp) { hid.media(KeyMap.VOL_DOWN) }
                RoundButton("🔇", 60.dp) { hid.media(KeyMap.MUTE) }
                RoundButton("+", 72.dp, fontSize = 34.sp) { hid.media(KeyMap.VOL_UP) }
            }
            Text(stringResource(R.string.volume), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, letterSpacing = 1.5.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(stringResource(R.string.seek_back), Modifier.weight(1f)) { hid.key(KeyMap.LEFT) }
            KeyButton(stringResource(R.string.key_full_screen), Modifier.weight(1f)) { hid.key(KeyMap.F11) }
            KeyButton(stringResource(R.string.seek_forward), Modifier.weight(1f)) { hid.key(KeyMap.RIGHT) }
        }
        Text(
            stringResource(R.string.media_hint),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}