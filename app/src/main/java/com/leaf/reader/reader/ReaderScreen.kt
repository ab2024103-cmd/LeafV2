package com.leaf.reader.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.WindowManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leaf.reader.AppContainer
import com.leaf.reader.model.FileKind
import com.leaf.reader.model.HighlightColor
import com.leaf.reader.model.LeafHighlight
import com.leaf.reader.model.OrientationLock
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.model.ScrollDirection
import com.leaf.reader.ui.LeafViewModelFactory
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTabs: () -> Unit = {}
) {
    val viewModel: ReaderViewModel = viewModel(factory = LeafViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsState(initial = com.leaf.reader.model.LeafSettings())
    val context = LocalContext.current
    val activity = context as? Activity
    val colors = readerColors(state.theme, androidx.compose.foundation.isSystemInDarkTheme())

    LaunchedEffect(state.orientation, settings.autoRotate) {
        val requested = when (state.orientation) {
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationLock.AUTO -> if (settings.autoRotate) {
                ActivityInfo.SCREEN_ORIENTATION_USER
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        activity?.requestedOrientation = requested
    }

    LaunchedEffect(settings.keepAwake) {
        if (settings.keepAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            kotlinx.coroutines.delay(2600)
            viewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.fullScreen) {
                ReaderTopBar(
                    state = state,
                    colors = colors,
                    onBack = onBack,
                    onFind = { viewModel.setShowFind(true) },
                    onBookmarks = { viewModel.setShowBookmarks(true) },
                    onHighlights = { viewModel.setShowHighlights(true) },
                    onTabs = { onOpenTabs() },
                    onMore = { viewModel.setShowMore(true) }
                )
            }

            if (state.showFind && !state.fullScreen) {
                FindBar(
                    query = state.findQuery,
                    index = state.findIndex,
                    total = state.findMatches.size,
                    colors = colors,
                    onQueryChange = { viewModel.setFindQuery(it) },
                    onNext = { viewModel.nextMatch() },
                    onPrevious = { viewModel.previousMatch() },
                    onClose = { viewModel.setShowFind(false) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    PageArea(
                        state = state,
                        viewModel = viewModel,
                        colors = colors
                    )
                }
            }

            if (state.selection != null && !state.fullScreen) {
                HighlightBar(
                    colors = colors,
                    lastColor = state.lastColor,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onColor = { viewModel.setLastColor(it); viewModel.addHighlight(it) },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onDone = { viewModel.clearSelection() }
                )
            }

            state.activeHighlightId?.let { activeId ->
                val highlight = state.highlights.firstOrNull { it.id == activeId }
                HighlightContextBar(
                    colors = colors,
                    onColor = { viewModel.recolorHighlight(activeId, it) },
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("Leaf highlight", highlight?.quote.orEmpty())
                        )
                    },
                    onRemove = { viewModel.removeHighlight(activeId) },
                    onDone = { viewModel.setActiveHighlight(null) }
                )
            }

            if (!state.fullScreen) {
                ReaderBottomBar(
                    state = state,
                    colors = colors,
                    onPrevious = { viewModel.previousPage() },
                    onNext = { viewModel.nextPage() },
                    onZoomIn = { viewModel.setZoom(state.zoom + 0.1f) },
                    onZoomOut = { viewModel.setZoom(state.zoom - 0.1f) },
                    onToggleScroll = { viewModel.toggleScrollDirection() },
                    onToggleFullScreen = { viewModel.toggleFullScreen() }
                )
            }
        }

        if (state.fullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.setFullScreen(false) }
            )
        }

        state.message?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (state.showHighlights) {
        HighlightsSheet(
            highlights = state.highlights,
            colors = colors,
            onDismiss = { viewModel.setShowHighlights(false) },
            onJump = { highlight ->
                viewModel.setPage(highlight.page)
                viewModel.setShowHighlights(false)
            },
            onDelete = { viewModel.removeHighlight(it.id) },
            onClear = { viewModel.clearHighlights() }
        )
    }

    if (state.showBookmarks) {
        BookmarksSheet(
            bookmarks = state.bookmarks,
            colors = colors,
            onDismiss = { viewModel.setShowBookmarks(false) },
            onJump = {
                viewModel.jumpToBookmark(it)
                viewModel.setShowBookmarks(false)
            },
            onDelete = { viewModel.removeBookmark(it.id) },
            onAdd = {
                viewModel.addBookmark()
                viewModel.setShowBookmarks(false)
            }
        )
    }

    if (state.showMore) {
        ReaderMoreSheet(
            state = state,
            colors = colors,
            onDismiss = { viewModel.setShowMore(false) },
            onTheme = { viewModel.setTheme(it) },
            onReflow = { viewModel.setReflow(it) },
            onOrientation = { viewModel.cycleOrientation() },
            onRotate = { viewModel.cycleOrientation() },
            onCopyPath = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("Leaf path", state.document?.uri ?: "")
                )
                viewModel.setShowMore(false)
            },
            onCopyPageText = {
                val text = viewModel.sessionText()
                val slice = viewModel.pageSlice(state.page)
                val pageText = if (slice != null) {
                    text.substring(
                        slice.startOffset.coerceIn(0, text.length),
                        slice.endOffset.coerceIn(0, text.length)
                    )
                } else {
                    text
                }
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Leaf page", pageText))
                viewModel.setShowMore(false)
            },
            onShareDocument = { shareDocument(context, state) },
            onSharePage = { sharePageImage(context, state, viewModel) }
        )
    }
}

