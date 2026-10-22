package com.snishaper.android.ui.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snishaper.android.R
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.data.AppPreferences
import com.snishaper.android.data.AppearanceMode
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.SettingSwitchRow
import com.snishaper.android.ui.components.SettingTextRow
import com.snishaper.android.ui.components.rememberToast
import com.snishaper.android.ui.theme.SnishaperThemeCatalog
import kotlinx.coroutines.launch

/**
 * Settings page: General (port, language) + Appearance (mode, theme, dynamic color) +
 * Startup sections. The port is read/written directly against the engine settings JSON
 * (persisted by the Go core's settings.json). The desktop "Security / CA" section is
 * intentionally absent (zero CA surface), as are tray/update/log-file rows.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    preferences: AppPreferences,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onAutoStartProxyChange: (Boolean) -> Unit,
    onAutoUpdateRulesChange: (Boolean) -> Unit,
    onUseDynamicColorChange: (Boolean) -> Unit,
) {
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    val settings by com.snishaper.android.core.SnishaperEngine.settings.collectAsStateWithLifecycle()
    var port by rememberSaveable { mutableStateOf("") }
    var portSynced by rememberSaveable { mutableStateOf(false) }

    // Sync the engine's current listen port into the text field exactly once per load.
    LaunchedEffect(settings.listenPort) {
        if (!portSynced && settings.listenPort.isNotBlank()) {
            port = settings.listenPort
            portSynced = true
        }
    }

    val languages = listOf("zh" to R.string.language_zh, "en" to R.string.language_en, "ru" to R.string.language_ru)
    val appearanceOptions = listOf(
        AppearanceMode.LIGHT to R.string.appearance_light,
        AppearanceMode.DARK to R.string.appearance_dark,
        AppearanceMode.SYSTEM to R.string.appearance_system,
    )
    val appliedText = stringResource(R.string.settings_port_applied)

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Settings,
            titleRes = R.string.settings_title,
            subtitleRes = R.string.settings_subtitle,
        )

        SectionHeader(titleRes = R.string.settings_general, icon = Icons.Outlined.Memory)

        SettingTextRow(
            title = stringResource(R.string.settings_port),
            desc = stringResource(R.string.settings_port_desc),
            icon = Icons.Outlined.Memory,
            trailing = {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(
                        enabled = port.isNotBlank() && (port.toIntOrNull() ?: 0) in 1024..65535,
                        onClick = {
                            val newPort = port.toIntOrNull() ?: return@Button
                            scope.launch {
                                runCatching { SnishaperEngine.setListenPort(newPort) }
                                    .onSuccess { toast(appliedText) }
                                    .onFailure { toast(it.message ?: "操作失败") }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_apply))
                    }
                }
            },
        )

        SettingTextRow(
            title = stringResource(R.string.settings_language),
            icon = Icons.Outlined.Language,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            languages.forEach { (code, labelRes) ->
                FilterChip(
                    selected = preferences.language == code,
                    onClick = { onLanguageChange(code) },
                    label = { Text(stringResource(id = labelRes)) },
                )
            }
        }

        SectionHeader(titleRes = R.string.appearance_title, icon = Icons.Outlined.Palette)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            appearanceOptions.forEach { (mode, labelRes) ->
                FilterChip(
                    selected = preferences.appearance == mode,
                    onClick = { onAppearanceChange(mode) },
                    label = { Text(stringResource(id = labelRes)) },
                )
            }
        }

        SettingTextRow(
            title = stringResource(R.string.theme_title),
            icon = Icons.Outlined.Palette,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnishaperThemeCatalog.themes.forEach { theme ->
                FilterChip(
                    selected = preferences.themeId == theme.id,
                    onClick = { onThemeChange(theme.id) },
                    label = { Text(stringResource(id = theme.labelRes)) },
                )
            }
        }
        SettingSwitchRow(
            titleRes = R.string.settings_dynamic_color_title,
            descRes = R.string.settings_dynamic_color_desc,
            icon = Icons.Outlined.Palette,
            checked = preferences.useDynamicColor,
            onCheckedChange = onUseDynamicColorChange,
        )

        SectionHeader(titleRes = R.string.settings_startup, icon = Icons.Outlined.PlayArrow)
        SettingSwitchRow(
            titleRes = R.string.settings_auto_start_proxy,
            descRes = R.string.settings_auto_start_proxy_desc,
            icon = Icons.Outlined.PlayArrow,
            checked = preferences.autoStartProxy,
            onCheckedChange = onAutoStartProxyChange,
        )
        SettingSwitchRow(
            titleRes = R.string.settings_auto_update_rules,
            descRes = R.string.settings_auto_update_rules_desc,
            icon = Icons.Outlined.Refresh,
            checked = preferences.autoUpdateRules,
            onCheckedChange = onAutoUpdateRulesChange,
        )
    }
}
