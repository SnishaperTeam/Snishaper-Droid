package com.snishaper.android.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.rememberToast

/** DNS node list skeleton (`DNS.tsx:307-338`), CA-free: no cert-verify panel or tag. */
@Composable
fun DnsScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.CellTower,
            titleRes = R.string.dns_title,
            subtitleRes = R.string.dns_subtitle,
            actions = {
                OutlinedButton(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.dns_test_all))
                }
                Button(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.dns_add))
                }
            },
        )

        EmptyState(icon = Icons.Outlined.CellTower, titleRes = R.string.dns_empty)
        SkeletonNote()
    }
}
