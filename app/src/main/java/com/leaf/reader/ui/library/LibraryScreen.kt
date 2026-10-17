package com.leaf.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.reader.core.Formatters
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField
import com.leaf.reader.ui.LibraryState
import com.leaf.reader.ui.components.CoverTile
import com.leaf.reader.ui.components.DocumentRow
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.FileTypeIcon
import com.leaf.reader.ui.components.ProgressBar
import com.leaf.reader.ui.components.SectionHeader
import com.leaf.reader.ui.components.swatchColor
import com.leaf.reader.ui.descendantIds
import com.leaf.reader.ui.inFolder
import com.leaf.reader.ui.matchesRule
import com.leaf.reader.ui.sortDocuments
import com.leaf.reader.ui.sortLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    onOpen: (LeafDocument) -> Unit,
    onToggleFavorite: (LeafDocument) -> Unit,
    onDocumentActions: (LeafDocument) -> Unit,
    onAddDocuments: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onToggleGroupMode: (Boolean) -> Unit,
    onSort: (SortField, SortDirection) -> Unit,
    onManageFolders: () -> Unit,
    onManageTags: () -> Unit,
    onOpenSettings: () -> Unit,
    pickMode: Boolean = false,
    onPickDocument: ((LeafDocument) -> Unit)? = null,
    onCancelPick: (() -> Unit)? = null
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    var selectedTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val documents = remember(state.documents, selectedFolder, selectedCollection, selectedTagIds) {
        var list = state.documents
        if (selectedFolder != null) list = list.inFolder(selectedFolder, state.folders)
        if (selectedCollection != null) {
            val collection = state.collections.firstOrNull { it.id == selectedCollection }
            if (collection != null) list = list.filter { matchesRule(collection.rule, it, state) }
        }
        if (selectedTagIds.isNotEmpty()) {
            list = list.filter { doc ->
                val tags = state.tagsFor(doc.id).map { it.id }.toSet()
                selectedTagIds.all { it in tags }
            }
        }
        list.sortDocuments(state.settings.librarySortField, state.settings.librarySortDirection)
    }

    val resume = state.documents
        .filter { it.lastOpened != null }
        .maxByOrNull { it.lastOpened ?: 0L }
    val continueDocs = state.documents
        .filter { it.startedReading }
        .sortedByDescending { it.lastOpened ?: it.dateAdded }
        .take(5)
    val unfiltered = selectedFolder == null && selectedCollection == null && selectedTagIds.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onAddDocuments) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add documents")
                    }
                    BadgedBox(
                        badge = {
                            if (state.tabs.isNotEmpty()) Badge { Text("${state.tabs.size}") }
                        }
                    ) {
                        IconButton(onClick = onOpenTabs) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Open documents")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.settings.groupByType) {
                                        "Show document list"
                                    } else {
                                        "Group by file type"
                                    }
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onToggleGroupMode(!state.settings.groupByType)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Sort · " + sortLabel(
                                        state.settings.librarySortField,
                                        state.settings.librarySortDirection
                                    )
                                )
                            },
                            onClick = {
                                menuOpen = false
                                sortOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage folders") },
                            onClick = { menuOpen = false; onManageFolders() }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage tags") },
                            onClick = { menuOpen = false; onManageTags() }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menuOpen = false; onOpenSettings() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 840.dp
            val selectedDocument = state.documents.firstOrNull { it.id == selectedId }
            Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            if (pickMode) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choose a document to open in a new tab",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onCancelPick?.invoke() }) { Text("Cancel") }
                        }
                    }
                }
            }
            item {
                FolderChips(
                    state = state,
                    selectedFolder = selectedFolder,
                    selectedCollection = selectedCollection,
                    onFolder = { folderId ->
                        selectedFolder = if (selectedFolder == folderId) null else folderId
                        selectedCollection = null
                    },
                    onCollection = { collectionId ->
                        selectedCollection = if (selectedCollection == collectionId) null else collectionId
                        selectedFolder = null
                    }
                )
            }

            if (state.documents.isEmpty()) {
                item {
                    EmptyState(
                        title = "Your library is empty",
                        message = "Add a PDF, Word, Excel, PowerPoint, text or EPUB file to start reading. Everything stays on this device.",
                        action = {
                            TextButton(onClick = onAddDocuments) { Text("Add documents") }
                        }
                    )
                }
                return@LazyColumn
            }

            if (resume != null && unfiltered) {
                item { ResumeCard(document = resume, onClick = { onOpen(resume) }) }
            }

            if (continueDocs.isNotEmpty() && unfiltered) {
                item {
                    SectionHeader(title = "Continue · last 5")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueDocs, key = { it.id }) { document ->
                            CoverTile(
                                document = document,
                                modifier = Modifier.width(116.dp),
                                onClick = { onOpen(document) }
                            )
                        }
                    }
                }
            }

            if (state.settings.groupByType) {
                item {
                    SectionHeader(
                        title = "File types",
                        subtitle = "Counts follow the current folder, collection and filters"
                    )
                }
                items(com.leaf.reader.model.FileKind.ordered, key = { it.key }) { kind ->
                    val groupDocs = documents.filter { it.kind == kind }
                    if (groupDocs.isNotEmpty()) {
                        FileTypeCard(
                            kind = kind,
                            documents = groupDocs,
                            highlightCount = groupDocs.sumOf { state.highlightsFor(it.id).size },
                            bookmarkCount = groupDocs.sumOf { state.bookmarksFor(it.id).size },
                            onClick = { onOpenGroup(kind.key) }
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                SectionHeader(
                    title = "Documents",
                    subtitle = "${documents.size} shown · " + sortLabel(
                        state.settings.librarySortField,
                        state.settings.librarySortDirection
                    )
                )
            }

            items(documents, key = { it.id }) { document ->
                DocumentRow(
                    document = document,
                    folderName = state.folderName(document.folderId),
                    tags = state.tagsFor(document.id),
                    onClick = {
                        when {
                            pickMode && onPickDocument != null -> onPickDocument(document)
                            wide -> selectedId = document.id
                            else -> onOpen(document)
                        }
                    },
                    onLongClick = { onDocumentActions(document) },
                    onToggleFavorite = { onToggleFavorite(document) },
                    trailingAction = if (pickMode) {
                        {
                            IconButton(onClick = { onPickDocument?.invoke(document) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Open ${document.name} in a new tab")
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            if (documents.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "No document matches this view. Try another folder, collection or tag."
                    )
                }
            }
            }
                if (wide) {
                    DocumentDetailPane(
                        state = state,
                        document = selectedDocument,
                        onOpen = { onOpen(it) },
                        onToggleFavorite = onToggleFavorite,
                        onDocumentActions = onDocumentActions,
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                    )
                }
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

@Composable
private fun DocumentDetailPane(
    state: LibraryState,
    document: LeafDocument?,
    onOpen: (LeafDocument) -> Unit,
    onToggleFavorite: (LeafDocument) -> Unit,
    onDocumentActions: (LeafDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        if (document == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Select a document to see its details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Surface
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(kind = document.kind, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = document.kind.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Size: ${Formatters.fileSize(document.sizeBytes)}")
            Text("Added: ${Formatters.relativeTime(document.dateAdded)}")
            Text(
                text = if (document.lastOpened != null) {
                    "Last opened: ${Formatters.relativeTime(document.lastOpened ?: 0L)}"
                } else {
                    "Never opened"
                }
            )
            Text("Pages: ${document.pageCount}")
            Text("Folder: ${state.folderName(document.folderId) ?: "None"}")
            val tags = state.tagsFor(document.id)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Tags: ${tags.joinToString(", ") { it.name }}")
            }
            if (document.currentPage > 0 && document.pageCount > 0) {
                Spacer(Modifier.height(8.dp))
                ProgressBar(
                    percent = document.progressPercent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { onOpen(document) }) {
                    Text("Open")
                }
                androidx.compose.material3.OutlinedButton(onClick = { onToggleFavorite(document) }) {
                    Text(if (document.isFavorite) "Unstar" else "Star")
                }
                androidx.compose.material3.TextButton(onClick = { onDocumentActions(document) }) {
                    Text("More")
                }
            }
        }
    }
}

@Composable
private fun FolderChips(
    state: LibraryState,
    selectedFolder: String?,
    selectedCollection: String?,
    onFolder: (String?) -> Unit,
    onCollection: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFolder == null && selectedCollection == null,
                onClick = {
                    onFolder(null)
                    onCollection(null)
                },
                label = { Text("All (${state.documents.size})") }
            )
        }
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            val ids = state.folders.descendantIds(folder.id)
            val count = state.documents.count { it.folderId in ids }
            FilterChip(
                selected = selectedFolder == folder.id,
                onClick = { onFolder(folder.id) },
                label = { Text("${folder.name} ($count)") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(swatchColor(folder.colorIndex))
                    )
                }
            )
        }
        items(state.collections, key = { "collection-${it.id}" }) { collection ->
            FilterChip(
                selected = selectedCollection == collection.id,
                onClick = { onCollection(collection.id) },
                label = { Text(collection.name) },
                leadingIcon = {
                    Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
private fun ResumeCard(document: LeafDocument, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Jump back in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Page ${document.currentPage + 1} of ${document.pageCount.coerceAtLeast(1)} · " +
                    Formatters.relativeTime(document.lastOpened ?: document.dateAdded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar(
                percent = document.progressPercent,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Resume reading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun FileTypeCard(
    kind: com.leaf.reader.model.FileKind,
    documents: List<LeafDocument>,
    highlightCount: Int,
    bookmarkCount: Int,
    onClick: () -> Unit
) {
    val totalSize = documents.sumOf { it.sizeBytes }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIcon(kind, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${documents.size} documents · ${Formatters.fileSize(totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$highlightCount highlights · $bookmarkCount bookmarks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
