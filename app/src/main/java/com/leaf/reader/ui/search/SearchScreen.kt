package com.leaf.reader.ui.search

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.leaf.reader.AppContainer
import com.leaf.reader.model.FileKind
import com.leaf.reader.model.InFileMatch
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.SearchSummary
import com.leaf.reader.ui.LibraryState
import com.leaf.reader.ui.components.EmptyState
import com.leaf.reader.ui.components.FileTypeIcon
import com.leaf.reader.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class SearchFilters(
    val kind: FileKind? = null,
    val folderId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val addedWithinDays: Int? = null
)

data class SearchOutcome(
    val nameMatches: List<LeafDocument> = emptyList(),
    val inFileMatches: List<InFileMatch> = emptyList(),
    val summary: SearchSummary = SearchSummary(0, 0, 0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    state: LibraryState,
    onOpen: (LeafDocument, Int, String) -> Unit,
    onDocumentActions: (LeafDocument) -> Unit,
    onRecordSearch: (String) -> Unit = {},
    onRemoveHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var outcome by remember { mutableStateOf(SearchOutcome()) }
    var searching by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    val history = state.settings.searchHistory

    LaunchedEffect(query, filters, state.documents) {
        if (query.isBlank()) {
            outcome = SearchOutcome()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(220)
        outcome = runSearch(container, state, query, filters)
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { filterSheetOpen = true }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search names, tags, folders and file contents") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onRecordSearch(query)
                })
            )

            if (filters.kind != null || filters.folderId != null || filters.tagIds.isNotEmpty() || filters.addedWithinDays != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.kind?.let { AssistChip(onClick = {}, label = { Text(it.label) }) }
                    filters.folderId?.let { id ->
                        AssistChip(onClick = {}, label = { Text(state.folderName(id) ?: "Folder") })
                    }
                    filters.addedWithinDays?.let {
                        AssistChip(onClick = {}, label = { Text("Last $it days") })
                    }
                    filters.tagIds.forEach { id ->
                        AssistChip(onClick = {}, label = { Text(state.tags.firstOrNull { it.id == id }?.name ?: "Tag") })
                    }
                }
            }

            if (searching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }

            if (query.isBlank()) {
                SearchHistory(
                    history = history,
                    onRun = { query = it },
                    onRemove = { onRemoveHistory(it) },
                    onClear = { onClearHistory() }
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        SectionHeader(
                            title = "Results",
                            subtitle = "${outcome.summary.documents} documents · " +
                                "${outcome.summary.filesContaining} files contain \"$query\" " +
                                "(${outcome.summary.hits} hits)"
                        )
                    }
                    if (outcome.nameMatches.isNotEmpty()) {
                        item { SectionHeader(title = "Matching names, tags and folders") }
                        items(outcome.nameMatches, key = { "name-${it.id}" }) { document ->
                            ResultRow(
                                title = document.name,
                                subtitle = "${document.kind.label} · ${state.folderName(document.folderId) ?: "No folder"}",
                                onClick = { onOpen(document, document.currentPage, query) }
                            )
                        }
                    }
                    if (outcome.inFileMatches.isNotEmpty()) {
                        item { SectionHeader(title = "Inside files") }
                        items(outcome.inFileMatches, key = { "file-${it.documentId}-${it.matchStart}" }) { match ->
                            val document = state.documents.firstOrNull { it.id == match.documentId }
                            InFileResult(
                                match = match,
                                query = query,
                                onClick = { if (document != null) onOpen(document, match.page, query) }
                            )
                        }
                    }
                    if (outcome.nameMatches.isEmpty() && outcome.inFileMatches.isEmpty() && !searching) {
                        item {
                            EmptyState(
                                title = "No matches",
                                message = "Nothing matched \"$query\". Try a different word, a tag, or a folder name."
                            )
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        SearchFilterSheet(
            state = state,
            filters = filters,
            onApply = { filters = it; filterSheetOpen = false },
            onDismiss = { filterSheetOpen = false }
        )
    }
}

@Composable
private fun SearchHistory(
    history: List<String>,
    onRun: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (history.isNotEmpty()) {
            SectionHeader(
                title = "Recent searches",
                action = { TextButton(onClick = onClear) { Text("Clear all") } }
            )
            history.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRun(entry) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(entry, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove $entry from history")
                    }
                }
            }
        } else {
            SectionHeader(title = "Quick searches")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { onRun("All PDFs") }, label = { Text("All PDFs") })
                AssistChip(onClick = { onRun("Added this week") }, label = { Text("Added this week") })
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { onRun("Work") }, label = { Text("Folder · Work") })
                AssistChip(onClick = { onRun("important") }, label = { Text("Tag · important") })
                AssistChip(onClick = { onRun("chapter") }, label = { Text("Word · chapter") })
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InFileResult(match: InFileMatch, query: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(match.documentKind, size = 34)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = match.documentName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${match.count} hit${if (match.count == 1) "" else "s"} · p${match.page + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = emphasised(match.snippet, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

private fun emphasised(snippet: String, query: String) = buildAnnotatedString {
    var index = 0
    val lowerSnippet = snippet.lowercase()
    val lowerQuery = query.lowercase()
    while (index < snippet.length) {
        val found = lowerSnippet.indexOf(lowerQuery, index)
        if (found < 0 || query.isEmpty()) {
            append(snippet.substring(index))
            break
        }
        append(snippet.substring(index, found))
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                background = MaterialThemeColorHighlight
            )
        ) {
            append(snippet.substring(found, found + query.length))
        }
        index = found + query.length
    }
}

private val MaterialThemeColorHighlight = androidx.compose.ui.graphics.Color(0x44FFD54F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSheet(
    state: LibraryState,
    filters: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(filters.kind) }
    var folderId by remember { mutableStateOf(filters.folderId) }
    var tagIds by remember { mutableStateOf(filters.tagIds) }
    var addedDays by remember { mutableStateOf(filters.addedWithinDays) }

    com.leaf.reader.ui.components.LeafBottomSheet(title = "Filters", onDismiss = onDismiss) {
        Text(
            text = "File type",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileKind.ordered.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = if (kind == option) null else option },
                    label = { Text(option.label) }
                )
            }
        }
        Text(
            text = "Added within",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = addedDays == days,
                    onClick = { addedDays = if (addedDays == days) null else days },
                    label = { Text("$days days") }
                )
            }
        }
        if (state.folders.isNotEmpty()) {
            Text(
                text = "Folder",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.folders.take(6).forEach { folder ->
                    FilterChip(
                        selected = folderId == folder.id,
                        onClick = { folderId = if (folderId == folder.id) null else folder.id },
                        label = { Text(folder.name) }
                    )
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            Text(
                text = "Tags (all must match)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.tags.take(8).forEach { tag ->
                    FilterChip(
                        selected = tagIds.contains(tag.id),
                        onClick = {
                            tagIds = if (tagIds.contains(tag.id)) tagIds - tag.id else tagIds + tag.id
                        },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onApply(SearchFilters(kind, folderId, tagIds, addedDays)) }) {
                Text("Apply")
            }
        }
    }
}

