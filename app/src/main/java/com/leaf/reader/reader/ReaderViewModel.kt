package com.leaf.reader.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.reader.AppContainer
import com.leaf.reader.data.DocumentRepository
import com.leaf.reader.data.prefs.SettingsStore
import com.leaf.reader.engine.PdfSession
import com.leaf.reader.engine.TextSession
import com.leaf.reader.model.LeafBookmark
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.LeafHighlight
import com.leaf.reader.model.NormRect
import com.leaf.reader.model.OrientationLock
import com.leaf.reader.model.HighlightColor
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.model.ScrollDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class FindMatch(
    val page: Int,
    val start: Int,
    val end: Int,
    val rects: List<NormRect> = emptyList()
)

data class ReaderUiState(
    val document: LeafDocument? = null,
    val page: Int = 0,
    val pageCount: Int = 1,
    val zoom: Float = 1f,
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val reflow: Boolean = false,
    val theme: ReadingTheme = ReadingTheme.AUTO,
    val orientation: OrientationLock = OrientationLock.AUTO,
    val fullScreen: Boolean = false,
    val highlights: List<LeafHighlight> = emptyList(),
    val bookmarks: List<LeafBookmark> = emptyList(),
    val selection: IntRange? = null,
    val findQuery: String = "",
    val findMatches: List<FindMatch> = emptyList(),
    val findIndex: Int = 0,
    val showFind: Boolean = false,
    val showHighlights: Boolean = false,
    val showBookmarks: Boolean = false,
    val showTabs: Boolean = false,
    val showMore: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val loading: Boolean = true,
    val lastColor: HighlightColor = HighlightColor.YELLOW,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeHighlightId: String? = null
)

private sealed interface HighlightOp {
    data class Add(val highlight: LeafHighlight) : HighlightOp
    data class Remove(val highlight: LeafHighlight) : HighlightOp
    data class Recolor(val before: LeafHighlight, val after: LeafHighlight) : HighlightOp
    data class ClearAll(val documentId: String, val highlights: List<LeafHighlight>) : HighlightOp
}

class ReaderViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: DocumentRepository = container.repository
    private val settingsStore: SettingsStore = container.settingsStore

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var pdfSession: PdfSession? = null
    private var textSession: TextSession? = null
    private var activeDocumentId: String? = null
    private var viewportWidth = 0
    private var viewportHeight = 0

    private val undoStack = ArrayDeque<HighlightOp>()
    private val redoStack = ArrayDeque<HighlightOp>()

    private val bitmapCache = object : LinkedHashMap<Int, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean = size > 2
    }

    init {
        viewModelScope.launch {
            combine(
                settingsStore.tabs,
                settingsStore.activeTab,
                settingsStore.settings
            ) { tabs, active, settings -> Triple(tabs, active, settings) }
                .collect { (tabs, active, settings) ->
                    val target = active ?: tabs.lastOrNull()?.documentId
                    if (target != null && target != activeDocumentId) {
                        openDocument(target, settings)
                    } else if (target == null && activeDocumentId != null) {
                        closeSession()
                    }
                }
        }
    }

    // ------------------------------------------------------------- lifecycle

    private suspend fun openDocument(documentId: String, settings: com.leaf.reader.model.LeafSettings) {
        closeSession()
        val document = repository.documentById(documentId)
        if (document == null) {
            _state.value = _state.value.copy(
                loading = false,
                error = "This document is no longer available. Add it again from the Device tab."
            )
            return
        }
        val saved = repository.readerState(documentId)
        val page = if (settings.rememberLastPage) saved?.page ?: 0 else 0
        val zoom = saved?.zoom ?: settings.defaultZoom
        val direction = saved?.scrollDirection ?: settings.defaultScrollDirection
        val reflow = saved?.reflow ?: settings.defaultReflow
        val theme = saved?.theme ?: settings.readingTheme
        val orientation = saved?.orientation ?: settings.lockOrientation

        activeDocumentId = documentId
        _state.value = ReaderUiState(
            document = document,
            page = page,
            pageCount = document.pageCount.coerceAtLeast(1),
            zoom = zoom,
            scrollDirection = direction,
            reflow = reflow,
            theme = theme,
            orientation = orientation,
            loading = true,
            error = null
        )

        if (document.kind == com.leaf.reader.model.FileKind.PDF) {
            val session = withContext(Dispatchers.IO) {
                PdfSession(container.appContext, android.net.Uri.parse(document.uri))
            }
            pdfSession = session
            if (!session.isValid()) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "This PDF could not be opened. The file may be damaged or the app no longer has access to it."
                )
                return
            }
            _state.value = _state.value.copy(pageCount = session.pageCount)
            repository.setPageCount(documentId, session.pageCount)
            // The text index (used for in-page search and geometry highlights) is built on
            // demand: it is the most memory hungry part of reading a PDF, so Leaf only pays
            // for it when the reader actually searches inside the file.
        } else {
            val parsed = withContext(Dispatchers.IO) {
                com.leaf.reader.engine.TextExtractor.parseDocument(
                    container.appContext,
                    android.net.Uri.parse(document.uri),
                    document.kind
                )
            }
            val session = TextSession(
                document.kind,
                parsed,
                settings.typeface
            )
            textSession = session
            if (viewportWidth > 0 && viewportHeight > 0) {
                session.configure(viewportWidth, viewportHeight, textSizeFor(zoom))
                _state.value = _state.value.copy(pageCount = session.pageCount)
            }
        }

        val highlights = repository.highlightsFor(documentId)
        val bookmarks = repository.bookmarksFor(documentId)
        _state.value = _state.value.copy(
            highlights = highlights,
            bookmarks = bookmarks,
            loading = false
        )

        val pending = container.pendingFind
        if (pending != null && pending.first == documentId) {
            container.pendingFind = null
            setFindQuery(pending.second)
        }
    }

    private fun closeSession() {
        pdfSession?.close()
        pdfSession = null
        textSession = null
        activeDocumentId = null
        bitmapCache.clear()
    }

    fun configureViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        val session = textSession ?: return
        val zoom = _state.value.zoom
        session.configure(width, height, textSizeFor(zoom))
        _state.value = _state.value.copy(pageCount = session.pageCount)
        if (_state.value.page >= session.pageCount) {
            setPage((session.pageCount - 1).coerceAtLeast(0))
        }
    }

    private fun textSizeFor(zoom: Float): Float =
        TextSession.DEFAULT_TEXT_SIZE * zoom

    // ----------------------------------------------------------------- pages

    fun setPage(page: Int) {
        val state = _state.value
        val target = page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target == state.page) return
        _state.value = state.copy(page = target, selection = null)
        savePosition()
    }

    fun nextPage() = setPage(_state.value.page + 1)
    fun previousPage() = setPage(_state.value.page - 1)

    fun setZoom(zoom: Float) {
        val clamped = zoom.coerceIn(0.7f, 2.5f)
        _state.value = _state.value.copy(zoom = clamped)
        textSession?.let { session ->
            if (viewportWidth > 0 && viewportHeight > 0) {
                session.configure(viewportWidth, viewportHeight, textSizeFor(clamped))
                _state.value = _state.value.copy(pageCount = session.pageCount)
            }
        }
        savePosition()
    }

    fun toggleZoom() {
        val zoom = _state.value.zoom
        setZoom(if (zoom > 1.2f) 1f else 1.6f)
    }

    fun setScrollDirection(direction: ScrollDirection) {
        _state.value = _state.value.copy(scrollDirection = direction)
        savePosition()
    }

    fun toggleScrollDirection() {
        val next = if (_state.value.scrollDirection == ScrollDirection.VERTICAL) {
            ScrollDirection.HORIZONTAL
        } else {
            ScrollDirection.VERTICAL
        }
        setScrollDirection(next)
    }

    fun setReflow(enabled: Boolean) {
        _state.value = _state.value.copy(reflow = enabled)
        savePosition()
    }

    fun setTheme(theme: ReadingTheme) {
        _state.value = _state.value.copy(theme = theme)
        savePosition()
    }

    fun cycleTheme() {
        val themes = ReadingTheme.values()
        val next = themes[(themes.indexOf(_state.value.theme) + 1) % themes.size]
        setTheme(next)
    }

    fun setOrientation(orientation: OrientationLock) {
        _state.value = _state.value.copy(orientation = orientation)
        savePosition()
    }

    fun cycleOrientation() {
        val options = OrientationLock.values()
        val next = options[(options.indexOf(_state.value.orientation) + 1) % options.size]
        setOrientation(next)
    }

    fun setFullScreen(enabled: Boolean) {
        _state.value = _state.value.copy(fullScreen = enabled)
    }

    fun toggleFullScreen() = setFullScreen(!_state.value.fullScreen)

    fun setShowFind(show: Boolean) {
        _state.value = _state.value.copy(showFind = show)
        if (!show) {
            _state.value = _state.value.copy(findQuery = "", findMatches = emptyList(), findIndex = 0)
        }
    }

    fun setShowHighlights(show: Boolean) {
        _state.value = _state.value.copy(showHighlights = show)
    }

    fun setShowBookmarks(show: Boolean) {
        _state.value = _state.value.copy(showBookmarks = show)
    }

    fun setShowTabs(show: Boolean) {
        _state.value = _state.value.copy(showTabs = show)
    }

    fun setShowMore(show: Boolean) {
        _state.value = _state.value.copy(showMore = show)
    }

    fun setActiveHighlight(id: String?) {
        _state.value = _state.value.copy(activeHighlightId = id)
    }

    fun setLastColor(color: HighlightColor) {
        _state.value = _state.value.copy(lastColor = color)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ------------------------------------------------------------ highlights

    fun onSelectionChanged(start: Int, end: Int) {
        _state.value = _state.value.copy(selection = start until end)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = null, activeHighlightId = null)
    }

    fun addHighlight(color: HighlightColor) {
        val state = _state.value
        val document = state.document ?: return
        val selection = state.selection
        if (selection == null || selection.isEmpty()) {
            _state.value = state.copy(message = "Select some text first")
            return
        }
        val quote = sessionText().substring(
            selection.first.coerceIn(0, sessionText().length),
            selection.last.coerceIn(0, sessionText().length)
        )
        val highlight = LeafHighlight(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            color = color,
            page = state.page,
            rects = emptyList(),
            textStart = selection.first,
            textEnd = selection.last,
            quote = quote,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.addHighlight(highlight)
            pushUndo(HighlightOp.Add(highlight))
            _state.value = _state.value.copy(
                highlights = repository.highlightsFor(document.id),
                lastColor = color,
                selection = null
            )
        }
    }

    /** Adds a highlight from a rectangle drawn on a PDF page. */
    fun addPdfHighlight(rect: NormRect, quote: String, start: Int, end: Int) {
        val state = _state.value
        val document = state.document ?: return
        val highlight = LeafHighlight(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            color = state.lastColor,
            page = state.page,
            rects = listOf(rect),
            textStart = start,
            textEnd = end,
            quote = quote,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.addHighlight(highlight)
            pushUndo(HighlightOp.Add(highlight))
            _state.value = _state.value.copy(
                highlights = repository.highlightsFor(document.id),
                selection = null
            )
        }
    }

    fun recolorHighlight(id: String, color: HighlightColor) {
        val state = _state.value
        val existing = state.highlights.firstOrNull { it.id == id } ?: return
        val updated = existing.copy(color = color)
        viewModelScope.launch {
            repository.updateHighlight(updated)
            pushUndo(HighlightOp.Recolor(existing, updated))
            _state.value = _state.value.copy(
                highlights = repository.highlightsFor(existing.documentId),
                lastColor = color
            )
        }
    }

    fun removeHighlight(id: String) {
        val state = _state.value
        val existing = state.highlights.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            repository.removeHighlight(id)
            pushUndo(HighlightOp.Remove(existing))
            _state.value = _state.value.copy(
                highlights = repository.highlightsFor(existing.documentId),
                activeHighlightId = null
            )
        }
    }

    fun clearHighlights() {
        val state = _state.value
        val document = state.document ?: return
        viewModelScope.launch {
            val removed = repository.highlightsFor(document.id)
            repository.clearHighlights(document.id)
            pushUndo(HighlightOp.ClearAll(document.id, removed))
            _state.value = _state.value.copy(
                highlights = emptyList(),
                activeHighlightId = null
            )
        }
    }

    fun undo() {
        val op = undoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            when (op) {
                is HighlightOp.Add -> repository.removeHighlight(op.highlight.id)
                is HighlightOp.Remove -> repository.addHighlight(op.highlight)
                is HighlightOp.Recolor -> repository.updateHighlight(op.before)
                is HighlightOp.ClearAll -> op.highlights.forEach { repository.addHighlight(it) }
            }
            redoStack.addLast(op)
            refreshHighlights()
        }
    }

    fun redo() {
        val op = redoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            when (op) {
                is HighlightOp.Add -> repository.addHighlight(op.highlight)
                is HighlightOp.Remove -> repository.removeHighlight(op.highlight.id)
                is HighlightOp.Recolor -> repository.updateHighlight(op.after)
                is HighlightOp.ClearAll -> repository.clearHighlights(op.documentId)
            }
            undoStack.addLast(op)
            refreshHighlights()
        }
    }

    private fun pushUndo(op: HighlightOp) {
        undoStack.addLast(op)
        while (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _state.value = _state.value.copy(canUndo = undoStack.isNotEmpty(), canRedo = false)
    }

    private suspend fun refreshHighlights() {
        val document = _state.value.document ?: return
        _state.value = _state.value.copy(
            highlights = repository.highlightsFor(document.id),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    // ------------------------------------------------------------- bookmarks

    fun addBookmark() {
        val state = _state.value
        val document = state.document ?: return
        viewModelScope.launch {
            val bookmark = repository.addBookmark(document.id, state.page)
            _state.value = _state.value.copy(
                bookmarks = repository.bookmarksFor(document.id),
                message = "Bookmarked page ${state.page + 1}"
            )
            bookmark
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            repository.removeBookmark(id)
            val document = _state.value.document
            if (document != null) {
                _state.value = _state.value.copy(bookmarks = repository.bookmarksFor(document.id))
            }
        }
    }

    fun jumpToBookmark(bookmark: LeafBookmark) {
        setPage(bookmark.page)
    }

    // ------------------------------------------------------------------ find

    fun setFindQuery(query: String) {
        _state.value = _state.value.copy(findQuery = query, showFind = true)
        if (query.isBlank()) {
            _state.value = _state.value.copy(findMatches = emptyList(), findIndex = 0)
            return
        }
        viewModelScope.launch {
            val matches = computeMatches(query)
            _state.value = _state.value.copy(findMatches = matches, findIndex = 0)
            matches.firstOrNull()?.let { jumpToMatch(it) }
        }
    }

    fun nextMatch() {
        val state = _state.value
        if (state.findMatches.isEmpty()) return
        val next = (state.findIndex + 1) % state.findMatches.size
        _state.value = state.copy(findIndex = next)
        jumpToMatch(state.findMatches[next])
    }

    fun previousMatch() {
        val state = _state.value
        if (state.findMatches.isEmpty()) return
        val previous = (state.findIndex - 1 + state.findMatches.size) % state.findMatches.size
        _state.value = state.copy(findIndex = previous)
        jumpToMatch(state.findMatches[previous])
    }

    private fun jumpToMatch(match: FindMatch) {
        setPage(match.page)
    }

    private suspend fun computeMatches(query: String): List<FindMatch> = withContext(Dispatchers.IO) {
        val session = pdfSession
        if (session != null) {
            val index = session.textIndex()
            return@withContext index.findMatches(query).map {
                FindMatch(it.page, it.start, it.end, it.rects)
            }
        }
        val text = sessionText()
        if (text.isBlank()) return@withContext emptyList()
        val haystack = text.lowercase()
        val needle = query.lowercase()
        val out = mutableListOf<FindMatch>()
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            val page = textSession?.pageOfOffset(index) ?: 0
            out.add(FindMatch(page, index, index + query.length))
            index = haystack.indexOf(needle, index + needle.length)
        }
        out
    }

    // ------------------------------------------------------------- rendering

    fun sessionText(): String = textSession?.plainText ?: ""

    fun textLayout(): android.text.StaticLayout? = textSession?.layout()

    fun pageSlice(page: Int): com.leaf.reader.engine.PageSlice? = textSession?.slice(page)

    fun textSessionRef(): TextSession? = textSession

    fun pdfSessionRef(): PdfSession? = pdfSession

    suspend fun renderPdfPage(page: Int, width: Int): Bitmap? {
        val session = pdfSession ?: return null
        // The key carries the width bucket: the same page at the same zoom is reused, a zoom
        // change renders a sharper page.
        val key = page * 1000 + (width / 100)
        val cached = bitmapCache[key]
        if (cached != null) return cached
        val bitmap = withContext(Dispatchers.IO) { session.renderPage(page, width) } ?: return null
        bitmapCache[key] = bitmap
        return bitmap
    }

    fun pdfTextIndex(): com.leaf.reader.engine.PdfTextIndex? =
        pdfSession?.cachedTextIndex()

    /** Blocking render used when sharing a page image. */
    fun renderPdfPageBlocking(page: Int, width: Int): Bitmap? =
        pdfSession?.renderPage(page, width)

    // ------------------------------------------------------------- position

    fun savePosition() {
        val state = _state.value
        val document = state.document ?: return
        viewModelScope.launch {
            repository.saveReaderState(
                com.leaf.reader.model.ReaderState(
                    documentId = document.id,
                    page = state.page,
                    zoom = state.zoom,
                    scrollDirection = state.scrollDirection,
                    reflow = state.reflow,
                    theme = state.theme,
                    orientation = state.orientation,
                    findQuery = state.findQuery,
                    findIndex = state.findIndex
                )
            )
            repository.setProgress(document.id, state.page)
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePosition()
        closeSession()
    }
}
