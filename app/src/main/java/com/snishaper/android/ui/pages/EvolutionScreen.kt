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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snishaper.android.R
import com.snishaper.android.core.EvolutionTask
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.rememberToast
import kotlinx.coroutines.launch

/**
 * Evolution page (desktop `Evolution.tsx:264-325`): Test / Rules / Results tabs with real
 * engine progress and results. Applying is limited to tls_fragment / direct (bridge §4.3);
 * other trends are shown but cannot be applied on Android.
 */
@Composable
fun EvolutionScreen(contentPadding: PaddingValues) {
    val toast = rememberToast()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val task by SnishaperEngine.evolution.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var domains by rememberSaveable { mutableStateOf("") }
    var enableIpv6 by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val tabs = listOf(
        R.string.evolution_tab_test,
        R.string.evolution_tab_rules,
        R.string.evolution_tab_results,
    )

    LaunchedEffect(Unit) {
        runCatching { SnishaperEngine.evolutionStatus() }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { action() }.onFailure { toast(it.message ?: "操作失败") }
            busy = false
        }
    }

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Bolt,
            titleRes = R.string.evolution_title,
            subtitleRes = R.string.evolution_subtitle,
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, labelRes ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(id = labelRes)) },
                )
            }
        }

        when (selectedTab) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.evolution_domains_hint)) },
                    minLines = 5,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(checked = enableIpv6, onCheckedChange = { enableIpv6 = it })
                    Text(
                        text = stringResource(R.string.evolution_enable_ipv6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !busy && !(task?.running == true),
                        onClick = {
                            val domainList = domains.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            if (domainList.isEmpty()) {
                                toast("无可用域名")
                                return@Button
                            }
                            run {
                                SnishaperEngine.startEvolutionTest(domainList, enableIpv6)
                                domains = ""
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.evolution_start))
                    }
                    if (task?.running == true) {
                        OutlinedButton(onClick = { run { SnishaperEngine.stopEvolutionTest() } }) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.evolution_stop))
                        }
                    }
                }
                task?.let { t ->
                    if (t.running) {
                        Card {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.evolution_status_running),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.evolution_progress, t.progress, t.total),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tempRules = task?.tempRules.orEmpty()
                if (tempRules.isEmpty()) {
                    EmptyState(icon = Icons.Outlined.Description, titleRes = R.string.evolution_empty_rules)
                } else {
                    tempRules.forEach { rule ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.domain,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = stringResource(R.string.evolution_rule_mode, rule.mode),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (rule.mode == "tls_fragment" || rule.mode == "direct") {
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            run { SnishaperEngine.applyEvolutionRule(rule.id) }
                                        },
                                    ) {
                                        Text(stringResource(R.string.evolution_apply))
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.evolution_apply_unsupported),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val results = task?.results.orEmpty()
                if (results.isEmpty()) {
                    EmptyState(icon = Icons.Outlined.Bolt, titleRes = R.string.evolution_empty_results)
                } else {
                    results.forEach { result ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = result.domain,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (result.bestIp.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.evolution_best_ip, result.bestIp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    text = if (result.reachable) {
                                        stringResource(R.string.evolution_reachable) +
                                            stringResource(R.string.evolution_delay, result.delay)
                                    } else {
                                        stringResource(R.string.evolution_unreachable)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.reachable) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
