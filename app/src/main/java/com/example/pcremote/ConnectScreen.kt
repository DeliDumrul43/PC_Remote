package com.example.pcremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pcremote.HidController.Phase

@Composable
fun ConnectScreen(
    hid: HidController,
    state: HidController.State,
    backgroundFailed: Boolean,
    onPairNew: () -> Unit,
    onTurnOn: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val version by hid.devicesVersion.collectAsState()
    val devices = remember(state, showAll, version) { hid.pairedDevices(computersOnly = !showAll) }
    val last = remember(state, version) { hid.lastHost() }
    // You may pick a PC while the app is auto-reconnecting to another one.
    val canAct = state.phase == Phase.READY || state.phase == Phase.CONNECTING
    val c = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.connect_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LanguageSwitch()
                KeyButton("⚙", Modifier.width(52.dp), height = 44.dp, onClick = onOpenSettings)
            }
        }

        StatusCard(state, onTurnOn)

        if (backgroundFailed) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WarningAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.warn_background), Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (canAct && last != null) {
            Button(
                onClick = { hid.connect(last) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    stringResource(R.string.reconnect_to, hid.label(last)),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.your_computers)) }
            TextButton(onClick = { hid.refreshDevices() }) { Text(stringResource(R.string.refresh_list)) }
        }
        if (devices.isEmpty()) {
            Text(
                stringResource(if (showAll) R.string.no_paired_devices else R.string.no_paired_computers),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant
            )
        }
        devices.forEach { d ->
            // The address tail tells apart devices with the same name (like your three "GoPro 8034").
            DeviceRow(hid.label(d), d.address.takeLast(5), computer = hid.isComputer(d), enabled = canAct) {
                hid.connect(d)
            }
        }
        TextButton(onClick = { showAll = !showAll }) {
            Text(stringResource(if (showAll) R.string.show_computers_only else R.string.show_all_devices))
        }

        PairCard(enabled = canAct, onPairNew = onPairNew)

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(stringResource(if (showDetails) R.string.hide_details else R.string.show_details))
        }
        if (showDetails) DetailsLog(hid)
    }
}

@Composable
private fun StatusCard(state: HidController.State, onTurnOn: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (color, title, pulsing) = when (state.phase) {
        Phase.READY -> Triple(c.primary, R.string.status_ready, false)
        Phase.STARTING -> Triple(WarningAmber, R.string.status_starting, true)
        Phase.CONNECTING -> Triple(WarningAmber, R.string.status_connecting, true)
        Phase.CONNECTED -> Triple(ConnectedGreen, R.string.status_connected, false)
        Phase.UNSUPPORTED -> Triple(c.error, R.string.status_unsupported, false)
        Phase.OFF -> Triple(c.onSurfaceVariant, R.string.status_off, false)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = c.surfaceContainer,
        border = BorderStroke(1.dp, c.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusDot(color, pulsing, size = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(state.message, state.arg ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
            }
            if (state.phase == Phase.OFF || state.phase == Phase.UNSUPPORTED) {
                FilledTonalButton(onClick = onTurnOn) { Text(stringResource(R.string.turn_on)) }
            }
        }
    }
}

@Composable
private fun DeviceRow(name: String, addressTail: String, computer: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = c.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(42.dp).background(c.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(if (computer) "💻" else "📶", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (computer) R.string.device_computer else R.string.device_other) + "  ·  " + addressTail,
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
            }
            Text("›", fontSize = 26.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun PairCard(enabled: Boolean, onPairNew: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, c.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Step(1, stringResource(R.string.pair_step1))
            Step(2, stringResource(R.string.pair_step2))
            Step(3, stringResource(R.string.pair_step3))
            Text(stringResource(R.string.pair_tip), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Text(stringResource(R.string.pair_tip_windows), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            OutlinedButton(
                onClick = onPairNew,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.make_visible)) }
        }
    }
}

@Composable
private fun DetailsLog(hid: HidController) {
    val lines by hid.log.collectAsState()
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.details_hint), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            lines.forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.onSurface)
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, // already uppercase in strings.xml: code uppercasing gets Turkish "İ" wrong
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp
    )
}