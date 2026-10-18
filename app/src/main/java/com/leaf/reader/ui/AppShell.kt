package com.leaf.reader.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leaf.reader.AppContainer
import com.leaf.reader.core.DeviceFile
import com.leaf.reader.core.DeviceScanner
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.reader.ReaderScreen
import com.leaf.reader.ui.components.ConfirmDialog
import com.leaf.reader.ui.device.DeviceScreen
import com.leaf.reader.ui.components.DocumentActionsSheet
import com.leaf.reader.ui.components.FolderManagerSheet
import com.leaf.reader.ui.components.MoveToFolderSheet
import com.leaf.reader.ui.components.OpenDocumentsSheet
import com.leaf.reader.ui.components.TagManagerSheet
import com.leaf.reader.ui.components.TagPickerSheet
import com.leaf.reader.ui.components.TextInputDialog
import com.leaf.reader.ui.favorites.FavoritesScreen
import com.leaf.reader.ui.library.LibraryScreen
import com.leaf.reader.ui.recents.RecentsScreen
import com.leaf.reader.ui.search.SearchScreen
import com.leaf.reader.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val LIBRARY = "library"
    const val RECENTS = "recents"
    const val FAVORITES = "favorites"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val GROUP = "group"
    const val DEVICE = "device"
    const val READER = "reader"
}

val DocumentMimeTypes = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/epub+zip",
    "text/plain",
    "application/octet-stream",
    "*/*"
)

