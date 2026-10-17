package com.leaf.reader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leaf.reader.AppContainer
import com.leaf.reader.data.DocumentRepository
import com.leaf.reader.data.prefs.SettingsStore
import com.leaf.reader.model.LeafBookmark
import com.leaf.reader.model.LeafCollection
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.LeafFolder
import com.leaf.reader.model.LeafHighlight
import com.leaf.reader.model.LeafSettings
import com.leaf.reader.model.LeafTag
import com.leaf.reader.model.OpenTab
import com.leaf.reader.model.RecentEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val documents: List<LeafDocument> = emptyList(),
    val folders: List<LeafFolder> = emptyList(),
    val tags: List<LeafTag> = emptyList(),
    val collections: List<LeafCollection> = emptyList(),
    val recents: List<RecentEntry> = emptyList(),
    val documentTags: Map<String, List<LeafTag>> = emptyMap(),
    val highlights: List<LeafHighlight> = emptyList(),
    val bookmarks: List<LeafBookmark> = emptyList(),
    val settings: LeafSettings = LeafSettings(),
    val tabs: List<OpenTab> = emptyList(),
    val activeTabId: String? = null
) {
    val documentsById: Map<String, LeafDocument> by lazy { documents.associateBy { it.id } }

    fun folderName(folderId: String?): String? = folders.firstOrNull { it.id == folderId }?.name

    fun tagsFor(documentId: String): List<LeafTag> = documentTags[documentId].orEmpty()

    fun highlightsFor(documentId: String): List<LeafHighlight> = highlights.filter { it.documentId == documentId }

    fun bookmarksFor(documentId: String): List<LeafBookmark> = bookmarks.filter { it.documentId == documentId }
}

sealed interface LibraryEvent {
    data class OpenReader(val documentId: String) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
    data class OpenGroup(val kind: String) : LibraryEvent
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: DocumentRepository = container.repository
    private val settingsStore: SettingsStore = container.settingsStore

    private val events = Channel<LibraryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<LibraryEvent> = events.receiveAsFlow()

    private val baseState = combine(
        repository.documents,
        repository.folders,
        repository.tags,
        repository.collections,
        repository.recents
    ) { documents, folders, tags, collections, recents ->
        BaseState(documents, folders, tags, collections, recents)
    }

    private val metaState = combine(
        repository.documentTags,
        repository.highlights,
        repository.bookmarks,
        settingsStore.settings
    ) { documentTags, highlights, bookmarks, settings ->
        MetaState(documentTags, highlights, bookmarks, settings)
    }

