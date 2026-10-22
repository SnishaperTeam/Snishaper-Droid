package com.snishaper.android.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.snishaper.android.core.DnsNode
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.ConfirmDialog
import com.snishaper.android.ui.components.DialogField
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.TextFieldDialog
import kotlinx.coroutines.launch

/**
 * DNS page: DoH nodes (desktop `DNS.tsx:307-460`) with real engine CRUD, per-node and
 * all-node tests, priority ordering. CA cert-verify editing is intentionally absent —
 * the engine keeps its field untouched when Android sends no `cert_verify` section.
 */
@Composable
fun DnsScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val toast = com.snishaper.android.ui.components.rememberToast()
    val nodes = remember { mutableStateListOf<DnsNode>() }
    var loading by rememberSaveable { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var editNode by remember { mutableStateOf<DnsNode?>(null) }
    var deleteNode by remember { mutableStateOf<DnsNode?>(null) }
    var testingAll by rememberSaveable { mutableStateOf(false) }
    var testingId by rememberSaveable { mutableStateOf("") }

    // Result text per node id, shown on the card. Localized prefixes resolved at
    // composition time so background coroutines can reuse them safely.
    val results = remember { mutableStateMapOf<String, String>() }
    val okLabel = stringResource(R.string.dns_test_ok)
    val progressLabel = stringResource(R.string.dns_test_progress)
    val failPrefix = stringResource(R.string.dns_test_fail_prefix)
    val latencySuffix = stringResource(R.string.dns_test_latency_suffix)

    suspend fun testNode(node: DnsNode) {
        results[node.id] = progressLabel
        runCatching { SnishaperEngine.testDnsNode(node.id) }
            .onSuccess { result ->
                results[node.id] = if (result.success) {
                    "$okLabel · ${result.latencyMs}$latencySuffix"
                } else {
                    failPrefix + result.error
                }
            }
            .onFailure { results[node.id] = failPrefix + (it.message ?: "error") }
    }

    suspend fun reload() {
        runCatching { SnishaperEngine.dnsNodes() }
            .onSuccess {
                nodes.clear()
                nodes.addAll(it)
            }
            .onFailure { toast(it.message ?: "加载失败") }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val nameLabel = stringResource(R.string.dns_field_name)
    val urlLabel = stringResource(R.string.dns_field_url)

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.CellTower,
            titleRes = R.string.dns_title,
            subtitleRes = R.string.dns_subtitle,
            actions = {
                OutlinedButton(
                    enabled = nodes.isNotEmpty() && !testingAll,
                    onClick = {
                        scope.launch {
                            testingAll = true
                            nodes.forEach { node -> testNode(node) }
                            testingAll = false
                        }
                    },
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dns_test_all))
                }
                Button(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dns_add))
                }
            },
        )

        if (loading) {
            CircularProgressIndicator()
        } else if (nodes.isEmpty()) {
            EmptyState(icon = Icons.Outlined.CellTower, titleRes = R.string.dns_empty)
        } else {
            nodes.forEachIndexed { index, node ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = node.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { testingId = node.id }) {
                                Text(stringResource(R.string.common_test))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (node.echEnabled) {
                                Text(
                                    text = "ECH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (node.quic) {
                                Text(
                                    text = "QUIC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            results[node.id]?.let { resultText ->
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = resultText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (resultText.startsWith(okLabel)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    scope.launch {
                                        runCatching { SnishaperEngine.setDnsNodePriority(node.id, index - 1) }
                                            .onSuccess { reload() }
                                            .onFailure { toast(it.message ?: "操作失败") }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowUpward,
                                    contentDescription = stringResource(R.string.dns_priority_up),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = { editNode = node }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.common_edit))
                            }
                            IconButton(onClick = { deleteNode = node }) {
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
    }

    // Single test trigger.
    LaunchedEffect(testingId) {
        testingId.takeIf { it.isNotBlank() }?.let { id ->
            nodes.firstOrNull { it.id == id }?.let { testNode(it) }
            testingId = ""
        }
    }

    if (showAdd) {
        val sniLabel = stringResource(R.string.dns_field_sni)
        val ipsLabel = stringResource(R.string.dns_field_ips)
        TextFieldDialog(
            title = stringResource(R.string.dns_add),
            fields = listOf(
                DialogField(nameLabel),
                DialogField(urlLabel),
                DialogField(sniLabel),
                DialogField(ipsLabel),
            ),
            onConfirm = { values ->
                showAdd = false
                scope.launch {
                    runCatching {
                        SnishaperEngine.addDnsNode(
                            DnsNode(
                                name = values[0],
                                url = values[1],
                                sni = values[2],
                                ips = values[3].split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            ),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "保存失败") }
                }
            },
            onDismiss = { showAdd = false },
        )
    }
    editNode?.let { node ->
        val sniLabel = stringResource(R.string.dns_field_sni)
        val ipsLabel = stringResource(R.string.dns_field_ips)
        TextFieldDialog(
            title = stringResource(R.string.common_edit),
            fields = listOf(
                DialogField(nameLabel, node.name),
                DialogField(urlLabel, node.url),
                DialogField(sniLabel, node.sni),
                DialogField(ipsLabel, node.ips.joinToString(", ")),
            ),
            onConfirm = { values ->
                editNode = null
                scope.launch {
                    runCatching {
                        SnishaperEngine.updateDnsNode(
                            node.copy(
                                name = values[0],
                                url = values[1],
                                sni = values[2],
                                ips = values[3].split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            ),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "保存失败") }
                }
            },
            onDismiss = { editNode = null },
        )
    }
    deleteNode?.let { node ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.dns_delete_confirm, node.name),
            onConfirm = {
                deleteNode = null
                scope.launch {
                    runCatching { SnishaperEngine.deleteDnsNode(node.id) }
                        .onSuccess { reload() }
                        .onFailure { toast(it.message ?: "删除失败") }
                }
            },
            onDismiss = { deleteNode = null },
        )
    }
}
