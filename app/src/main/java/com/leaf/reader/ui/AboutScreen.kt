package com.leaf.reader.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leaf.reader.BuildConfig
import com.leaf.reader.R
import com.leaf.reader.ui.components.SectionHeader

private data class CoverageItem(val title: String, val detail: String)

private val coverage = listOf(
    CoverageItem("Library", "List with type icons, folders, tags, size and reading progress"),
    CoverageItem("Jump back in", "Most recent document with page, time and progress bar"),
    CoverageItem("Continue · last 5", "Cover tiles for the five most recent documents"),
    CoverageItem("Recents", "Today and Earlier groups, per-entry removal, clear today and clear all"),
    CoverageItem("Favorites", "Own sort control, instant starring with haptics"),
    CoverageItem("Folders", "Custom folders, 10 colours, nesting, safe deletion that keeps documents"),
    CoverageItem("Smart collections", "All PDFs, added this week/month, still reading, not filed, has highlights"),
    CoverageItem("Tags", "10 colour palette, multi-tag documents, AND filtering, safe deletion"),
    CoverageItem("Page stay", "Page, scroll, zoom, direction, reflow, theme and orientation per document"),
    CoverageItem("Reading themes", "Paper, Sepia, Night, OLED plus Auto that follows the system"),
    CoverageItem("Viewing modes", "Vertical pages, horizontal book paging, pinch and double-tap zoom, reflow"),
    CoverageItem("Highlights", "Five colours, recolour, remove, copy, 50 step undo and redo"),
    CoverageItem("Bookmarks", "Several per document, jump, delete and add from the toolbar"),
    CoverageItem("Tabs", "Several open documents, badge, close, close all, configurable limit"),
    CoverageItem("Search", "Names, tags, folders, full text inside files, filters, history"),
    CoverageItem("File management", "Rename, delete, move, tag, favourite, file info"),
    CoverageItem("Share", "Whole document, page as image, selection, copy path and page text"),
    CoverageItem("Settings", "Appearance, reading, device, data storage and about"),
    CoverageItem("Offline", "No internet permission, no account, no analytics, no tracking")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: LibraryState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Leaf") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher),
                                contentDescription = "Leaf app icon",
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Leaf",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} · offline reader for Android 6.0+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(14.dp))
                        Row {
                            CountTile("${state.documents.size}", "documents")
                            CountTile("${state.folders.size}", "folders")
                            CountTile("${state.tags.size}", "tags")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            CountTile("${state.highlights.size}", "highlights")
                            CountTile("${state.bookmarks.size}", "bookmarks")
                            CountTile("${state.tabs.size}", "open tabs")
                        }
                    }
                }
            }

            item { SectionHeader(title = "Spec coverage", subtitle = "${coverage.size} areas implemented") }

            items(coverage.size) { index ->
                val item = coverage[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Implemented",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Leaf keeps every document where you granted access. It makes no network calls, stores no analytics and requires no account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CountTile(value: String, label: String) {
    Column(modifier = Modifier.width(96.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
