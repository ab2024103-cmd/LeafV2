package com.leaf.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.leaf.reader.model.FileKind
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField
import com.leaf.reader.ui.components.DocumentRow
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.FileTypeIcon
import com.leaf.reader.ui.components.SectionHeader
import com.leaf.reader.ui.sortDocuments
import com.leaf.reader.ui.sortLabel

/** The file-type group view (spec 4.19 – 4.25). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: LibraryState,
    kind: String,
    onBack: () -> Unit,
    onOpen: (LeafDocument) -> Unit,
    onDocumentActions: (LeafDocument) -> Unit,
    onSort: (SortField, SortDirection) -> Unit
) {
    val fileKind = FileKind.fromKey(kind) ?: FileKind.PDF
    var sortOpen by remember { mutableStateOf(false) }
    val documents = remember(state.documents, fileKind, state.settings.librarySortField, state.settings.librarySortDirection) {
        state.documents
            .filter { it.kind == fileKind }
            .sortDocuments(state.settings.librarySortField, state.settings.librarySortDirection)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to file types")
                        }
                        FileTypeIcon(fileKind, size = 30)
                        Text(
                            text = "  ${fileKind.label} · ${documents.size}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sort ${fileKind.label}")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SectionHeader(
                    title = "${fileKind.label} documents",
                    subtitle = sortLabel(state.settings.librarySortField, state.settings.librarySortDirection)
                )
            }
            if (documents.isEmpty()) {
                item {
                    EmptyState(
                        title = "No ${fileKind.label} documents",
                        message = "Add one from the library to see it here."
                    )
                }
            }
            items(documents, key = { it.id }) { document ->
                DocumentRow(
                    document = document,
                    folderName = state.folderName(document.folderId),
                    tags = state.tagsFor(document.id),
                    onClick = { onOpen(document) },
                    onLongClick = { onDocumentActions(document) }
                )
            }
        }
    }

    if (sortOpen) {
        com.leaf.reader.ui.components.SortSheet(
            currentField = state.settings.librarySortField,
            currentDirection = state.settings.librarySortDirection,
            onSelect = { field, direction -> onSort(field, direction) },
            onDismiss = { sortOpen = false }
        )
    }
}
