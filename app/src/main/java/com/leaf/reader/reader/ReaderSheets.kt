package com.leaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leaf.reader.core.Formatters
import com.leaf.reader.model.LeafBookmark
import com.leaf.reader.model.LeafHighlight
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.ui.components.LeafBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsSheet(
    highlights: List<LeafHighlight>,
    colors: ReaderColors,
    onDismiss: () -> Unit,
    onJump: (LeafHighlight) -> Unit,
    onDelete: (LeafHighlight) -> Unit,
    onClear: () -> Unit
) {
    LeafBottomSheet(title = "Highlights (${highlights.size})", onDismiss = onDismiss) {
        if (highlights.isEmpty()) {
            Text(
                text = "No highlights yet. Select text on the page and pick a colour.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(highlights, key = { it.id }) { highlight ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(highlight) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.tint(highlight.color))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Page ${highlight.page + 1} · ${Formatters.relativeTime(highlight.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = highlight.quote.ifBlank { "Highlighted area" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3
                            )
                        }
                        IconButton(onClick = { onDelete(highlight) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete highlight on page ${highlight.page + 1}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onClear,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text("Clear all highlights", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<LeafBookmark>,
    colors: ReaderColors,
    onDismiss: () -> Unit,
    onJump: (LeafBookmark) -> Unit,
    onDelete: (LeafBookmark) -> Unit,
    onAdd: () -> Unit
) {
    LeafBottomSheet(title = "Bookmarks (${bookmarks.size})", onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Bookmark this page")
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                text = "No bookmarks yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(bookmark) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${bookmark.page + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Formatters.relativeTime(bookmark.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete bookmark on page ${bookmark.page + 1}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMoreSheet(
    state: ReaderUiState,
    colors: ReaderColors,
    onDismiss: () -> Unit,
    onTheme: (ReadingTheme) -> Unit,
    onReflow: (Boolean) -> Unit,
    onOrientation: () -> Unit,
    onRotate: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyPageText: () -> Unit,
    onShareDocument: () -> Unit,
    onSharePage: () -> Unit
) {
    LeafBottomSheet(title = state.document?.name ?: "Reader", onDismiss = onDismiss) {
        Text(
            text = "Reading theme",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadingTheme.values().forEach { theme ->
                FilterChip(
                    selected = state.theme == theme,
                    onClick = { onTheme(theme) },
                    label = { Text(theme.label) }
                )
            }
        }
        Text(
            text = "Text reflow · ${if (state.reflow) "on" else "off"}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clickable { onReflow(!state.reflow) }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
        SheetRow("Rotate orientation", Icons.Outlined.RotateRight, onRotate)
        SheetRow("Share document", Icons.Outlined.Share, onShareDocument)
        SheetRow("Share this page as image", Icons.Outlined.Share, onSharePage)
        SheetRow("Copy page text", Icons.Outlined.TextFields, onCopyPageText)
        SheetRow("Copy file path", Icons.Outlined.ContentCopy, onCopyPath)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ConfirmDeleteDialog(
    documentName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $documentName?") },
        text = { Text("The document is removed from your library. This cannot be undone once the file is gone.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