/** Runs the combined search: names, tags, folders and full text. */
suspend fun runSearch(
    container: AppContainer,
    state: LibraryState,
    query: String,
    filters: SearchFilters
): SearchOutcome = withContext(Dispatchers.IO) {
    val needle = query.trim()
    if (needle.isEmpty()) return@withContext SearchOutcome()

    var candidates = state.documents
    if (filters.kind != null) candidates = candidates.filter { it.kind == filters.kind }
    if (filters.folderId != null) candidates = candidates.filter { it.folderId == filters.folderId }
    if (filters.tagIds.isNotEmpty()) {
        candidates = candidates.filter { doc ->
            val tags = state.tagsFor(doc.id).map { it.id }.toSet()
            filters.tagIds.all { it in tags }
        }
    }
    if (filters.addedWithinDays != null) {
        val cutoff = System.currentTimeMillis() - filters.addedWithinDays * 24L * 60L * 60L * 1000L
        candidates = candidates.filter { it.dateAdded >= cutoff }
    }

    val lower = needle.lowercase()
    val nameMatches = candidates.filter { document ->
        document.name.lowercase().contains(lower) ||
            state.tagsFor(document.id).any { it.name.lowercase().contains(lower) } ||
            (state.folderName(document.folderId)?.lowercase()?.contains(lower) == true)
    }

    val inFileMatches = mutableListOf<InFileMatch>()
    var totalHits = 0
    var filesWithHits = 0
    candidates.take(40).forEach { document ->
        val extracted = runCatching { container.repository.ensureText(document) }.getOrNull() ?: return@forEach
        if (extracted.text.isBlank()) return@forEach
        val haystack = extracted.text.lowercase()
        var index = haystack.indexOf(lower)
        if (index < 0) return@forEach
        var count = 0
        var first = index
        while (index >= 0) {
            count++
            if (count == 1) first = index
            index = haystack.indexOf(lower, index + lower.length)
        }
        totalHits += count
        filesWithHits++
        val page = pageOf(extracted.pageOffsets, first)
        val snippet = snippetAround(extracted.text, first, needle.length)
        inFileMatches.add(
            InFileMatch(
                documentId = document.id,
                documentName = document.name,
                documentKind = document.kind,
                page = page,
                count = count,
                snippet = snippet,
                matchStart = first,
                matchEnd = first + needle.length
            )
        )
    }

    SearchOutcome(
        nameMatches = nameMatches,
        inFileMatches = inFileMatches,
        summary = SearchSummary(
            documents = candidates.size,
            filesContaining = filesWithHits,
            hits = totalHits
        )
    )
}

private fun pageOf(offsets: List<Int>, charOffset: Int): Int {
    if (offsets.isEmpty()) return 0
    var page = 0
    for (index in offsets.indices) {
        if (offsets[index] <= charOffset) page = index else break
    }
    return page
}

private fun snippetAround(text: String, offset: Int, length: Int): String {
    val start = (offset - 40).coerceAtLeast(0)
    val end = (offset + length + 60).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
}
