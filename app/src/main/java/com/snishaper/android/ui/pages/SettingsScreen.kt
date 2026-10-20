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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.data.AppPreferences
import com.snishaper.android.data.AppearanceMode
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.SettingRow
import com.snishaper.android.ui.components.SettingSwitchRow
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.rememberToast
import com.snishaper.android.ui.theme.SnishaperThemeCatalog

/**
 * Settings skeleton: General + Appearance + Startup sections. The desktop "Security / CA"
 * section is intentionally absent (zero CA surface), as are tray/update/log-file rows.
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
) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()
    var port by rememberSaveable { mutableStateOf("8080") }
    val languages = listOf("zh" to R.string.language_zh, "en" to R.string.language_en, "ru" to R.string.language_ru)
    val appearanceOptions = listOf(
        AppearanceMode.LIGHT to R.string.appearance_light,
        AppearanceMode.DARK to R.string.appearance_dark,
        AppearanceMode.SYSTEM to R.string.appearance_system,
    )

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Settings,
            titleRes = R.string.settings_title,
            subtitleRes = R.string.settings_subtitle,
        )

        SectionHeader(titleRes = R.string.settings_general, icon = Icons.Outlined.Memory)

        SettingRow(
            titleRes = R.string.settings_port,
            descRes = R.string.settings_port_desc,
            icon = Icons.Outlined.Language,
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
                    Button(onClick = { toast(comingSoon) }) {
                        Text(stringResource(id = R.string.common_apply))
                    }
                }
            },
        )

        SettingRow(
            titleRes = R.string.settings_language,
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

        SettingRow(
            titleRes = R.string.theme_title,
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

        Spacer(modifier = Modifier.width(1.dp))
        SkeletonNote()
        Text(
            text = "端口/规则等引擎设置将由 Go core 持久化，当前仅保留 UI 骨架。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
