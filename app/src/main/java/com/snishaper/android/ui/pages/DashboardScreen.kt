package com.snishaper.android.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.StatusPill
import com.snishaper.android.ui.components.rememberToast
import com.snishaper.android.ui.theme.LocalSnishaperExtraColors

/**
 * Dashboard skeleton. Desktop structure (`Dashboard.tsx:346-556`) minus the removed CA card:
 * header actions + Core Status card + Connection Info card.
 */
@Composable
fun DashboardScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()
    val extra = LocalSnishaperExtraColors.current

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Memory,
            titleRes = R.string.dashboard_title,
            subtitleRes = R.string.dashboard_subtitle,
            actions = {
                Button(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.dashboard_start_proxy))
                }
                OutlinedButton(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.dashboard_vpn))
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                titleRes = R.string.dashboard_core_status,
                icon = Icons.Outlined.Memory,
            ) {
                InfoRow(
                    labelRes = R.string.dashboard_run_status,
                    pillText = stringResource(id = R.string.common_stopped),
                    pillColor = MaterialTheme.colorScheme.error,
                )
                InfoRow(
                    labelRes = R.string.dashboard_work_mode,
                    value = "TLS-RF",
                )
                InfoRow(
                    labelRes = R.string.dashboard_port,
                    value = "8080",
                )
                InfoRow(
                    labelRes = R.string.dashboard_vpn_status,
                    pillText = stringResource(id = R.string.common_off),
                    pillColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                titleRes = R.string.dashboard_conn_info,
                icon = Icons.Outlined.Bolt,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "127.0.0.1:8080",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    StatusPill(
                        text = stringResource(id = R.string.dashboard_ready),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SkeletonNote()
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "VPN（VpnService）与代理引擎将在功能阶段接入",
            style = MaterialTheme.typography.bodySmall,
            color = extra.success.copy(alpha = 0.8f),
        )
    }
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
