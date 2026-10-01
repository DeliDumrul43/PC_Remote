package com.example.pcremote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Rounded keyboard-style button with haptic feedback. */
@Composable
fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: Dp = 52.dp,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = { haptic(); onClick() },
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.primaryContainer else c.surfaceContainerHigh,
        contentColor = if (selected) c.onPrimaryContainer else c.onSurface,
        border = BorderStroke(1.dp, if (selected) c.primary else c.outlineVariant),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Big circular button for the media remote. */
@Composable
fun RoundButton(
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    fontSize: TextUnit = 22.sp,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = { haptic(); onClick() },
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (primary) c.primary else c.surfaceContainerHigh,
        contentColor = if (primary) c.onPrimary else c.onSurface,
        border = if (primary) null else BorderStroke(1.dp, c.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Small colored dot; pulses while something is in progress. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 10.dp) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "alpha"
        ).value
    } else 1f
    Box(Modifier.size(size).alpha(alpha).background(color, CircleShape))
}