    val state: StateFlow<LibraryState> = combine(
        baseState,
        metaState,
        settingsStore.tabs,
        settingsStore.activeTab
    ) { base, meta, tabs, activeTabId ->
        LibraryState(
            documents = base.documents,
            folders = base.folders,
            tags = base.tags,
            collections = base.collections,
            recents = base.recents,
            documentTags = meta.documentTags,
            highlights = meta.highlights,
            bookmarks = meta.bookmarks,
            settings = meta.settings,
            tabs = tabs,
            activeTabId = activeTabId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())

    private data class BaseState(
        val documents: List<LeafDocument>,
        val folders: List<LeafFolder>,
        val tags: List<LeafTag>,
        val collections: List<LeafCollection>,
        val recents: List<RecentEntry>
    )

    private data class MetaState(
        val documentTags: Map<String, List<LeafTag>>,
        val highlights: List<LeafHighlight>,
        val bookmarks: List<LeafBookmark>,
        val settings: LeafSettings
    )

    // ------------------------------------------------------------- documents

    fun addDocuments(uris: List<Uri>) {
        viewModelScope.launch {
            val imported = repository.importDocuments(uris)
            if (imported.isEmpty()) {
                events.send(LibraryEvent.Message("No supported documents were added"))
            } else {
                events.send(
                    LibraryEvent.Message("Added ${imported.size} document${if (imported.size == 1) "" else "s"}")
                )
            }
        }
    }

    fun toggleFavorite(document: LeafDocument) {
        viewModelScope.launch {
            repository.setFavorite(document, !document.isFavorite)
        }
    }

    fun renameDocument(document: LeafDocument, newName: String) {
        viewModelScope.launch {
            repository.renameDocument(document, newName)
            events.send(LibraryEvent.Message("Renamed to $newName"))
        }
    }

    fun deleteDocument(document: LeafDocument) {
        viewModelScope.launch {
            repository.removeDocument(document)
            events.send(LibraryEvent.Message("Removed ${document.name}"))
        }
    }

    fun moveToFolder(document: LeafDocument, folderId: String?) {
        viewModelScope.launch {
            repository.moveToFolder(document, folderId)
            val name = folderId?.let { state.value.folderName(it) } ?: "no folder"
            events.send(LibraryEvent.Message("Moved to $name"))
        }
    }

    fun setTags(document: LeafDocument, tagIds: Set<String>) {
        viewModelScope.launch {
            repository.setDocumentTags(document.id, tagIds)
            events.send(LibraryEvent.Message("Tags updated"))
        }
    }

    /** Opens a document in a tab and asks the shell to show the reader. */
    fun openDocument(document: LeafDocument) {
        viewModelScope.launch {
            val settings = state.value.settings
            val tabs = state.value.tabs
            if (tabs.none { it.documentId == document.id }) {
                if (tabs.size >= settings.maxTabs) {
                    events.send(
                        LibraryEvent.Message(
                            "Tab limit reached (${settings.maxTabs}) — close a tab or raise the limit in Settings"
                        )
                    )
                    return@launch
                }
                settingsStore.setTabs(tabs + OpenTab(document.id))
            }
            settingsStore.setActiveTab(document.id)
            repository.logOpened(document.id)
            events.send(LibraryEvent.OpenReader(document.id))
        }
    }

    /** Opens a document in a brand new tab (spec 5.18). */
    fun openDocumentInNewTab(document: LeafDocument) {
        viewModelScope.launch {
            val settings = state.value.settings
            val tabs = state.value.tabs
            if (tabs.size >= settings.maxTabs) {
                events.send(
                    LibraryEvent.Message(
                        "Tab limit reached (${settings.maxTabs}) — close a tab or raise the limit in Settings"
                    )
                )
                return@launch
            }
            settingsStore.setTabs(tabs + OpenTab(document.id))
            settingsStore.setActiveTab(document.id)
            repository.logOpened(document.id)
            container.pickMode.value = false
            events.send(LibraryEvent.OpenReader(document.id))
        }
    }

    // --------------------------------------------------------------- folders

    fun createFolder(name: String, parentId: String?, colorIndex: Int) {
        viewModelScope.launch {
            repository.createFolder(name, parentId, colorIndex)
            events.send(LibraryEvent.Message("Folder created"))
        }
    }

    fun renameFolder(folder: LeafFolder, name: String, colorIndex: Int) {
        viewModelScope.launch {
            repository.renameFolder(folder.id, name, colorIndex)
        }
    }

    fun deleteFolder(folder: LeafFolder) {
        viewModelScope.launch {
            repository.deleteFolder(folder.id)
            events.send(LibraryEvent.Message("Folder deleted — documents were kept"))
        }
    }

    // ------------------------------------------------------------------ tags

    fun createTag(name: String, colorIndex: Int) {
        viewModelScope.launch {
            repository.createTag(name, colorIndex)
        }
    }

    fun renameTag(tag: LeafTag, name: String, colorIndex: Int) {
        viewModelScope.launch {
            repository.renameTag(tag.id, name, colorIndex)
        }
    }

    fun deleteTag(tag: LeafTag) {
        viewModelScope.launch {
            repository.deleteTag(tag.id)
            events.send(LibraryEvent.Message("Tag removed — documents were kept"))
        }
    }

    // ----------------------------------------------------------- collections

    fun createCollection(name: String, rule: String) {
        viewModelScope.launch {
            val existing = repository.collectionByName(name)
            if (existing != null) {
                events.send(LibraryEvent.Message("A collection called \"$name\" already exists"))
            } else {
                repository.createCollection(name, rule)
                events.send(LibraryEvent.Message("Collection created"))
            }
        }
    }

    fun deleteCollection(collection: LeafCollection) {
        viewModelScope.launch {
            repository.deleteCollection(collection.id)
        }
    }

    // --------------------------------------------------------------- recents

    fun removeRecent(documentId: String) {
        viewModelScope.launch { repository.removeRecent(documentId) }
    }

    /** Puts a removed recent entry back (undo, spec 2.5). */
    fun restoreRecent(documentId: String) {
        viewModelScope.launch { repository.restoreRecent(documentId) }
    }

    fun clearRecents() {
        viewModelScope.launch {
            repository.clearRecents()
            events.send(LibraryEvent.Message("Recents cleared"))
        }
    }

    fun clearTodayRecents() {
        viewModelScope.launch {
            repository.clearTodayRecents()
            events.send(LibraryEvent.Message("Today's recents cleared"))
        }
    }

    // ------------------------------------------------------------------ tabs

    fun closeTab(documentId: String) {
        viewModelScope.launch {
            val tabs = state.value.tabs
            val remaining = tabs.filterNot { it.documentId == documentId }
            settingsStore.setTabs(remaining)
            if (state.value.activeTabId == documentId) {
                settingsStore.setActiveTab(remaining.lastOrNull()?.documentId)
            }
        }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            settingsStore.setTabs(emptyList())
            settingsStore.setActiveTab(null)
        }
    }

    fun focusTab(documentId: String) {
        viewModelScope.launch { settingsStore.setActiveTab(documentId) }
    }

    // -------------------------------------------------------------- settings

    fun setLibrarySort(field: com.leaf.reader.model.SortField, direction: com.leaf.reader.model.SortDirection) {
        viewModelScope.launch { settingsStore.setLibrarySort(field, direction) }
    }

    fun setFavoritesSort(field: com.leaf.reader.model.SortField, direction: com.leaf.reader.model.SortDirection) {
        viewModelScope.launch { settingsStore.setFavoritesSort(field, direction) }
    }

    fun setGroupByType(value: Boolean) {
        viewModelScope.launch { settingsStore.setGroupByType(value) }
    }

    // --------------------------------------------------------- search history

    fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val history = state.value.settings.searchHistory
            val updated = (listOf(trimmed) + history.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
            settingsStore.setSearchHistory(updated)
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            val updated = state.value.settings.searchHistory.filterNot { it == query }
            settingsStore.setSearchHistory(updated)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { settingsStore.setSearchHistory(emptyList()) }
    }
}

class LeafViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(container) as T
        modelClass.isAssignableFrom(com.leaf.reader.reader.ReaderViewModel::class.java) ->
            com.leaf.reader.reader.ReaderViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
