package com.leaf.reader.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaf.reader.core.DeviceFile
import com.leaf.reader.core.Formatters
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.FileTypeIcon
import com.leaf.reader.ui.components.SectionHeader

/**
 * Lists every supported document that already lives on the device.
 *
 * Leaf never copies files: adding one only remembers the uri that was already granted, so the
 * document keeps living where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    files: List<DeviceFile>,
    scanning: Boolean,
    message: String?,
    addedUris: Set<String>,
    onBrowseFolder: () -> Unit,
    onScanStorage: () -> Unit,
    onAdd: (DeviceFile) -> Unit,
    onOpen: (DeviceFile) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device files") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Everything on this device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Leaf reads PDF, DOCX, XLSX, PPTX, TXT and EPUB. Choose a folder to " +
                                "browse it — no permission prompt, and your files are never copied.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBrowseFolder) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choose folder")
                            }
                            OutlinedButton(onClick = onScanStorage) {
                                Text("Scan storage")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "“Choose folder” never asks for a permission. “Scan storage” " +
                                "searches the whole device and asks for storage access once.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (scanning) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }

            message?.let { note ->
                item {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (files.isEmpty() && !scanning) {
                item {
                    EmptyState(
                        title = "No device documents yet",
                        message = "Choose a folder above to see the PDFs, documents, spreadsheets, " +
                            "presentations, text files and books stored on this device."
                    )
                }
            }

            val grouped = files.groupBy { it.location }
            grouped.forEach { (location, group) ->
                item(key = "header-$location") {
                    SectionHeader(title = location)
                }
                items(group, key = { it.uri }) { file ->
                    DeviceFileRow(
                        file = file,
                        added = file.uri in addedUris,
                        onAdd = { onAdd(file) },
                        onOpen = { onOpen(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceFileRow(
    file: DeviceFile,
    added: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(kind = file.kind, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpen() }
            )
            Text(
                text = "${file.kind.label} · ${Formatters.fileSize(file.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAdd) {
            if (added) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "${file.name} is in your library",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.Outlined.Add, contentDescription = "Add ${file.name} to your library")
            }
        }
    }
}
