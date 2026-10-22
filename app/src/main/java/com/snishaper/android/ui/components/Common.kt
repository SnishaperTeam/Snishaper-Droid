package com.snishaper.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import kotlinx.coroutines.launch

/** Global snackbar host, provided by the main scaffold. */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/** Convenience launcher for the global snackbar. */
@Composable
fun rememberToast(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val hostState = LocalSnackbarHostState.current
    return { message ->
        scope.launch { hostState?.showSnackbar(message) }
    }
}

/** Scrollable page body (standard padding). */
@Composable
fun PageColumn(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/**
 * Page header: plain M3 icon + title (+ optional subtitle), then optional action row.
 * (Pure Material 3: default tinting, no custom capsule.)
 */
@Composable
fun PageHeader(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                if (subtitleRes != null) {
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Plain M3 section label with optional trailing action. */
@Composable
fun SectionHeader(
    @StringRes titleRes: Int,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Plain M3 list row inside a Card (desktop SettingRow equivalent). */
@Composable
fun SettingRow(
    @StringRes titleRes: Int,
    @StringRes descRes: Int? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (descRes != null) {
                    Text(
                        text = stringResource(descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Variant of [SettingRow] taking raw text (dynamic strings from engine data). */
@Composable
fun SettingTextRow(
    title: String,
    desc: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!desc.isNullOrEmpty()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Plain M3 switch row inside a Card. */
@Composable
fun SettingSwitchRow(
    @StringRes titleRes: Int,
    @StringRes descRes: Int? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        titleRes = titleRes,
        descRes = descRes,
        icon = icon,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/** Plain M3 empty state: centered icon + texts, no custom dashed box. */
@Composable
fun EmptyState(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes messageRes: Int? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (messageRes != null) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Plain M3 badge (elevated Surface with the standard small shape). */
@Composable
fun StatusPill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One editable field inside [TextFieldDialog]. */
data class DialogField(
    val label: String,
    val initial: String = "",
    val singleLine: Boolean = true,
    val minLines: Int = 1,
    val keyboardType: KeyboardType = KeyboardType.Text,
)

/** Multi-field text dialog (standard M3 AlertDialog + OutlinedTextField). */
@Composable
fun TextFieldDialog(
    title: String,
    fields: List<DialogField>,
    confirmText: String = stringResource(R.string.common_save),
    dismissText: String = stringResource(R.string.common_cancel),
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(fields) {
        mutableStateListOf(*fields.map { it.initial }.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { value -> values[index] = value },
                        label = { Text(field.label) },
                        singleLine = field.singleLine,
                        minLines = field.minLines,
                        maxLines = if (field.singleLine) 1 else 10,
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values.toList()) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}

/** Confirmation dialog (standard M3 AlertDialog). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.common_apply),
    dismissText: String = stringResource(R.string.common_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
