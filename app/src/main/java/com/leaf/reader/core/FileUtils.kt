package com.leaf.reader.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

/** Metadata resolved from a content uri. */
data class FileMeta(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModified: Long
)

object FileUtils {

    /**
     * Opens a document for reading. `file://` uris are opened directly, every other uri goes
     * through the content resolver — this keeps the demo library and the device browser working
     * on every Android release.
     */
    fun openInputStream(context: Context, uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return runCatching { FileInputStream(File(path)) }.getOrNull()
        }
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    /** Opens a seekable descriptor (needed by the platform PDF renderer). */
    fun openFileDescriptor(context: Context, uri: Uri, mode: String = "r"): ParcelFileDescriptor? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val flags = if (mode.contains('w')) {
                ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            } else {
                ParcelFileDescriptor.MODE_READ_ONLY
            }
            return runCatching { ParcelFileDescriptor.open(File(path), flags) }.getOrNull()
        }
        return runCatching { context.contentResolver.openFileDescriptor(uri, mode) }.getOrNull()
    }

    fun queryMeta(context: Context, uri: Uri): FileMeta {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        var size = 0L
        var mime: String? = resolver.getType(uri)
        var modified = 0L
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                name = file.name
                size = file.length()
                modified = file.lastModified()
            }
        } else {
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) modified = cursor.getLong(modifiedIndex)
                    }
                }
            }
        }
        return FileMeta(name, size, mime, modified)
    }

    /** Renames a document through the storage provider when it supports it. */
    fun rename(context: Context, uri: Uri, newName: String): Boolean {
        if (uri.scheme == "file") {
            return runCatching {
                val file = File(uri.path ?: "")
                val target = File(file.parentFile, newName)
                file.renameTo(target)
            }.getOrDefault(false)
        }
        return runCatching {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
        }.getOrDefault(false)
    }

    /** Deletes a document through the storage provider when it supports it. */
    fun delete(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            return runCatching {
                val file = File(uri.path ?: "")
                file.delete()
            }.getOrDefault(false)
        }
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrDefault(false)
    }

    fun isAppOwned(uri: Uri): Boolean = uri.scheme == "file"

    fun isPdfLike(name: String): Boolean = name.lowercase(Locale.US).endsWith(".pdf")
}
