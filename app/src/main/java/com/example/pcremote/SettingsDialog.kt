package com.example.pcremote

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    hid: HidController,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var layout by remember { mutableStateOf(hid.layout) }
    var language by remember { mutableStateOf(currentAppLanguage(context)) }
    val c = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pointer speed
                SettingTitle(stringResource(R.string.pointer_speed))
                Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.4f..3f)
                Text(
                    stringResource(R.string.pointer_speed_hint, "%.1f".format(speed)),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )

                HorizontalDivider(color = c.outlineVariant)

                // App language (per-app language needs Android 13+)
                SettingTitle(stringResource(R.string.app_language))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        "" to stringResource(R.string.lang_system),
                        "en" to "English",
                        "tr" to "Türkçe",
                    ).forEach { (tag, label) ->
                        Choice(label, selected = language == tag) {
                            language = tag
                            setAppLanguage(context, tag) // the screen reloads in the new language
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.app_language_old),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = c.outlineVariant)

                // PC keyboard layout
                SettingTitle(stringResource(R.string.pc_layout))
                Choice(stringResource(R.string.layout_us), selected = layout == PcLayout.US) {
                    layout = PcLayout.US; hid.layout = PcLayout.US
                }
                Choice(stringResource(R.string.layout_tr), selected = layout == PcLayout.TR_Q) {
                    layout = PcLayout.TR_Q; hid.layout = PcLayout.TR_Q
                }
                Text(
                    stringResource(R.string.pc_layout_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun SettingTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "" = follow the phone, "en", or "tr". */
fun currentAppLanguage(context: Context): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else ""

fun setAppLanguage(context: Context, tag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }
}

/** Always-visible EN | TR switch. Per-app language needs Android 13+, so it hides itself on older phones. */
@Composable
fun LanguageSwitch() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val shown = LocalConfiguration.current.locales[0].language // language the screen is in right now
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyButton("EN", Modifier.width(52.dp), selected = shown == "en", height = 44.dp) {
            setAppLanguage(context, "en")
        }
        KeyButton("TR", Modifier.width(52.dp), selected = shown == "tr", height = 44.dp) {
            setAppLanguage(context, "tr")
        }
    }
}