@Composable
fun LeafApp(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel: LibraryViewModel = viewModel(factory = LeafViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickMode by container.pickMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var actionDocument by remember { mutableStateOf<LeafDocument?>(null) }
    var moveDocument by remember { mutableStateOf<LeafDocument?>(null) }
    var tagDocument by remember { mutableStateOf<LeafDocument?>(null) }
    var deleteDocument by remember { mutableStateOf<LeafDocument?>(null) }
    var renameDocument by remember { mutableStateOf<LeafDocument?>(null) }
    var folderManagerOpen by remember { mutableStateOf(false) }
    var tagManagerOpen by remember { mutableStateOf(false) }
    var tabsSheetOpen by remember { mutableStateOf(false) }
    var tabsHintShown by rememberSaveable { mutableStateOf(false) }
    var deviceFiles by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var deviceScanning by remember { mutableStateOf(false) }
    var deviceMessage by remember { mutableStateOf<String?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inReader = currentRoute == Routes.READER

    fun runDeviceScan(job: suspend () -> List<DeviceFile>) {
        scope.launch {
            deviceScanning = true
            deviceMessage = null
            val found = runCatching { job() }.getOrElse { emptyList() }
            deviceFiles = found
            deviceScanning = false
            deviceMessage = if (found.isEmpty()) {
                "No supported documents found yet"
            } else {
                "${found.size} document${if (found.size == 1) "" else "s"} found on this device"
            }
        }
    }

    /** Folder browsing through the system picker — no runtime permission is ever requested. */
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        deviceMessage = "Reading the folder…"
        runDeviceScan { DeviceScanner.scanTree(context, uri) }
    }

    /** Full device scan — on Android 6-9 this asks for storage access once, on purpose. */
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runDeviceScan { DeviceScanner.scanStorage(context) }
        } else {
            deviceMessage = "Storage access declined — use \"Choose folder\" instead, it never asks."
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uris.first(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addDocuments(uris)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is LibraryEvent.OpenReader -> {
                    if (!inReader) navController.navigate(Routes.READER) { launchSingleTop = true }
                }
                is LibraryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is LibraryEvent.OpenGroup -> navController.navigate("${Routes.GROUP}/${event.kind}")
            }
        }
    }

    val destinations = listOf(
        Triple(Routes.LIBRARY, Icons.Outlined.LibraryBooks, "Library"),
        Triple(Routes.RECENTS, Icons.Outlined.History, "Recents"),
        Triple(Routes.FAVORITES, Icons.Outlined.Favorite, "Favorites"),
        Triple(Routes.SEARCH, Icons.Outlined.Search, "Search"),
        Triple(Routes.DEVICE, Icons.Outlined.Folder, "Device"),
        Triple(Routes.SETTINGS, Icons.Outlined.Settings, "Settings")
    )

    val recentBadge = state.recents.count {
        System.currentTimeMillis() - it.openedAt < 24 * 60 * 60 * 1000L
    }

    fun navigateTo(route: String) {
        if (route == Routes.LIBRARY && currentRoute?.startsWith(Routes.GROUP) == true) {
            navController.popBackStack(Routes.LIBRARY, inclusive = false)
        } else {
            navController.navigate(route) {
                popUpTo(Routes.LIBRARY) { inclusive = false; saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!inReader && !wide) {
                    NavigationBar {
                        destinations.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                selected = currentRoute == route ||
                                    (route == Routes.LIBRARY && currentRoute?.startsWith(Routes.GROUP) == true),
                                onClick = { navigateTo(route) },
                                icon = {
                                    if (route == Routes.RECENTS && recentBadge > 0) {
                                        BadgedBox(badge = { Badge { Text("$recentBadge") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (!inReader && wide) {
                    NavigationRail {
                        destinations.forEach { (route, icon, label) ->
                            NavigationRailItem(
                                selected = currentRoute == route ||
                                    (route == Routes.LIBRARY && currentRoute?.startsWith(Routes.GROUP) == true),
                                onClick = { navigateTo(route) },
                                icon = {
                                    if (route == Routes.RECENTS && recentBadge > 0) {
                                        BadgedBox(badge = { Badge { Text("$recentBadge") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.LIBRARY,
                    modifier = if (inReader) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    }
                ) {
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            state = state,
                            onOpen = { viewModel.openDocument(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDocumentActions = { actionDocument = it },
                            onAddDocuments = { picker.launch(DocumentMimeTypes) },
                            onOpenTabs = { tabsSheetOpen = true },
                            onOpenGroup = { kind -> navController.navigate("${Routes.GROUP}/$kind") },
                            onToggleGroupMode = { viewModel.setGroupByType(it) },
                            onSort = { field, direction -> viewModel.setLibrarySort(field, direction) },
                            onManageFolders = { folderManagerOpen = true },
                            onOpenDevice = { navController.navigate(Routes.DEVICE) },
                            onManageTags = { tagManagerOpen = true },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            pickMode = pickMode,
                            onPickDocument = { viewModel.openDocumentInNewTab(it) },
                            onCancelPick = { container.pickMode.value = false }
                        )
                    }
                    composable(Routes.RECENTS) {
                        RecentsScreen(
                            state = state,
                            onOpen = { viewModel.openDocument(it) },
                            onRemove = { documentId ->
                                viewModel.removeRecent(documentId)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed from recents",
                                        actionLabel = "Undo"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreRecent(documentId)
                                    }
                                }
                            },
                            onClearAll = { viewModel.clearRecents() },
                            onClearToday = { viewModel.clearTodayRecents() },
                            onDocumentActions = { actionDocument = it }
                        )
                    }
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(
                            state = state,
                            onOpen = { viewModel.openDocument(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDocumentActions = { actionDocument = it },
                            onSort = { field, direction -> viewModel.setFavoritesSort(field, direction) }
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            container = container,
                            state = state,
                            onOpen = { document, page, query ->
                                container.pendingFind = document.id to query
                                viewModel.openDocument(document)
                            },
                            onDocumentActions = { actionDocument = it },
                            onRecordSearch = { viewModel.recordSearch(it) },
                            onRemoveHistory = { viewModel.removeSearchHistory(it) },
                            onClearHistory = { viewModel.clearSearchHistory() }
                        )
                    }
                    composable(Routes.DEVICE) {
                        DeviceScreen(
                            files = deviceFiles,
                            scanning = deviceScanning,
                            message = deviceMessage,
                            addedUris = state.documents.map { it.uri }.toSet(),
                            onBrowseFolder = { treePicker.launch(null) },
                            onScanStorage = {
                                if (DeviceScanner.canScanWithoutPermission()) {
                                    runDeviceScan { DeviceScanner.scanMediaStore(context) }
                                } else {
                                    storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            onAdd = { file -> viewModel.addDeviceFile(file) },
                            onOpen = { file -> viewModel.addAndOpenDeviceFile(file) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            container = container,
                            state = state,
                            onOpenAbout = { navController.navigate(Routes.ABOUT) }
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(state = state)
                    }
                    composable("${Routes.GROUP}/{kind}") { entry ->
                        GroupScreen(
                            state = state,
                            kind = entry.arguments?.getString("kind").orEmpty(),
                            onBack = { navController.popBackStack() },
                            onOpen = { viewModel.openDocument(it) },
                            onDocumentActions = { actionDocument = it },
                            onSort = { field, direction -> viewModel.setLibrarySort(field, direction) }
                        )
                    }
                    composable(Routes.READER) {
                        ReaderScreen(
                            container = container,
                            onBack = {
                                navController.popBackStack()
                                if (!tabsHintShown) {
                                    tabsHintShown = true
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Your open documents are still here — use the tabs button to switch."
                                        )
                                    }
                                }
                            },
                            onOpenTabs = { tabsSheetOpen = true }
                        )
                    }
                }
            }
        }
    }

    if (tabsSheetOpen) {
        OpenDocumentsSheet(
            tabs = state.tabs,
            activeId = state.activeTabId,
            documents = state.documents,
            onSelect = { id ->
                viewModel.focusTab(id)
                tabsSheetOpen = false
                if (!inReader) navController.navigate(Routes.READER) { launchSingleTop = true }
            },
            onClose = { id -> viewModel.closeTab(id) },
            onCloseAll = { viewModel.closeAllTabs() },
            onNewTab = {
                tabsSheetOpen = false
                container.pickMode.value = true
                if (!inReader) navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
            },
            onDismiss = { tabsSheetOpen = false }
        )
    }

    actionDocument?.let { document ->
        DocumentActionsSheet(
            document = document,
            folderName = state.folderName(document.folderId),
            tagNames = state.tagsFor(document.id).map { it.name },
            onDismiss = { actionDocument = null },
            onOpen = { viewModel.openDocument(document) },
            onRename = { renameDocument = document },
            onDelete = { deleteDocument = document },
            onMove = { moveDocument = document },
            onTags = { tagDocument = document },
            onToggleFavorite = { viewModel.toggleFavorite(document) },
            onInfo = { scope.launch { snackbarHostState.showSnackbar(fileInfoText(state, document)) } },
            onShare = { shareDocument(context, document) },
            onCopyPath = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Leaf path", document.uri))
                scope.launch { snackbarHostState.showSnackbar("Path copied") }
            }
        )
    }

    moveDocument?.let { document ->
        MoveToFolderSheet(
            folders = state.folders,
            currentFolderId = document.folderId,
            onMove = { folderId -> viewModel.moveToFolder(document, folderId) },
            onDismiss = { moveDocument = null }
        )
    }

    tagDocument?.let { document ->
        TagPickerSheet(
            tags = state.tags,
            selected = state.tagsFor(document.id).map { it.id }.toSet(),
            onApply = { selected -> viewModel.setTags(document, selected) },
            onManage = { tagManagerOpen = true },
            onDismiss = { tagDocument = null }
        )
    }

    if (folderManagerOpen) {
        FolderManagerSheet(
            folders = state.folders,
            documentCounts = state.documents
                .mapNotNull { doc -> doc.folderId }
                .groupingBy { it }
                .eachCount(),
            childCounts = state.folders
                .mapNotNull { folder -> folder.parentId }
                .groupingBy { it }
                .eachCount(),
            onCreate = { name, parent -> viewModel.createFolder(name, parent, 0) },
            onMove = { folder, parent -> viewModel.moveFolder(folder, parent) },
            onRename = { folder, name, color -> viewModel.renameFolder(folder, name, color) },
            onDelete = { folder -> viewModel.deleteFolder(folder) },
            onDismiss = { folderManagerOpen = false }
        )
    }

    if (tagManagerOpen) {
        TagManagerSheet(
            tags = state.tags,
            usage = state.documents.map { doc -> doc.id to state.tagsFor(doc.id).size }.toMap(),
            onCreate = { name, color -> viewModel.createTag(name, color) },
            onRename = { tag, name, color -> viewModel.renameTag(tag, name, color) },
            onDelete = { tag -> viewModel.deleteTag(tag) },
            onDismiss = { tagManagerOpen = false }
        )
    }

    renameDocument?.let { document ->
        TextInputDialog(
            title = "Rename document",
            label = "New name",
            initial = document.name,
            onConfirm = { name -> viewModel.renameDocument(document, name) },
            onDismiss = { renameDocument = null }
        )
    }

    deleteDocument?.let { document ->
        ConfirmDialog(
            title = "Delete ${document.name}?",
            message = "The document is removed from your library. Undo is offered right after.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteDocument(document) },
            onDismiss = { deleteDocument = null }
        )
    }
}

private fun fileInfoText(state: LibraryState, document: LeafDocument): String {
    val highlights = state.highlightsFor(document.id).size
    val bookmarks = state.bookmarksFor(document.id).size
    return "${document.kind.label} · ${com.leaf.reader.core.Formatters.fileSize(document.sizeBytes)} · " +
        "${document.pageCount} pages · $highlights highlights · $bookmarks bookmarks"
}

private fun shareDocument(context: android.content.Context, document: LeafDocument) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = document.kind.mime
        putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(document.uri))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share ${document.name}"))
}
