package com.leaf.reader.data

import android.content.Context
import android.net.Uri
import com.leaf.reader.core.FileUtils
import com.leaf.reader.core.Formatters
import com.leaf.reader.data.db.BookmarkDao
import com.leaf.reader.data.db.BookmarkEntity
import com.leaf.reader.data.db.CollectionDao
import com.leaf.reader.data.db.CollectionEntity
import com.leaf.reader.data.db.DocumentDao
import com.leaf.reader.data.db.DocumentEntity
import com.leaf.reader.data.db.DocumentTagCrossRef
import com.leaf.reader.data.db.FolderDao
import com.leaf.reader.data.db.FolderEntity
import com.leaf.reader.data.db.LeafDatabase
import com.leaf.reader.data.db.HighlightDao
import com.leaf.reader.data.db.HighlightEntity
import com.leaf.reader.data.db.RecentDao
import com.leaf.reader.data.db.RecentEntity
import com.leaf.reader.data.db.ReaderStateDao
import com.leaf.reader.data.db.ReaderStateEntity
import com.leaf.reader.data.db.TagDao
import com.leaf.reader.data.db.TagEntity
import com.leaf.reader.data.db.TextCacheDao
import com.leaf.reader.data.db.TextCacheEntity
import com.leaf.reader.model.FileKind
import com.leaf.reader.model.LeafBookmark
import com.leaf.reader.model.LeafCollection
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.LeafFolder
import com.leaf.reader.model.LeafHighlight
import com.leaf.reader.model.LeafTag
import com.leaf.reader.model.NormRect
import com.leaf.reader.model.OpenTab
import com.leaf.reader.model.ReaderState
import com.leaf.reader.model.RecentEntry
import com.leaf.reader.model.HighlightColor
import com.leaf.reader.model.ScrollDirection
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.model.OrientationLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Single source of truth for documents, organisation and annotations. */
class DocumentRepository(
    private val context: Context,
    private val db: LeafDatabase
) {
    private val documentDao: DocumentDao = db.documentDao()
    private val folderDao: FolderDao = db.folderDao()
    private val tagDao: TagDao = db.tagDao()
    private val collectionDao: CollectionDao = db.collectionDao()
    private val recentDao: RecentDao = db.recentDao()
    private val bookmarkDao: BookmarkDao = db.bookmarkDao()
    private val highlightDao: HighlightDao = db.highlightDao()
    private val readerStateDao: ReaderStateDao = db.readerStateDao()
    private val textCacheDao: TextCacheDao = db.textCacheDao()

    // ------------------------------------------------------------------ reads

    val documents: Flow<List<LeafDocument>> =
        documentDao.observeAll().map { list -> list.map { it.toModel() } }

    val folders: Flow<List<LeafFolder>> =
        folderDao.observeAll().map { list -> list.map { it.toModel() } }

    val tags: Flow<List<LeafTag>> =
        tagDao.observeAll().map { list -> list.map { it.toModel() } }

    val collections: Flow<List<LeafCollection>> =
        collectionDao.observeAll().map { list -> list.map { it.toModel() } }

    val recents: Flow<List<RecentEntry>> =
        combine(recentDao.observeAll(), documents) { recents, docs ->
            val byId = docs.associateBy { it.id }
            recents.sortedByDescending { it.openedAt }.map {
                RecentEntry(it.documentId, it.openedAt, byId[it.documentId])
            }
        }

    val highlights: Flow<List<LeafHighlight>> =
        highlightDao.observeAll().map { list -> list.map { it.toModel() } }

    val bookmarks: Flow<List<LeafBookmark>> =
        bookmarkDao.observeAll().map { list -> list.map { it.toModel() } }

    val documentTags: Flow<Map<String, List<LeafTag>>> =
        combine(tagDao.observeLinks(), tags) { links, allTags ->
            val byId = allTags.associateBy { it.id }
            links.groupBy { it.documentId }
                .mapValues { (_, refs) -> refs.mapNotNull { byId[it.tagId] } }
        }

    suspend fun documentById(id: String): LeafDocument? = documentDao.byId(id)?.toModel()

    suspend fun documentByUri(uri: String): LeafDocument? = documentDao.byUri(uri)?.toModel()

    suspend fun tagsFor(documentId: String): List<LeafTag> {
        val refs = tagDao.linksFor(documentId)
        return refs.mapNotNull { tagDao.byId(it.tagId)?.toModel() }
    }

    suspend fun highlightsFor(documentId: String): List<LeafHighlight> =
        highlightDao.forDocument(documentId).map { it.toModel() }

    suspend fun bookmarksFor(documentId: String): List<LeafBookmark> =
        bookmarkDao.forDocument(documentId).map { it.toModel() }

    suspend fun readerState(documentId: String): ReaderState? =
        readerStateDao.byId(documentId)?.toModel()

    suspend fun cachedText(documentId: String): TextCacheEntity? = textCacheDao.byId(documentId)

    suspend fun storeCachedText(documentId: String, text: String, pageOffsets: List<Int>) {
        textCacheDao.upsert(
            TextCacheEntity(
                documentId = documentId,
                text = text,
                pageOffsets = pageOffsets.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Extracted text for a document, using the cache when possible (spec 10.8). */
    suspend fun ensureText(document: LeafDocument): com.leaf.reader.engine.ExtractedText {
        val cached = textCacheDao.byId(document.id)
        if (cached != null) {
            val offsets = cached.pageOffsets.split(",").mapNotNull { it.toIntOrNull() }
            return com.leaf.reader.engine.ExtractedText(cached.text, offsets)
        }
        val extracted = com.leaf.reader.engine.TextExtractor.extract(
            context,
            Uri.parse(document.uri),
            document.kind
        )
        storeCachedText(document.id, extracted.text, extracted.pageOffsets)
        return extracted
    }

    // ----------------------------------------------------------------- writes

    /** Imports a document the user picked through the system picker. */
    suspend fun importDocument(uri: Uri): LeafDocument? {
        val meta = FileUtils.queryMeta(context, uri)
        val kind = FileKind.fromName(meta.name) ?: FileKind.fromMime(meta.mimeType) ?: return null
        val uriString = uri.toString()
        val existing = documentDao.byUri(uriString)
        val now = System.currentTimeMillis()
        val entity = existing?.copy(
            displayName = meta.name,
            kind = kind.key,
            sizeBytes = meta.sizeBytes
        ) ?: DocumentEntity(
            id = UUID.randomUUID().toString(),
            uri = uriString,
            displayName = meta.name,
            kind = kind.key,
            folderId = null,
            sizeBytes = meta.sizeBytes,
            dateAdded = now,
            lastOpened = null,
            pageCount = 0,
            currentPage = 0,
            isFavorite = false,
            favAt = null,
            colorSeed = (meta.name.hashCode() and 0x0FFFFFFF)
        )
        documentDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun importDocuments(uris: List<Uri>): List<LeafDocument> =
        uris.mapNotNull { importDocument(it) }

    /** Adds a document that already lives in app storage (demo library). */
    suspend fun addLocalDocument(
        uri: Uri,
        name: String,
        kind: FileKind,
        sizeBytes: Long,
        dateAdded: Long
    ): LeafDocument {
        val entity = DocumentEntity(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            displayName = name,
            kind = kind.key,
            folderId = null,
            sizeBytes = sizeBytes,
            dateAdded = dateAdded,
            lastOpened = null,
            pageCount = 0,
            currentPage = 0,
            isFavorite = false,
            favAt = null,
            colorSeed = (name.hashCode() and 0x0FFFFFFF)
        )
        documentDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun renameDocument(doc: LeafDocument, newName: String): Boolean {
        val uri = Uri.parse(doc.uri)
        val ok = FileUtils.rename(context, uri, newName)
        documentDao.setName(doc.id, newName)
        return ok
    }

    /** Removes a document from the library (and the file when the provider allows it). */
    suspend fun removeDocument(doc: LeafDocument): Boolean {
        val uri = Uri.parse(doc.uri)
        val fileDeleted = if (FileUtils.isAppOwned(uri)) {
            runCatching { java.io.File(uri.path ?: "").delete() }.getOrDefault(false)
        } else {
            FileUtils.delete(context, uri)
        }
        documentDao.deleteById(doc.id)
        recentDao.delete(doc.id)
        tagDao.unlinkAll(doc.id)
        highlightDao.deleteForDocument(doc.id)
        readerStateDao.deleteById(doc.id)
        return fileDeleted
    }

    suspend fun moveToFolder(doc: LeafDocument, folderId: String?) {
        documentDao.setFolder(doc.id, folderId)
    }

    suspend fun setFavorite(doc: LeafDocument, favorite: Boolean) {
        documentDao.setFavorite(doc.id, favorite, if (favorite) System.currentTimeMillis() else null)
    }

    suspend fun setPageCount(docId: String, count: Int) = documentDao.setPageCount(docId, count)

    suspend fun setProgress(docId: String, page: Int) =
        documentDao.setProgress(docId, page, System.currentTimeMillis())

    /** Logs an open: newest first, never duplicated (spec 2.1 / 2.2). */
    suspend fun logOpened(docId: String) {
        val now = System.currentTimeMillis()
        recentDao.upsert(RecentEntity(docId, now))
        documentDao.setProgress(docId, documentDao.byId(docId)?.currentPage ?: 0, now)
    }

    suspend fun removeRecent(documentId: String) = recentDao.delete(documentId)

    /** Restores a recent entry (undo). */
    suspend fun restoreRecent(documentId: String) {
        val existing = recentDao.all().firstOrNull { it.documentId == documentId }
        val openedAt = existing?.openedAt ?: System.currentTimeMillis()
        recentDao.upsert(RecentEntity(documentId, openedAt))
    }

    suspend fun clearRecents() = recentDao.clearAll()

    suspend fun clearTodayRecents() = recentDao.clearBefore(Formatters.startOfToday())

    // ----------------------------------------------------------------- folders

    suspend fun createFolder(name: String, parentId: String?, colorIndex: Int): LeafFolder {
        val entity = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            parentId = parentId,
            colorIndex = colorIndex,
            createdAt = System.currentTimeMillis()
        )
        folderDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun renameFolder(id: String, name: String, colorIndex: Int) =
        folderDao.rename(id, name, colorIndex)

    /** Moves a folder under a new parent (or to the top level). */
    suspend fun moveFolder(id: String, newParentId: String?) {
        val folder = folderDao.byId(id) ?: return
        folderDao.upsert(folder.copy(parentId = newParentId))
    }

    /** Deletes a folder and re-parents its documents and children (spec 2.18). */
    suspend fun deleteFolder(id: String) {
        val children = folderDao.all().filter { it.parentId == id }
        children.forEach { child ->
            folderDao.upsert(child.copy(parentId = null))
        }
        val docs = documentDao.all().filter { it.folderId == id }
        docs.forEach { documentDao.setFolder(it.id, null) }
        folderDao.deleteById(id)
    }

    // -------------------------------------------------------------------- tags

    suspend fun createTag(name: String, colorIndex: Int): LeafTag {
        val entity = TagEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            colorIndex = colorIndex,
            createdAt = System.currentTimeMillis()
        )
        tagDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun renameTag(id: String, name: String, colorIndex: Int) =
        tagDao.rename(id, name, colorIndex)

    suspend fun deleteTag(id: String) {
        tagDao.unlinkTagEverywhere(id)
        tagDao.deleteById(id)
    }

    suspend fun setDocumentTags(documentId: String, tagIds: Set<String>) {
        val current = tagDao.linksFor(documentId).map { it.tagId }.toSet()
        (current - tagIds).forEach { tagDao.unlink(documentId, it) }
        tagIds.forEach { tagDao.link(DocumentTagCrossRef(documentId, it)) }
    }

    // ------------------------------------------------------------- collections

    suspend fun createCollection(name: String, rule: String): LeafCollection {
        val entity = CollectionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            rule = rule,
            builtin = false,
            createdAt = System.currentTimeMillis()
        )
        collectionDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun deleteCollection(id: String) = collectionDao.deleteById(id)

    suspend fun collectionByName(name: String): LeafCollection? = collectionDao.byName(name)?.toModel()

    // ---------------------------------------------------------------- bookmarks

    suspend fun addBookmark(documentId: String, page: Int, label: String? = null): LeafBookmark {
        val entity = com.leaf.reader.data.db.BookmarkEntity(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            page = page,
            label = label,
            createdAt = System.currentTimeMillis()
        )
        bookmarkDao.upsert(entity)
        return entity.toModel()
    }

    suspend fun removeBookmark(id: String) = bookmarkDao.deleteById(id)

    // ---------------------------------------------------------------- highlights

    suspend fun addHighlight(highlight: LeafHighlight) {
        highlightDao.upsert(highlight.toEntity())
    }

    suspend fun updateHighlight(highlight: LeafHighlight) {
        highlightDao.upsert(highlight.toEntity())
    }

    suspend fun removeHighlight(id: String) = highlightDao.deleteById(id)

    suspend fun clearHighlights(documentId: String) = highlightDao.deleteForDocument(documentId)

    suspend fun clearAllHighlights() = highlightDao.deleteAll()

    // -------------------------------------------------------------- reader state

    suspend fun saveReaderState(state: ReaderState) {
        readerStateDao.upsert(
            ReaderStateEntity(
                documentId = state.documentId,
                page = state.page,
                scrollOffset = state.scrollOffset,
                zoom = state.zoom,
                scrollDirection = state.scrollDirection.key,
                reflow = state.reflow,
                theme = state.theme.key,
                orientation = state.orientation.key,
                findQuery = state.findQuery,
                findIndex = state.findIndex,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearReadingPositions() = readerStateDao.deleteAll()

    // ------------------------------------------------------------------- reset

    suspend fun clearSearchHistoryCaches() = textCacheDao.deleteAll()

    suspend fun wipeLibrary() {
        documentDao.all().forEach { documentDao.deleteById(it.id) }
        folderDao.all().forEach { folderDao.deleteById(it.id) }
        tagDao.all().forEach { tagDao.deleteById(it.id) }
        collectionDao.all().forEach { collectionDao.deleteById(it.id) }
        highlightDao.deleteAll()
        readerStateDao.deleteAll()
        recentDao.clearAll()
        textCacheDao.deleteAll()
    }

    // ------------------------------------------------------------------ mappers

    private fun DocumentEntity.toModel() = LeafDocument(
        id = id,
        uri = uri,
        name = displayName,
        kind = FileKind.fromKey(kind) ?: FileKind.TXT,
        folderId = folderId,
        sizeBytes = sizeBytes,
        dateAdded = dateAdded,
        lastOpened = lastOpened,
        pageCount = pageCount,
        currentPage = currentPage,
        isFavorite = isFavorite,
        favAt = favAt,
        colorSeed = colorSeed
    )

    private fun FolderEntity.toModel() = LeafFolder(id, name, parentId, colorIndex, createdAt)

    private fun TagEntity.toModel() = LeafTag(id, name, colorIndex, createdAt)

    private fun CollectionEntity.toModel() = LeafCollection(id, name, rule, builtin, createdAt)

    private fun LeafHighlight.toEntity() = HighlightEntity(
        id = id,
        documentId = documentId,
        color = color.key,
        page = page,
        rects = rects.joinToString(";") { it.encode() },
        textStart = textStart,
        textEnd = textEnd,
        quote = quote,
        createdAt = createdAt
    )

    private fun HighlightEntity.toModel() = LeafHighlight(
        id = id,
        documentId = documentId,
        color = HighlightColor.fromKey(color),
        page = page,
        rects = rects.split(";").mapNotNull { NormRect.decode(it) },
        textStart = textStart,
        textEnd = textEnd,
        quote = quote,
        createdAt = createdAt
    )

    private fun BookmarkEntity.toModel() = LeafBookmark(id, documentId, page, label, createdAt)

    private fun ReaderStateEntity.toModel() = ReaderState(
        documentId = documentId,
        page = page,
        scrollOffset = scrollOffset,
        zoom = zoom,
        scrollDirection = ScrollDirection.fromKey(scrollDirection),
        reflow = reflow,
        theme = ReadingTheme.fromKey(theme),
        orientation = OrientationLock.fromKey(orientation),
        findQuery = findQuery,
        findIndex = findIndex
    )
}
