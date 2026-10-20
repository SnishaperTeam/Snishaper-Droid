package com.snishaper.android.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SectionHeader
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.rememberToast

/**
 * Proxies skeleton. Desktop sections kept: ECH profiles + NAT64 profiles.
 * The desktop "Connection Migration Service" section is dropped (migration mode removed, user Q2).
 */
@Composable
fun ProxiesScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()

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
                OutlinedButton(onClick = { toast(comingSoon) }) {
                    Text(stringResource(id = R.string.proxies_add_ech))
                }
            },
        )
        EmptyState(icon = Icons.Outlined.Bolt, titleRes = R.string.proxies_empty_ech)

        Spacer(modifier = Modifier.size(4.dp))

        SectionHeader(
            titleRes = R.string.proxies_nat64_section,
            icon = Icons.Outlined.Public,
            action = {
                TextButton(onClick = { toast(comingSoon) }) {
                    Text(stringResource(id = R.string.proxies_add_nat64))
                }
            },
        )
        EmptyState(icon = Icons.Outlined.Public, titleRes = R.string.proxies_empty_nat64)

        SkeletonNote()
    }
}
