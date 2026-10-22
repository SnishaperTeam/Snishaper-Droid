package com.snishaper.android.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.core.GfwListStatus
import com.snishaper.android.core.RouteEvent
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Routing page: live traffic flow (route events pushed by the engine listener) and the
 * GFWList source status, read-only (desktop `Routing.tsx` minus the removed Smart switch).
 */
@Composable
fun RoutingScreen(contentPadding: PaddingValues) {
    val events = remember { mutableStateListOf<RouteEvent>() }
    var gfwlist by remember { mutableStateOf(GfwListStatus()) }
    val listState = rememberLazyListState()

    // Collect engine route events while the page is visible.
    LaunchedEffect(Unit) {
        SnishaperEngine.routes.collect { event ->
            events.add(0, event)
            while (events.size > 200) events.removeAt(events.lastIndex)
        }
    }
    // GFWList status refresh.
    LaunchedEffect(Unit) {
        runCatching { SnishaperEngine.autoRoutingStatus() }
            .onSuccess { gfwlist = it }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.Sync,
            titleRes = R.string.routing_title,
            subtitleRes = R.string.routing_subtitle,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader(titleRes = R.string.routing_traffic_flow, icon = Icons.Outlined.Share)
                Spacer(modifier = Modifier.size(8.dp))
                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.routing_waiting_traffic),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(events, key = { "${it.timestamp}-${it.domain}" }) { event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = event.domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(
                                    text = event.mode.ifBlank { "direct" },
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = timeFormat.format(Date(event.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Radar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.routing_gfwlist),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (gfwlist.enabled) {
                            stringResource(R.string.routing_domain_count, gfwlist.domainCount)
                        } else {
                            stringResource(R.string.routing_gfwlist_inactive)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = stringResource(if (gfwlist.enabled) R.string.common_on else R.string.common_off),
                    containerColor = if (gfwlist.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (gfwlist.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
