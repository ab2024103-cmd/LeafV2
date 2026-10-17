package com.leaf.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leaf.reader.core.Formatters
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.LeafFolder
import com.leaf.reader.model.LeafTag
import com.leaf.reader.model.OpenTab
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeafBottomSheet(
    title: String?,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun SheetAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onConfirm(value.trim())
                        onDismiss()
                    }
                },
                enabled = value.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentActionsSheet(
    document: LeafDocument,
    folderName: String?,
    tagNames: List<String>,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onTags: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onCopyPath: () -> Unit
) {
    LeafBottomSheet(title = document.name, onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIcon(document.kind, size = 40)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = document.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildString {
                        append(Formatters.fileSize(document.sizeBytes))
                        if (folderName != null) append(" · $folderName")
                        if (tagNames.isNotEmpty()) append(" · ${tagNames.joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        SheetAction("Open", Icons.Outlined.OpenInNew) { onOpen(); onDismiss() }
        SheetAction(
            if (document.isFavorite) "Remove from favorites" else "Add to favorites",
            if (document.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder
        ) { onToggleFavorite(); onDismiss() }
        SheetAction("Rename", Icons.Outlined.Edit) { onRename(); onDismiss() }
        SheetAction("Move to folder", Icons.Outlined.DriveFileMove) { onMove(); onDismiss() }
        SheetAction("Tags", Icons.Outlined.Label) { onTags(); onDismiss() }
        SheetAction("File info", Icons.Outlined.Info) { onInfo(); onDismiss() }
        SheetAction("Share", Icons.Outlined.Share) { onShare(); onDismiss() }
        SheetAction("Copy path", Icons.Outlined.ContentCopy) { onCopyPath(); onDismiss() }
        SheetAction("Delete", Icons.Outlined.Delete, MaterialTheme.colorScheme.error) { onDelete(); onDismiss() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(
    folders: List<LeafFolder>,
    currentFolderId: String?,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    LeafBottomSheet(title = "Move to folder", onDismiss = onDismiss) {
        LazyColumn(modifier = Modifier.height(320.dp)) {
            item {
                SheetAction("Remove from folder", Icons.Outlined.DriveFileMove) {
                    onMove(null)
                    onDismiss()
                }
            }
            items(folders, key = { it.id }) { folder ->
                val depth = folderDepth(folders, folder.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onMove(folder.id)
                            onDismiss()
                        }
                        .padding(start = (20 + depth * 20).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = swatchColor(folder.colorIndex))
                    Spacer(Modifier.width(12.dp))
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                    if (folder.id == currentFolderId) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Check, contentDescription = "Current folder", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun folderDepth(folders: List<LeafFolder>, id: String): Int {
    var depth = 0
    var current = folders.firstOrNull { it.id == id }
    val guard = folders.size + 2
    var steps = 0
    while (current?.parentId != null && steps < guard) {
        depth++
        current = folders.firstOrNull { it.id == current?.parentId }
        steps++
    }
    return depth
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    tags: List<LeafTag>,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    LeafBottomSheet(title = "Tags", onDismiss = onDismiss) {
        if (tags.isEmpty()) {
            Text(
                text = "No tags yet. Create one in the tag manager.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onApply(
                                    if (selected.contains(tag.id)) selected - tag.id else selected + tag.id
                                )
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(tag.colorIndex)
                        Spacer(Modifier.width(12.dp))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (selected.contains(tag.id)) {
                            Icon(Icons.Filled.Check, contentDescription = "Applied", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SheetAction("Manage tags", Icons.Outlined.Label) { onManage(); onDismiss() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    currentField: SortField,
    currentDirection: SortDirection,
    onSelect: (SortField, SortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    LeafBottomSheet(title = "Sort by", onDismiss = onDismiss) {
        SortField.entries.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(field, currentDirection)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (field == currentField) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = if (currentDirection == SortDirection.ASCENDING) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                    onSelect(currentField, next)
                    onDismiss()
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentDirection == SortDirection.ASCENDING) "Ascending (A–Z)" else "Descending (Z–A)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenDocumentsSheet(
    tabs: List<OpenTab>,
    activeId: String?,
    documents: List<LeafDocument>,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    LeafBottomSheet(
        title = "Open documents (${tabs.size})",
        onDismiss = onDismiss
    ) {
        if (tabs.isEmpty()) {
            Text(
                text = "No documents are open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(tabs, key = { it.documentId }) { tab ->
                    val document = documents.firstOrNull { it.id == tab.documentId }
                    val pageCount = (document?.pageCount ?: 1).coerceAtLeast(1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tab.documentId) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileTypeIcon(document?.kind ?: com.leaf.reader.model.FileKind.TXT, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document?.name ?: "Document",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "Page ${tab.page + 1} of $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tab.documentId == activeId) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                        IconButton(onClick = { onClose(tab.documentId) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close ${document?.name ?: "document"}")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SheetAction("Open a new document…", Icons.Filled.Add) { onNewTab(); onDismiss() }
        SheetAction("Close all tabs", Icons.Outlined.Delete, MaterialTheme.colorScheme.error) { onCloseAll(); onDismiss() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagerSheet(
    folders: List<LeafFolder>,
    documentCounts: Map<String, Int>,
    childCounts: Map<String, Int>,
    onCreate: (String, String?) -> Unit,
    onRename: (LeafFolder, String, Int) -> Unit,
    onDelete: (LeafFolder) -> Unit,
    onMove: (LeafFolder, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var newParentId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<LeafFolder?>(null) }
    var deleting by remember { mutableStateOf<LeafFolder?>(null) }
    var moving by remember { mutableStateOf<LeafFolder?>(null) }
    var parentMenuOpen by remember { mutableStateOf(false) }

    LeafBottomSheet(title = "Folders", onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("New folder") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box {
                androidx.compose.material3.FilledTonalButton(onClick = { parentMenuOpen = true }) {
                    Text(
                        text = folders.firstOrNull { it.id == newParentId }?.name ?: "Top level",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = parentMenuOpen,
                    onDismissRequest = { parentMenuOpen = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Top level") },
                        onClick = {
                            newParentId = null
                            parentMenuOpen = false
                        }
                    )
                    folders.forEach { candidate ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(candidate.name) },
                            onClick = {
                                newParentId = candidate.id
                                parentMenuOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreate(newFolderName.trim(), newParentId)
                        newFolderName = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create folder")
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.height(320.dp)) {
            items(folders, key = { it.id }) { folder ->
                val depth = folderDepth(folders, folder.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (20 + depth * 20).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(folder.colorIndex)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${documentCounts[folder.id] ?: 0} documents · ${childCounts[folder.id] ?: 0} subfolders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        newParentId = folder.id
                        newFolderName = ""
                    }) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "New subfolder inside ${folder.name}"
                        )
                    }
                    IconButton(onClick = { moving = folder }) {
                        Icon(
                            Icons.Outlined.DriveFileMove,
                            contentDescription = "Move ${folder.name}"
                        )
                    }
                    IconButton(onClick = { renaming = folder }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename ${folder.name}")
                    }
                    IconButton(onClick = { deleting = folder }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete ${folder.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    renaming?.let { folder ->
        TextInputDialog(
            title = "Rename folder",
            label = "Folder name",
            initial = folder.name,
            onConfirm = { name -> onRename(folder, name, folder.colorIndex) },
            onDismiss = { renaming = null }
        )
    }

    moving?.let { folder ->
        LeafBottomSheet(title = "Move ${folder.name}", onDismiss = { moving = null }) {
            folders.filter { it.id != folder.id && it.id != folder.parentId }.forEach { candidate ->
                SheetAction(
                    title = candidate.name,
                    icon = Icons.Outlined.Folder,
                    onClick = {
                        onMove(folder, candidate.id)
                        moving = null
                    }
                )
            }
            SheetAction(
                title = "Top level",
                icon = Icons.Outlined.FolderOpen,
                onClick = {
                    onMove(folder, null)
                    moving = null
                }
            )
        }
    }

    deleting?.let { folder ->
        ConfirmDialog(
            title = "Delete ${folder.name}?",
            message = "Documents in this folder are kept and moved out of it.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(folder) },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerSheet(
    tags: List<LeafTag>,
    usage: Map<String, Int>,
    onCreate: (String, Int) -> Unit,
    onRename: (LeafTag, String, Int) -> Unit,
    onDelete: (LeafTag) -> Unit,
    onDismiss: () -> Unit
) {
    var newTagName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<LeafTag?>(null) }
    var deleting by remember { mutableStateOf<LeafTag?>(null) }

    LeafBottomSheet(title = "Tags", onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                label = { Text("New tag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newTagName.isNotBlank()) {
                        onCreate(newTagName.trim(), newColor)
                        newTagName = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create tag")
            }
        }
        SwatchRow(selected = newColor, onSelect = { newColor = it })
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.height(300.dp)) {
            items(tags, key = { it.id }) { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(tag.colorIndex)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${usage[tag.id] ?: 0} documents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { renaming = tag }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename ${tag.name}")
                    }
                    IconButton(onClick = { deleting = tag }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete ${tag.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    renaming?.let { tag ->
        TextInputDialog(
            title = "Rename tag",
            label = "Tag name",
            initial = tag.name,
            onConfirm = { name -> onRename(tag, name, tag.colorIndex) },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { tag ->
        ConfirmDialog(
            title = "Delete ${tag.name}?",
            message = "The tag is removed from every document. Documents are never deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(tag) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
fun SwatchRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(10) { index ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(swatchColor(index))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                if (index == selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Colour ${index + 1}",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPickerSheet(
    collections: List<com.leaf.reader.model.LeafCollection>,
    onPick: (com.leaf.reader.model.LeafCollection) -> Unit,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newRule by remember { mutableStateOf("added_week") }
    LeafBottomSheet(title = "Collections", onDismiss = onDismiss) {
        collections.forEach { collection ->
            SheetAction(collection.name, Icons.Outlined.Folder) { onPick(collection); onDismiss() }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New smart collection",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "added_week" to "This week",
                "added_month" to "This month",
                "still_reading" to "Still reading",
                "not_filed" to "Not filed",
                "has_highlights" to "Has highlights",
                "all_pdfs" to "All PDFs"
            ).forEach { (rule, label) ->
                FilterChip(
                    selected = newRule == rule,
                    onClick = { newRule = rule },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (newName.isNotBlank()) {
                    onCreate(newName.trim(), newRule)
                    newName = ""
                }
            },
            enabled = newName.isNotBlank(),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) { Text("Create collection") }
    }
}
