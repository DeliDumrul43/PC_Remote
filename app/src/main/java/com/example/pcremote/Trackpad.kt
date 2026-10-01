package com.example.pcremote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

private const val BASE_GAIN = 1.2f          // base pointer speed (multiplied by the user's setting)
private const val ACCEL = 0.04f             // faster swipe = proportionally more distance
private const val SCROLL_PX_PER_STEP = 40f  // finger pixels per wheel notch
private const val TAP_MAX_MS = 250L

/**
 * 1 finger move  -> pointer move
 * 1 finger tap   -> left click
 * 2 finger tap   -> right click
 * 2 finger swipe -> scroll (phone style: swipe up = page moves up)
 */
@Composable
fun Trackpad(
    hid: HidController,
    speed: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptic = rememberHaptic()
    val fingers = remember { mutableStateListOf<Offset>() }
    val c = MaterialTheme.colorScheme
    val glow = c.primary
    val dots = c.outlineVariant
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.surfaceContainerHigh, c.surfaceContainer)))
            .border(1.dp, c.outlineVariant, shape)
            .drawBehind {
                // subtle dot grid so it reads as a touch surface
                val step = 26.dp.toPx()
                val r = 1.2.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) { drawCircle(dots, r, Offset(x, y)); x += step }
                    y += step
                }
                // soft glow under each finger
                val glowR = 64.dp.toPx()
                fingers.forEach { p ->
                    drawCircle(
                        Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), center = p, radius = glowR),
                        radius = glowR, center = p
                    )
                }
            }
            .pointerInput(speed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var maxFingers = 1
                    var travelled = 0f
                    var remX = 0f; var remY = 0f; var scrollRem = 0f
                    var lastTime = down.uptimeMillis

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        maxFingers = maxOf(maxFingers, pressed.size)
                        lastTime = event.changes.first().uptimeMillis
                        fingers.clear(); fingers.addAll(pressed.map { it.position })

                        if (pressed.size == 1 && maxFingers == 1) {
                            val d = pressed[0].positionChange()
                            val dist = hypot(d.x, d.y)
                            travelled += dist
                            val gain = BASE_GAIN * speed * (1f + ACCEL * dist)
                            // keep the fractional part so slow, precise moves aren't lost
                            remX += d.x * gain; remY += d.y * gain
                            val sx = remX.toInt(); val sy = remY.toInt()
                            if (sx != 0 || sy != 0) { hid.move(sx, sy); remX -= sx; remY -= sy }
                        } else if (pressed.size >= 2) {
                            val dy = pressed.map { it.positionChange().y }.average().toFloat()
                            travelled += abs(dy)
                            scrollRem += dy
                            val steps = (scrollRem / SCROLL_PX_PER_STEP).toInt()
                            if (steps != 0) { hid.scroll(steps); scrollRem -= steps * SCROLL_PX_PER_STEP }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    fingers.clear()
                    val isTap = travelled < viewConfiguration.touchSlop &&
                        lastTime - down.uptimeMillis < TAP_MAX_MS
                    if (isTap) {
                        haptic()
                        hid.click(if (maxFingers >= 2) HidController.BTN_RIGHT else HidController.BTN_LEFT)
                    }
                }
            }
    ) {
        if (!compact) {
            Text(
                stringResource(R.string.trackpad_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}
