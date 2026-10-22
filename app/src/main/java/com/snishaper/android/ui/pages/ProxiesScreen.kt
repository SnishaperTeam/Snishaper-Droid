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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
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
import com.snishaper.android.core.ECHProfile
import com.snishaper.android.core.Nat64Profile
import com.snishaper.android.core.Nat64TestResult
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.ConfirmDialog
import com.snishaper.android.ui.components.DialogField
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.SettingTextRow
import com.snishaper.android.ui.components.TextFieldDialog
import kotlinx.coroutines.launch

/**
 * Proxies page: ECH profiles + NAT64 profiles (desktop `Proxies.tsx` minus the removed
 * "Connection Migration Service" section). CRUD via standard M3 dialogs and rows.
 */
@Composable
fun ProxiesScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val toast = rememberToastSafe()
    val echProfiles = remember { mutableStateListOf<ECHProfile>() }
    val nat64Profiles = remember { mutableStateListOf<Nat64Profile>() }
    var loading by rememberSaveable { mutableStateOf(true) }

    var showAddEch by remember { mutableStateOf(false) }
    var showFetchEch by remember { mutableStateOf(false) }
    var editEch by remember { mutableStateOf<ECHProfile?>(null) }
    var nat64ToDelete by remember { mutableStateOf<Nat64Profile?>(null) }
    var echToDelete by remember { mutableStateOf<ECHProfile?>(null) }
    var showAddNat64 by remember { mutableStateOf(false) }
    var testNat64 by remember { mutableStateOf<String?>(null) }
    var nat64Result by remember { mutableStateOf<Nat64TestResult?>(null) }
    var nat64Testing by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching {
            echProfiles.clear()
            echProfiles.addAll(SnishaperEngine.echProfiles())
            nat64Profiles.clear()
            nat64Profiles.addAll(SnishaperEngine.nat64Profiles())
        }.onFailure { toast(it.message ?: "加载失败") }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    // NAT64 test on demand.
    LaunchedEffect(testNat64) {
        val prefix = testNat64 ?: return@LaunchedEffect
        nat64Testing = true
        runCatching { SnishaperEngine.testNat64(prefix) }
            .onSuccess { nat64Result = it }
            .onFailure { toast(it.message ?: "测试失败") }
        nat64Testing = false
    }

    val nameLabel = stringResource(R.string.proxies_field_name)
    val discoveryLabel = stringResource(R.string.proxies_field_discovery)
    val dohLabel = stringResource(R.string.proxies_field_doh)
    val prefixLabel = stringResource(R.string.proxies_field_prefix)
    val okLabel = stringResource(R.string.dns_test_ok)
    val failPrefix = stringResource(R.string.dns_test_fail_prefix)
    val latencySuffix = stringResource(R.string.dns_test_latency_suffix)

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Public,
            titleRes = R.string.proxies_title,
            subtitleRes = R.string.proxies_subtitle,
        )

        SectionHeader(
            titleRes = R.string.proxies_ech_section,
            icon = Icons.Outlined.Bolt,
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFetchEch = true }) {
                        Text(stringResource(R.string.proxies_fetch_ech))
                    }
                    Button(onClick = { showAddEch = true }) {
                        Text(stringResource(R.string.proxies_add_ech))
                    }
                }
            },
        )
        if (loading) {
            CircularProgressIndicator()
        } else if (echProfiles.isEmpty()) {
            EmptyState(icon = Icons.Outlined.Bolt, titleRes = R.string.proxies_empty_ech)
        } else {
            echProfiles.forEach { profile ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    append(profile.discoveryDomain)
                                    if (profile.dohUpstream.isNotBlank()) {
                                        append(" · ")
                                        append(profile.dohUpstream)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.proxies_ech_config_size,
                                    profile.config.length,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { editEch = profile }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.common_edit))
                        }
                        IconButton(onClick = { echToDelete = profile }) {
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

        Spacer(modifier = Modifier.size(4.dp))

        SectionHeader(
            titleRes = R.string.proxies_nat64_section,
            icon = Icons.Outlined.Public,
            action = {
                Button(onClick = { showAddNat64 = true }) {
                    Text(stringResource(R.string.proxies_add_nat64))
                }
            },
        )
        if (nat64Profiles.isEmpty() && !loading) {
            EmptyState(icon = Icons.Outlined.Public, titleRes = R.string.proxies_empty_nat64)
        } else {
            nat64Profiles.forEach { profile ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = profile.prefix,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            nat64Result?.takeIf { testNat64 == profile.prefix }?.let { result ->
                                Text(
                                    text = if (result.success) {
                                        "$okLabel · ${result.latencyMs}$latencySuffix"
                                    } else {
                                        failPrefix + result.error
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.success) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                        OutlinedButton(
                            enabled = !nat64Testing,
                            onClick = { testNat64 = profile.prefix },
                        ) {
                            Text(stringResource(R.string.common_test))
                        }
                        IconButton(onClick = { nat64ToDelete = profile }) {
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

    // --- dialogs -----------------------------------------------------------

    if (showAddEch) {
        TextFieldDialog(
            title = stringResource(R.string.proxies_add_ech),
            fields = listOf(
                DialogField(nameLabel),
                DialogField(discoveryLabel),
                DialogField(dohLabel),
            ),
            onConfirm = { values ->
                showAddEch = false
                scope.launch {
                    runCatching {
                        SnishaperEngine.upsertEchProfile(
                            ECHProfile(name = values[0], discoveryDomain = values[1], dohUpstream = values[2]),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "操作失败") }
                }
            },
            onDismiss = { showAddEch = false },
        )
    }
    if (showFetchEch) {
        val domainLabel = stringResource(R.string.proxies_field_domain)
        TextFieldDialog(
            title = stringResource(R.string.proxies_fetch_ech),
            fields = listOf(DialogField(domainLabel), DialogField(dohLabel)),
            confirmText = stringResource(R.string.common_fetch),
            onConfirm = { values ->
                showFetchEch = false
                scope.launch {
                    runCatching {
                        val json = SnishaperEngine.fetchEchConfig(values[0], values[1])
                        // Pre-fill the add dialog with the fetched config by upserting a profile.
                        val parsed = org.json.JSONObject(json)
                        val config = parsed.optString("config")
                        if (config.isNotBlank()) {
                            SnishaperEngine.upsertEchProfile(
                                ECHProfile(
                                    name = values[0],
                                    discoveryDomain = values[0],
                                    dohUpstream = values[1],
                                    config = config,
                                    autoUpdate = true,
                                ),
                            )
                        }
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "获取失败") }
                }
            },
            onDismiss = { showFetchEch = false },
        )
    }
    editEch?.let { profile ->
        TextFieldDialog(
            title = stringResource(R.string.proxies_edit_ech),
            fields = listOf(
                DialogField(nameLabel, profile.name),
                DialogField(discoveryLabel, profile.discoveryDomain),
                DialogField(dohLabel, profile.dohUpstream),
            ),
            onConfirm = { values ->
                editEch = null
                scope.launch {
                    runCatching {
                        SnishaperEngine.upsertEchProfile(
                            profile.copy(name = values[0], discoveryDomain = values[1], dohUpstream = values[2]),
                        )
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "操作失败") }
                }
            },
            onDismiss = { editEch = null },
        )
    }
    echToDelete?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.proxies_delete_ech_confirm, profile.name),
            onConfirm = {
                echToDelete = null
                scope.launch {
                    runCatching { SnishaperEngine.deleteEchProfile(profile.id) }
                        .onSuccess { reload() }
                        .onFailure { toast(it.message ?: "删除失败") }
                }
            },
            onDismiss = { echToDelete = null },
        )
    }
    if (showAddNat64) {
        TextFieldDialog(
            title = stringResource(R.string.proxies_add_nat64),
            fields = listOf(DialogField(nameLabel), DialogField(prefixLabel)),
            onConfirm = { values ->
                showAddNat64 = false
                scope.launch {
                    runCatching {
                        SnishaperEngine.addNat64Profile(Nat64Profile(name = values[0], prefix = values[1]))
                    }.onSuccess { reload() }.onFailure { toast(it.message ?: "操作失败") }
                }
            },
            onDismiss = { showAddNat64 = false },
        )
    }
    nat64ToDelete?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.proxies_delete_nat64_confirm, profile.name),
            onConfirm = {
                nat64ToDelete = null
                scope.launch {
                    runCatching { SnishaperEngine.deleteNat64Profile(profile.id) }
                        .onSuccess { reload() }
                        .onFailure { toast(it.message ?: "删除失败") }
                }
            },
            onDismiss = { nat64ToDelete = null },
        )
    }
}

/** Helper mirroring [rememberToast]. */
@Composable
private fun rememberToastSafe(): (String) -> Unit = com.snishaper.android.ui.components.rememberToast()
