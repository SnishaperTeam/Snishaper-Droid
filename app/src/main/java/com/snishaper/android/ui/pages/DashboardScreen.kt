package com.snishaper.android.ui.pages

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snishaper.android.R
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.core.Stats
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.StatusPill
import com.snishaper.android.ui.components.rememberToast
import com.snishaper.android.vpn.TunVpnService
import com.snishaper.android.vpn.VpnInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dashboard: the real engine status (desktop `Dashboard.tsx:346-556` minus the removed
 * certificate card). Start/stop the proxy, toggle the TUN-mode VpnService and watch the
 * listen address and live traffic stats.
 */
@Composable
fun DashboardScreen(contentPadding: PaddingValues) {
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val consentNeededText = stringResource(R.string.dashboard_vpn_consent_needed)
    val vpnFailedText = stringResource(R.string.dashboard_vpn_failed)
    val status by SnishaperEngine.status.collectAsStateWithLifecycle()
    val ready by SnishaperEngine.ready.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(Stats()) }

    // VPN consent flow: VpnService.prepare returns null (already granted) directly, otherwise
    // the returned intent is launched and RESULT_OK means the user granted it.
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                context.startForegroundService(Intent(context, TunVpnService::class.java))
            }.onFailure { toast("$vpnFailedText: ${it.message}") }
        } else {
            toast(consentNeededText)
        }
    }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (isActive) {
            runCatching { SnishaperEngine.getStats() }.onSuccess { stats = it }
            delay(2_000)
        }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { action() }.onFailure { toast(it.message ?: "操作失败") }
            busy = false
        }
    }

    fun toggleVpn() {
        if (!ready) return
        if (status.tunRunning) {
            runCatching {
                context.startService(
                    Intent(context, TunVpnService::class.java).setAction(TunVpnService.ACTION_STOP),
                )
            }
            return
        }
        val consent = VpnInstaller.prepare(context)
        if (consent == null) {
            runCatching {
                context.startForegroundService(Intent(context, TunVpnService::class.java))
            }.onFailure { toast("$vpnFailedText: ${it.message}") }
        } else {
            vpnConsentLauncher.launch(consent)
        }
    }

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Memory,
            titleRes = R.string.dashboard_title,
            subtitleRes = R.string.dashboard_subtitle,
            actions = {
                Button(
                    enabled = ready && !busy,
                    onClick = {
                        run {
                            if (status.proxyRunning) {
                                SnishaperEngine.stopProxy()
                                toast("代理已停止")
                            } else {
                                SnishaperEngine.startProxy()
                                toast("代理已启动")
                            }
                        }
                    },
                ) {
                    Icon(
                        if (status.proxyRunning) Icons.Outlined.PowerSettingsNew else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (status.proxyRunning) R.string.dashboard_stop_proxy else R.string.dashboard_start_proxy,
                        ),
                    )
                }
                OutlinedButton(
                    enabled = ready,
                    onClick = { toggleVpn() },
                ) {
                    Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (status.tunRunning) R.string.dashboard_vpn_stop else R.string.dashboard_vpn_start,
                        ),
                    )
                }
            },
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 600.dp
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreStatusCard(Modifier.weight(1f), ready, status.runLabel(), status.listenPort, status.tunRunning)
                    ConnectionCard(Modifier.weight(1f), status.listenAddr, status.proxyRunning)
                }
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RuntimeCard(Modifier.weight(1f), ready, status.ipv6Available, status.lastError)
                    TrafficCard(Modifier.weight(1f), stats)
                }
            } else {
                CoreStatusCard(Modifier.fillMaxWidth(), ready, status.runLabel(), status.listenPort, status.tunRunning)
                Spacer(Modifier.size(12.dp))
                ConnectionCard(Modifier.fillMaxWidth(), status.listenAddr, status.proxyRunning)
                Spacer(Modifier.size(12.dp))
                RuntimeCard(Modifier.fillMaxWidth(), ready, status.ipv6Available, status.lastError)
                Spacer(Modifier.size(12.dp))
                TrafficCard(Modifier.fillMaxWidth(), stats)
            }
        }
    }
}

private fun com.snishaper.android.core.EngineStatus.runLabel(): String = when (proxyMode) {
    "" -> "TLS-RF"
    "transparent" -> "Transparent"
    else -> proxyMode
}

@Composable
private fun CoreStatusCard(
    modifier: Modifier,
    ready: Boolean,
    mode: String,
    port: Int,
    vpnRunning: Boolean,
) {
    StatusCard(modifier, R.string.dashboard_core_status, Icons.Outlined.Memory) {
        InfoRow(
            labelRes = R.string.dashboard_run_status,
            pillText = stringResource(
                when {
                    !ready -> R.string.dashboard_engine_offline
                    SnishaperEngine.status.value.proxyRunning -> R.string.common_running
                    else -> R.string.common_stopped
                },
            ),
            pillColor = when {
                !ready -> MaterialTheme.colorScheme.error
                SnishaperEngine.status.value.proxyRunning -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
        )
        InfoRow(labelRes = R.string.dashboard_work_mode, value = mode)
        InfoRow(labelRes = R.string.dashboard_port, value = port.toString())
        InfoRow(
            labelRes = R.string.dashboard_vpn_status,
            value = if (vpnRunning) stringResource(R.string.common_on) else stringResource(R.string.common_off),
        )
    }
}

@Composable
private fun ConnectionCard(modifier: Modifier, listenAddr: String, running: Boolean) {
    StatusCard(modifier, R.string.dashboard_conn_info, Icons.Outlined.Bolt) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = listenAddr.ifEmpty { "127.0.0.1:8080" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            StatusPill(
                text = stringResource(if (running) R.string.dashboard_ready else R.string.dashboard_not_ready),
                containerColor = if (running) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuntimeCard(modifier: Modifier, ready: Boolean, ipv6: Boolean, lastError: String) {
    StatusCard(modifier, R.string.dashboard_runtime, Icons.Outlined.Speed) {
        InfoRow(
            labelRes = R.string.dashboard_ipv6,
            value = stringResource(if (ipv6) R.string.dashboard_ipv6_ok else R.string.dashboard_ipv6_off),
        )
        InfoRow(labelRes = R.string.dashboard_engine, value = if (ready) "Go · snishaper-core v0.4.0" else "—")
        if (lastError.isNotBlank()) {
            Text(
                text = lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TrafficCard(modifier: Modifier, stats: Stats) {
    StatusCard(modifier, R.string.dashboard_traffic, Icons.Outlined.Speed) {
        InfoRow(labelRes = R.string.dashboard_down, value = formatBytes(stats.down))
        InfoRow(labelRes = R.string.dashboard_up, value = formatBytes(stats.up))
        InfoRow(labelRes = R.string.dashboard_connections, value = stats.etc.toString())
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var index = -1
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    return String.format("%.1f %s", size, units[index])
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    @StringRes titleRes: Int,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(id = titleRes).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(
    @StringRes labelRes: Int,
    value: String? = null,
    pillText: String? = null,
    pillColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pillText != null) {
            StatusPill(
                text = pillText,
                containerColor = pillColor.copy(alpha = 0.16f),
                contentColor = pillColor,
            )
        } else if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
