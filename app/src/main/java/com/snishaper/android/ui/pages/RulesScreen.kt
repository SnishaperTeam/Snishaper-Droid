package com.snishaper.android.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.core.SiteGroup
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.ConfirmDialog
import com.snishaper.android.ui.components.DialogField
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.TextFieldDialog
import kotlinx.coroutines.launch

private data class RuleFilter(val key: String?, @StringRes val labelRes: Int)

/** Mode filters after CA removal: ALL / TLS-RF / TRANSPARENT only. */
private val RuleFilters = listOf(
    RuleFilter(null, R.string.rules_filter_all),
    RuleFilter("tls-rf", R.string.rules_filter_tlsrf),
    RuleFilter("transparent", R.string.rules_filter_transparent),
)

/**
 * Rules page: site groups (desktop `Rules.tsx:206-322`) with real engine CRUD.
 * Modes are limited to tls-rf / transparent (+ read-only built-in direct); no CA modes.
 */
@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val toast = com.snishaper.android.ui.components.rememberToast()
    val groups = remember { mutableStateListOf<SiteGroup>() }
    var loading by rememberSaveable { mutableStateOf(true) }
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<SiteGroup?>(null) }
    var deleteGroup by remember { mutableStateOf<SiteGroup?>(null) }

    suspend fun reload() {
        runCatching { SnishaperEngine.siteGroups() }
            .onSuccess {
                groups.clear()
                groups.addAll(it)
            }
            .onFailure { toast(it.message ?: "加载失败") }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val filtered = groups.filter { group ->
        val modeOk = RuleFilters[selectedFilter].key == null || group.mode == RuleFilters[selectedFilter].key
        val queryOk = query.isBlank() ||
            group.name.contains(query, ignoreCase = true) ||
            group.domains.any { it.contains(query, ignoreCase = true) } ||
            group.upstream.contains(query, ignoreCase = true)
        modeOk && queryOk
    }

    val nameLabel = stringResource(R.string.rules_field_name)
    val domainsLabel = stringResource(R.string.rules_field_domains)
    val upstreamLabel = stringResource(R.string.rules_field_upstream)
    val modeTlsrf = stringResource(R.string.rules_mode_tlsrf)
    val modeTransparent = stringResource(R.string.rules_mode_transparent)

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.VerifiedUser,
            titleRes = R.string.rules_title,
            subtitleRes = R.string.rules_subtitle,
            actions = {
                Button(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.rules_add))
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuleFilters.forEachIndexed { index, filter ->
                FilterChip(
                    selected = selectedFilter == index,
                    onClick = { selectedFilter = index },
                    label = { Text(stringResource(id = filter.labelRes)) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.rules_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )

        Text(
            text = stringResource(R.string.rules_showing, filtered.size, groups.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (loading) {
            CircularProgressIndicator()
        } else if (filtered.isEmpty()) {
            EmptyState(icon = Icons.Outlined.VerifiedUser, titleRes = R.string.rules_empty)
        } else {
            filtered.forEach { group ->
                val enabled = group.enabled
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = when (group.mode) {
                                        "transparent" -> modeTransparent
                                        "direct" -> stringResource(R.string.rules_mode_direct)
                                        else -> modeTlsrf
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (group.domains.isEmpty()) {
                                    stringResource(R.string.common_empty)
                                } else {
                                    stringResource(R.string.rules_domains_preview, group.domains.size.toString())
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (group.upstream.isNotBlank()) {
                                Text(
                                    text = "${stringResource(R.string.rules_field_upstream)}: ${group.upstream}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    runCatching {
                                        SnishaperEngine.updateSiteGroup(group.copy(enabled = checked))
                                    }.onSuccess { reload() }
                                        .onFailure { toast(it.message ?: "操作失败") }
                                }
                            },
                        )
                        IconButton(
                            enabled = group.mode != "direct",
                            onClick = { editGroup = group },
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.common_edit))
                        }
                        IconButton(
                            enabled = group.mode != "direct",
                            onClick = { deleteGroup = group },
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextFieldDialog(
            title = stringResource(R.string.rules_add),
            fields = listOf(
                DialogField(nameLabel),
                DialogField(domainsLabel, singleLine = false, minLines = 4),
                DialogField(upstreamLabel),
            ),
            onConfirm = { values ->
                showAdd = false
                scope.launch {
                    runCatching {
                        val domains = values[1].lines().map { it.trim() }.filter { it.isNotEmpty() }
                        SnishaperEngine.addSiteGroup(
                            SiteGroup(name = values[0], domains = domains, upstream = values[2], mode = "tls-rf"),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "保存失败") }
                }
            },
            onDismiss = { showAdd = false },
        )
    }
    editGroup?.let { group ->
        TextFieldDialog(
            title = stringResource(R.string.rules_title),
            fields = listOf(
                DialogField(nameLabel, group.name),
                DialogField(domainsLabel, group.domains.joinToString("\n"), singleLine = false, minLines = 4),
                DialogField(upstreamLabel, group.upstream),
            ),
            onConfirm = { values ->
                editGroup = null
                scope.launch {
                    runCatching {
                        val domains = values[1].lines().map { it.trim() }.filter { it.isNotEmpty() }
                        SnishaperEngine.updateSiteGroup(
                            group.copy(name = values[0], domains = domains, upstream = values[2]),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "保存失败") }
                }
            },
            onDismiss = { editGroup = null },
        )
    }
    deleteGroup?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.rules_delete_confirm, group.name),
            onConfirm = {
                deleteGroup = null
                scope.launch {
                    runCatching { SnishaperEngine.deleteSiteGroup(group.id) }
                        .onSuccess { reload() }
                        .onFailure { toast(it.message ?: "删除失败") }
                }
            },
            onDismiss = { deleteGroup = null },
        )
    }
}
