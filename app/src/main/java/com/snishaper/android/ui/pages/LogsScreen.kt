package com.snishaper.android.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snishaper.android.R
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.ui.components.PageColumn
import com.snishaper.android.ui.components.PageHeader
import com.snishaper.android.ui.components.rememberToast

/**
 * Logs page (desktop `Logs.tsx:174-293`): live engine logs from the shared listener flow,
 * search filter, pause/resume auto-scrolling and clear.
 */
@Composable
fun LogsScreen(contentPadding: PaddingValues) {
    val toast = rememberToast()
    val lines = remember { mutableStateListOf<String>() }
    var query by rememberSaveable { mutableStateOf("") }
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Collect engine logs while the page is visible.
    LaunchedEffect(Unit) {
        SnishaperEngine.logs.collect { line ->
            lines.add(line)
            while (lines.size > 2000) lines.removeAt(0)
        }
    }

    // Auto-scroll while enabled and the user is near the bottom.
    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    val filtered = if (query.isBlank()) lines else lines.filter { it.contains(query, ignoreCase = true) }

    PageColumn(contentPadding = contentPadding) {
        PageHeader(
            icon = Icons.Outlined.PlayArrow,
            titleRes = R.string.logs_title,
            subtitleRes = R.string.logs_subtitle,
            actions = {
                OutlinedButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(if (autoScroll) R.string.logs_pause else R.string.logs_resume))
                }
                OutlinedButton(
                    onClick = { lines.clear() },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.logs_clear))
                }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.logs_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.logs_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered.size) { index ->
                        Text(
                            text = filtered[index],
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
