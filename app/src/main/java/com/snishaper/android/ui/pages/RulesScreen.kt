package com.snishaper.android.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.ui.components.EmptyState
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.SkeletonNote
import com.snishaper.android.ui.components.rememberToast

private data class RuleFilter(@StringRes val labelRes: Int)

/** Mode filters after CA removal: ALL / TLS-RF / TRANSPARENT only. */
private val RuleFilters = listOf(
    RuleFilter(R.string.rules_filter_all),
    RuleFilter(R.string.rules_filter_tlsrf),
    RuleFilter(R.string.rules_filter_transparent),
)

/**
 * Rules skeleton. Desktop structure (`Rules.tsx:206-322`): header actions, mode filter chips,
 * search field, grouped list (empty state for now).
 */
@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val comingSoon = stringResource(id = R.string.common_coming_soon)
    val toast = rememberToast()
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.VerifiedUser,
            titleRes = R.string.rules_title,
            subtitleRes = R.string.rules_subtitle,
            actions = {
                Button(onClick = { toast(comingSoon) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.rules_add))
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
            placeholder = { Text(stringResource(id = R.string.rules_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        EmptyState(
            icon = Icons.Outlined.VerifiedUser,
            titleRes = R.string.rules_empty,
            messageRes = R.string.common_coming_soon,
        )

        SkeletonNote()
    }
}
