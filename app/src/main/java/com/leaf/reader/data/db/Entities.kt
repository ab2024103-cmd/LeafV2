package com.leaf.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true)])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val kind: String,
    val folderId: String?,
    val sizeBytes: Long,
    val dateAdded: Long,
    val lastOpened: Long?,
    val pageCount: Int,
    val currentPage: Int,
    val isFavorite: Boolean,
    val favAt: Long?,
    val colorSeed: Int
)

@Entity(tableName = "folders", indices = [Index(value = ["parentId"])])
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val colorIndex: Int,
    val createdAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long
)

@Entity(tableName = "document_tags", primaryKeys = ["documentId", "tagId"])
data class DocumentTagCrossRef(
    val documentId: String,
    val tagId: String
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rule: String,
    val builtin: Boolean,
    val createdAt: Long
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val documentId: String,
    val openedAt: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val page: Int,
    val label: String?,
    val createdAt: Long
)

@Entity(tableName = "highlights", indices = [Index(value = ["documentId"])])
data class HighlightEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val color: String,
    val page: Int,
    val rects: String,
    val textStart: Int,
    val textEnd: Int,
    val quote: String,
    val createdAt: Long
)

@Entity(tableName = "reader_state")
data class ReaderStateEntity(
    @PrimaryKey val documentId: String,
    val page: Int,
    val scrollOffset: Float,
    val zoom: Float,
    val scrollDirection: String,
    val reflow: Boolean,
    val theme: String,
    val orientation: String,
    val findQuery: String,
    val findIndex: Int,
    val updatedAt: Long
)

@Entity(tableName = "text_cache")
data class TextCacheEntity(
    @PrimaryKey val documentId: String,
    val text: String,
    val pageOffsets: String,
    val updatedAt: Long
)
