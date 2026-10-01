package com.example.pcremote

import android.view.HapticFeedbackConstants
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

/**
 * Dark by default: this app is used in a living room, often with the lights down,
 * and a bright white screen next to the TV is unpleasant.
 */
private val RemoteColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF223B6A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF6EE7C8),
    onSecondary = Color(0xFF00382C),
    background = Color(0xFF0D0F13),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF0D0F13),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF1B1F27),
    onSurfaceVariant = Color(0xFFA3AAB8),
    surfaceContainer = Color(0xFF15181E),
    surfaceContainerHigh = Color(0xFF1D2129),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF282D37),
    error = Color(0xFFFF8A80),
)

val ConnectedGreen = Color(0xFF4ADE80)
val WarningAmber = Color(0xFFFBBF24)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RemoteColors, content = content)
}

/** A light "tick" vibration, so buttons feel physical even without looking. */
@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        val tick: () -> Unit = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        tick
    }
}
