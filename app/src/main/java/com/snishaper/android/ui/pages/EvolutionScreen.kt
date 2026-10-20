package com.snishaper.android.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.rememberToast

/**
 * Evolution skeleton (`Evolution.tsx:264-325`): Test / Rules / Results tabs.
 * Android keeps diagnostics; only tls_fragment/direct results may be applied (bridge contract §4.3).
 */
@Composable
fun EvolutionScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var domains by rememberSaveable { mutableStateOf("") }
    var enableIpv6 by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(
        R.string.evolution_tab_test,
        R.string.evolution_tab_rules,
        R.string.evolution_tab_results,
    )

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
                    placeholder = { Text(stringResource(id = R.string.evolution_domains_hint)) },
                    minLines = 5,
                    shape = MaterialTheme.shapes.large,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(checked = enableIpv6, onCheckedChange = { enableIpv6 = it })
                    Text(
                        text = stringResource(id = R.string.evolution_enable_ipv6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.evolution_start))
                }
            }

            1 -> EmptyState(
                icon = Icons.Outlined.Description,
                titleRes = R.string.evolution_empty_rules,
            )

            else -> EmptyState(
                icon = Icons.Outlined.Bolt,
                titleRes = R.string.evolution_empty_results,
            )
        }

        SkeletonNote()
    }
}
