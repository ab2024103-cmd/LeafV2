package com.leaf.reader.ui.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField
import com.leaf.reader.ui.LibraryState
import com.leaf.reader.ui.components.DocumentRow
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.SectionHeader
import com.leaf.reader.ui.sortDocuments
import com.leaf.reader.ui.sortLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: LibraryState,
    onOpen: (LeafDocument) -> Unit,
    onToggleFavorite: (LeafDocument) -> Unit,
    onDocumentActions: (LeafDocument) -> Unit,
    onSort: (SortField, SortDirection) -> Unit
) {
    var sortOpen by remember { mutableStateOf(false) }
    val favorites = remember(state.documents, state.settings.favoritesSortField, state.settings.favoritesSortDirection) {
        state.documents
            .filter { it.isFavorite }
            .sortDocuments(state.settings.favoritesSortField, state.settings.favoritesSortDirection)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sort favorites")
                    }
                }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "No favorites yet",
                    message = "Star a document from the library, the long-press sheet or the reader to pin it here."
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
            item {
                SectionHeader(
                    title = "${favorites.size} favorite${if (favorites.size == 1) "" else "s"}",
                    subtitle = sortLabel(state.settings.favoritesSortField, state.settings.favoritesSortDirection)
                )
            }
            items(favorites, key = { it.id }) { document ->
                DocumentRow(
                    document = document,
                    folderName = state.folderName(document.folderId),
                    tags = state.tagsFor(document.id),
                    onClick = { onOpen(document) },
                    onLongClick = { onDocumentActions(document) },
                    onToggleFavorite = { onToggleFavorite(document) }
                )
            }
        }
    }

    if (sortOpen) {
        com.leaf.reader.ui.components.SortSheet(
            currentField = state.settings.favoritesSortField,
            currentDirection = state.settings.favoritesSortDirection,
            onSelect = { field, direction -> onSort(field, direction) },
            onDismiss = { sortOpen = false }
        )
    }
}
