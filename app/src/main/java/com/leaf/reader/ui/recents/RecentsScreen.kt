package com.leaf.reader.ui.recents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leaf.reader.core.Formatters
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.ui.LibraryState
import com.leaf.reader.ui.components.DocumentRow
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    state: LibraryState,
    onOpen: (LeafDocument) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onClearToday: () -> Unit,
    onDocumentActions: (LeafDocument) -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    val today = state.recents.filter { Formatters.isToday(it.openedAt) }
    val earlier = state.recents.filterNot { Formatters.isToday(it.openedAt) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recents") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = state.recents.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all recents")
                    }
                }
            )
        }
    ) { padding ->
        if (state.recents.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "No recents yet",
                    message = "Documents you open appear here automatically. Recents never change your files."
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (today.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Today",
                        subtitle = "${today.size} document${if (today.size == 1) "" else "s"}",
                        action = {
                            TextButton(onClick = onClearToday) { Text("Clear today") }
                        }
                    )
                }
                items(today, key = { it.documentId }) { entry ->
                    val document = entry.document
                    if (document != null) {
                        RecentRow(
                            document = document,
                            openedAt = entry.openedAt,
                            onOpen = { onOpen(document) },
                            onRemove = { onRemove(entry.documentId) },
                            onLongPress = { onDocumentActions(document) }
                        )
                    }
                }
            }
            if (earlier.isNotEmpty()) {
                item { SectionHeader(title = "Earlier") }
                items(earlier, key = { it.documentId }) { entry ->
                    val document = entry.document
                    if (document != null) {
                        RecentRow(
                            document = document,
                            openedAt = entry.openedAt,
                            onOpen = { onOpen(document) },
                            onRemove = { onRemove(entry.documentId) },
                            onLongPress = { onDocumentActions(document) }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        com.leaf.reader.ui.components.ConfirmDialog(
            title = "Clear all recents?",
            message = "This only clears the history list. Your documents are never touched.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = onClearAll,
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun RecentRow(
    document: LeafDocument,
    openedAt: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            DocumentRow(
                document = document,
                folderName = null,
                tags = emptyList(),
                onClick = onOpen,
                onLongClick = onLongPress
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove ${document.name} from recents",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Opened ${Formatters.relativeTime(openedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