@Composable
private fun PageArea(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    colors: ReaderColors
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt()
        val heightPx = with(density) { maxHeight.toPx() }.toInt()
        LaunchedEffect(widthPx, heightPx) {
            viewModel.configureViewport(widthPx, heightPx)
        }

        val document = state.document
        if (document == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No document", color = colors.text)
            }
            return@BoxWithConstraints
        }

        if (document.kind == FileKind.PDF) {
            PdfPageList(
                state = state,
                viewModel = viewModel,
                colors = colors,
                widthPx = widthPx,
                vertical = state.scrollDirection == ScrollDirection.VERTICAL
            )
        } else {
            TextPageList(
                state = state,
                viewModel = viewModel,
                colors = colors,
                heightPx = heightPx,
                vertical = state.scrollDirection == ScrollDirection.VERTICAL
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfPageList(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    colors: ReaderColors,
    widthPx: Int,
    vertical: Boolean
) {
    val listState = rememberLazyListState()
    val pageCount = state.pageCount.coerceAtLeast(1)

    LaunchedEffect(listState, vertical) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.setPage(it) }
    }
    LaunchedEffect(state.page, vertical) {
        if (listState.firstVisibleItemIndex != state.page) {
            listState.scrollToItem(state.page.coerceIn(0, pageCount - 1))
        }
    }

    if (vertical) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pageCount, key = { it }) { page ->
                PdfPageItem(page, state, viewModel, colors, widthPx)
            }
        }
    } else {
        LazyRow(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pageCount, key = { it }) { page ->
                PdfPageItem(page, state, viewModel, colors, widthPx)
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    page: Int,
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    colors: ReaderColors,
    widthPx: Int
) {
    val renderWidth = (widthPx * state.zoom).toInt().coerceIn(64, 4096)
    val bitmap by produceState<Bitmap?>(null, page, renderWidth) {
        value = viewModel.renderPdfPage(page, renderWidth)
    }
    val rendered = bitmap
    val aspect = remember(rendered) {
        if (rendered != null && rendered.height > 0) {
            rendered.height.toFloat() / rendered.width
        } else {
            1.414f
        }
    }
    val density = LocalDensity.current
    val itemHeight = with(density) { (widthPx * aspect).toDp() }

    val pageHighlights = state.highlights.filter { it.page == page && it.rects.isNotEmpty() }
    val flatRects = remember(pageHighlights) {
        pageHighlights.flatMap { highlight -> highlight.rects.map { PdfHighlightRect(it, highlight.color) } }
    }
    val flatIds = remember(pageHighlights) {
        pageHighlights.flatMap { highlight -> highlight.rects.map { highlight.id } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .background(colors.background)
    ) {
        AndroidView(
            factory = { ctx ->
                LeafPdfView(ctx).apply {
                    onSelection = { rect ->
                        val index = viewModel.pdfTextIndex()?.pageOf(page)
                        if (index != null) {
                            val text = viewModel.pdfTextIndex()?.textInRect(index, rect).orEmpty()
                            val range = viewModel.pdfTextIndex()?.offsetOfRect(index, rect)
                            viewModel.addPdfHighlight(
                                rect = rect,
                                quote = text,
                                start = range?.first ?: 0,
                                end = (range?.last ?: 0) + 1
                            )
                        } else {
                            viewModel.addPdfHighlight(rect, "", 0, 0)
                        }
                    }
                    onTap = { x, y ->
                        if (x < 0f) {
                            viewModel.setActiveHighlight(flatIds.getOrNull(y.toInt()))
                        } else {
                            viewModel.setActiveHighlight(null)
                        }
                    }
                    onDoubleTap = { viewModel.toggleZoom() }
                    onScaleChanged = { factor -> viewModel.setZoom(state.zoom * factor) }
                }
            },
            update = { view ->
                view.colors = colors
                view.bitmap = bitmap
                view.highlightRects = flatRects
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextPageList(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    colors: ReaderColors,
    heightPx: Int,
    vertical: Boolean
) {
    val listState = rememberLazyListState()
    val pageCount = state.pageCount.coerceAtLeast(1)
    val density = LocalDensity.current
    val pageHeight = with(density) { heightPx.toDp() }

    LaunchedEffect(listState, vertical) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.setPage(it) }
    }
    LaunchedEffect(state.page, vertical) {
        if (listState.firstVisibleItemIndex != state.page) {
            listState.scrollToItem(state.page.coerceIn(0, pageCount - 1))
        }
    }

    val layout = viewModel.textLayout()
    val highlightIds = state.highlights.map { it.id }
    val highlightRanges = state.highlights.map { TextHighlightRange(it.textStart, it.textEnd, it.color) }

    if (state.reflow) {
        // Reflow: the whole text in one continuous column (spec 3.17).
        val reflowState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(reflowState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    LeafTextView(ctx).apply {
                        onSelectionChanged = { start, end -> viewModel.onSelectionChanged(start, end) }
                        onHighlightTap = { index -> viewModel.setActiveHighlight(highlightIds.getOrNull(index)) }
                        onTap = { viewModel.setActiveHighlight(null) }
                        onDoubleTap = { viewModel.toggleZoom() }
                        onScaleChanged = { factor -> viewModel.setZoom(state.zoom * factor) }
                    }
                },
                update = { view ->
                    view.colors = colors
                    view.textLayout = layout
                    view.pageTop = 0
                    view.highlightRanges = highlightRanges
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((layout?.height ?: heightPx).toFloat() / density.density).dp)
            )
        }
        return
    }

    if (vertical) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pageCount, key = { it }) { page ->
                val slice = viewModel.pageSlice(page)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight)
                        .background(colors.background)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            LeafTextView(ctx).apply {
                                onSelectionChanged = { start, end -> viewModel.onSelectionChanged(start, end) }
                                onHighlightTap = { index -> viewModel.setActiveHighlight(highlightIds.getOrNull(index)) }
                                onTap = { viewModel.setActiveHighlight(null) }
                                onDoubleTap = { viewModel.toggleZoom() }
                                onScaleChanged = { factor -> viewModel.setZoom(state.zoom * factor) }
                            }
                        },
                        update = { view ->
                            view.colors = colors
                            view.textLayout = layout
                            view.pageTop = slice?.topPx ?: 0
                            view.highlightRanges = highlightRanges
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        LazyRow(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pageCount, key = { it }) { page ->
                val slice = viewModel.pageSlice(page)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight)
                        .background(colors.background)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            LeafTextView(ctx).apply {
                                onSelectionChanged = { start, end -> viewModel.onSelectionChanged(start, end) }
                                onHighlightTap = { index -> viewModel.setActiveHighlight(highlightIds.getOrNull(index)) }
                                onTap = { viewModel.setActiveHighlight(null) }
                                onDoubleTap = { viewModel.toggleZoom() }
                                onScaleChanged = { factor -> viewModel.setZoom(state.zoom * factor) }
                            }
                        },
                        update = { view ->
                            view.colors = colors
                            view.textLayout = layout
                            view.pageTop = slice?.topPx ?: 0
                            view.highlightRanges = highlightRanges
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    colors: ReaderColors,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onBookmarks: () -> Unit,
    onHighlights: () -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit
) {
    Surface(color = colors.toolbar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to library")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.document?.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    color = colors.text
                )
                Text(
                    text = "Page ${state.page + 1} of ${state.pageCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onFind) {
                Icon(Icons.Outlined.Search, contentDescription = "Find in document", tint = colors.text)
            }
            IconButton(onClick = onBookmarks) {
                Icon(
                    if (state.bookmarks.any { it.page == state.page }) Icons.Filled.Bookmark
                    else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmarks",
                    tint = colors.text
                )
            }
            IconButton(onClick = onHighlights) {
                Icon(Icons.Outlined.FormatColorFill, contentDescription = "Highlights", tint = colors.text)
            }
            IconButton(onClick = onTabs) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Open documents", tint = colors.text)
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = colors.text)
            }
        }
    }
}

