package com.leaf.reader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uri = :uri")
    suspend fun byUri(uri: String): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun all(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(docs: List<DocumentEntity>)

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE documents SET currentPage = :page, lastOpened = :at WHERE id = :id")
    suspend fun setProgress(id: String, page: Int, at: Long)

    @Query("UPDATE documents SET isFavorite = :favorite, favAt = :favAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, favAt: Long?)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?)

    @Query("UPDATE documents SET displayName = :name WHERE id = :id")
    suspend fun setName(id: String, name: String)

    @Query("UPDATE documents SET pageCount = :count WHERE id = :id")
    suspend fun setPageCount(id: String, count: Int)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE folders SET name = :name, colorIndex = :color WHERE id = :id")
    suspend fun rename(id: String, name: String, color: Int)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun byId(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE tags SET name = :name, colorIndex = :color WHERE id = :id")
    suspend fun rename(id: String, name: String, color: Int)

    @Query("SELECT * FROM document_tags")
    fun observeLinks(): Flow<List<DocumentTagCrossRef>>

    @Query("SELECT * FROM document_tags WHERE documentId = :documentId")
    suspend fun linksFor(documentId: String): List<DocumentTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(ref: DocumentTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkAll(refs: List<DocumentTagCrossRef>)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId AND tagId = :tagId")
    suspend fun unlink(documentId: String, tagId: String)

    @Query("DELETE FROM document_tags WHERE tagId = :tagId")
    suspend fun unlinkTagEverywhere(tagId: String)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun unlinkAll(documentId: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents")
    fun observeAll(): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recents")
    suspend fun all(): List<RecentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recent: RecentEntity)

    @Query("DELETE FROM recents WHERE documentId = :documentId")
    suspend fun delete(documentId: String)

    @Query("DELETE FROM recents")
    suspend fun clearAll()

    @Query("DELETE FROM recents WHERE openedAt < :before")
    suspend fun clearBefore(before: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY page ASC")
    fun observeFor(documentId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY page ASC")
    suspend fun forDocument(documentId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun observeFor(documentId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY createdAt DESC")
    suspend fun forDocument(documentId: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights")
    fun observeAll(): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlight: HighlightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(highlights: List<HighlightEntity>)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM highlights WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("DELETE FROM highlights")
    suspend fun deleteAll()
}

@Dao
interface ReaderStateDao {
    @Query("SELECT * FROM reader_state")
    fun observeAll(): Flow<List<ReaderStateEntity>>

    @Query("SELECT * FROM reader_state WHERE documentId = :documentId")
    suspend fun byId(documentId: String): ReaderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReaderStateEntity)

    @Query("DELETE FROM reader_state WHERE documentId = :documentId")
    suspend fun deleteById(documentId: String)

    @Query("DELETE FROM reader_state")
    suspend fun deleteAll()
}

@Dao
interface TextCacheDao {
    @Query("SELECT * FROM text_cache WHERE documentId = :documentId")
    suspend fun byId(documentId: String): TextCacheEntity?

    @Query("SELECT * FROM text_cache")
    suspend fun all(): List<TextCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: TextCacheEntity)

    @Query("DELETE FROM text_cache")
    suspend fun deleteAll()
}
