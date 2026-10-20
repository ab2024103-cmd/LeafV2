package com.leaf.reader.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.leaf.reader.model.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Finds every document Leaf can read that already lives on the device.
 *
 * Three strategies, in order of preference:
 *  1. [scanTree] — the Storage Access Framework tree grant. The user picks a folder once
 *     (no runtime permission is ever requested) and Leaf can then list everything below it.
 *  2. [scanMediaStore] — the media/document database. Needs no permission on Android 10+.
 *  3. [scanStorage] — a direct walk of shared storage. Requires READ_EXTERNAL_STORAGE, so the
 *     app only uses it when the user explicitly asks for a full scan.
 */
data class DeviceFile(
    val uri: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val location: String
)

object DeviceScanner {

    private val SUPPORTED_EXTENSIONS = setOf("pdf", "docx", "xlsx", "pptx", "txt", "epub")

    fun kindOf(name: String, mime: String?): FileKind? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension in SUPPORTED_EXTENSIONS) return FileKind.fromName(name)
        return FileKind.fromMime(mime)
    }

    /** True when the platform lets us list shared storage without a runtime permission. */
    fun canScanWithoutPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // ------------------------------------------------------------ media store

    /**
     * Lists documents known to the media/document database. On Android 10+ this works without
     * any runtime permission; on older releases it still returns the media/document rows the
     * platform is willing to share.
     */
    suspend fun scanMediaStore(context: Context): List<DeviceFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceFile>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    if (name.isBlank()) continue
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    val kind = kindOf(name, mime) ?: continue
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                    val uri = if (id >= 0) {
                        Uri.withAppendedPath(collection, id.toString())
                    } else {
                        Uri.parse("content://${MediaStore.Files.FileColumns._ID}/$id")
                    }
                    val data = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                    out += DeviceFile(
                        uri = uri.toString(),
                        name = name,
                        kind = kind,
                        sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                        modifiedAt = if (dateIndex >= 0 && !cursor.isNull(dateIndex)) cursor.getLong(dateIndex) * 1000L else 0L,
                        location = data?.let { prettyPath(it) } ?: "Device"
                    )
                }
            }
        }
        out.distinctBy { it.uri }
    }

    // --------------------------------------------------------------- tree scan

    /**
     * Walks a folder the user granted through the system picker. No permission dialog is shown:
     * the grant itself is the consent, and it is persisted by the caller.
     */
    suspend fun scanTree(context: Context, treeUri: Uri, maxDepth: Int = 8): List<DeviceFile> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<DeviceFile>()
            val rootName = treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Device"
            runCatching { walk(context, treeUri, treeUri, rootName, 0, maxDepth, out) }
            out.distinctBy { it.uri }
        }

    private fun walk(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        location: String,
        depth: Int,
        maxDepth: Int,
        out: MutableList<DeviceFile>
    ) {
        if (depth > maxDepth || out.size > 4000) return
        val documentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull() ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val childId = if (idIndex >= 0) cursor.getString(idIndex).orEmpty() else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    if (childId.isBlank() || name.isBlank()) continue
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    val isFolder = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    if (isFolder) {
                        if (!name.startsWith(".")) {
                            walk(context, treeUri, childUri, "$location/$name", depth + 1, maxDepth, out)
                        }
                    } else {
                        val kind = kindOf(name, mime) ?: continue
                        out += DeviceFile(
                            uri = childUri.toString(),
                            name = name,
                            kind = kind,
                            sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                            modifiedAt = if (dateIndex >= 0 && !cursor.isNull(dateIndex)) cursor.getLong(dateIndex) else 0L,
                            location = location
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- direct storage

    /**
     * Walks shared storage directly. The caller must hold READ_EXTERNAL_STORAGE, which on
     * Android 6+ means the user has to grant it once.
     */
    suspend fun scanStorage(context: Context): List<DeviceFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceFile>()
        val roots = buildList {
            Environment.getExternalStorageDirectory()?.let { add(it) }
            runCatching {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()?.listFiles()?.forEach { child ->
                    if (child.isDirectory && child.name.startsWith("/storage/")) add(child)
                }
            }
        }
        val seen = mutableSetOf<String>()
        roots.forEach { root -> walkDirectory(root, root.name, 0, out, seen) }
        out
    }

    private fun walkDirectory(
        dir: File,
        location: String,
        depth: Int,
        out: MutableList<DeviceFile>,
        seen: MutableSet<String>
    ) {
        if (depth > 8 || out.size > 4000) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { child ->
            val name = child.name
            if (name.startsWith(".")) return@forEach
            if (child.isDirectory) {
                walkDirectory(child, "$location/$name", depth + 1, out, seen)
            } else {
                val kind = kindOf(name, null) ?: return@forEach
                val path = child.absolutePath
                if (!seen.add(path)) return@forEach
                out += DeviceFile(
                    uri = Uri.fromFile(child).toString(),
                    name = name,
                    kind = kind,
                    sizeBytes = child.length(),
                    modifiedAt = child.lastModified(),
                    location = location
                )
            }
        }
    }

    private fun prettyPath(dataPath: String): String {
        val marker = "/storage/"
        val index = dataPath.indexOf(marker)
        if (index < 0) return dataPath.substringAfterLast('/')
        return dataPath.substring(index + marker.length)
    }
}