@Composable
private fun FindBar(
    query: String,
    index: Int,
    total: Int,
    colors: ReaderColors,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = colors.toolbar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Find in document") }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (total == 0) "0 of 0" else "${index + 1} of $total",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = "Previous match", tint = colors.text)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "Next match", tint = colors.text)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Search, contentDescription = "Close find", tint = colors.text)
            }
        }
    }
}

@Composable
private fun HighlightBar(
    colors: ReaderColors,
    lastColor: HighlightColor,
    canUndo: Boolean,
    canRedo: Boolean,
    onColor: (HighlightColor) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    Surface(color = colors.toolbar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.values().forEach { color ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .padding(end = 4.dp)
                        .clickable { onColor(color) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (color == lastColor) 24.dp else 18.dp)
                            .background(colors.tint(color), RoundedCornerShape(50))
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "Undo highlight")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "Redo highlight")
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun HighlightContextBar(
    colors: ReaderColors,
    onColor: (HighlightColor) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    Surface(color = colors.toolbar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.values().forEach { color ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .padding(end = 4.dp)
                        .clickable { onColor(color) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(colors.tint(color), RoundedCornerShape(50))
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCopy) { Text("Copy") }
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    colors: ReaderColors,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleScroll: () -> Unit,
    onToggleFullScreen: () -> Unit
) {
    Surface(color = colors.toolbar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious) { Text("Prev") }
            Text(
                text = "${(state.zoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text
            )
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out", tint = colors.text)
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in", tint = colors.text)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleScroll) {
                Icon(Icons.Outlined.SwapVert, contentDescription = "Switch scroll direction", tint = colors.text)
            }
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    if (state.fullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = "Toggle full screen",
                    tint = colors.text
                )
            }
            TextButton(onClick = onNext) { Text("Next") }
        }
    }
}

private fun shareDocument(context: android.content.Context, state: ReaderUiState) {
    val document = state.document ?: return
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = document.kind.mime
        putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(document.uri))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share ${document.name}"))
}

private fun sharePageImage(context: android.content.Context, state: ReaderUiState, viewModel: ReaderViewModel) {
    val document = state.document ?: return
    val bitmap: Bitmap? = if (document.kind == FileKind.PDF) {
        viewModel.renderPdfPageBlocking(state.page, 1400)
    } else {
        null
    }
    if (bitmap == null) {
        viewModel.setShowMore(false)
        return
    }
    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val file = java.io.File(dir, "leaf-page-${state.page + 1}.png")
    java.io.FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share page ${state.page + 1}"))
    viewModel.setShowMore(false)
}
