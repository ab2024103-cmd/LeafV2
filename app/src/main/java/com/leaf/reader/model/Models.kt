package com.leaf.reader.model

import androidx.compose.ui.graphics.Color
import com.leaf.reader.ui.theme.HighlightBlue
import com.leaf.reader.ui.theme.HighlightGreen
import com.leaf.reader.ui.theme.HighlightOrange
import com.leaf.reader.ui.theme.HighlightPink
import com.leaf.reader.ui.theme.HighlightYellow
import java.util.Locale

/** The six document families Leaf understands. The order is the fixed card order (spec 4.21). */
enum class FileKind(
    val key: String,
    val label: String,
    val ext: String,
    val mime: String
) {
    PDF("pdf", "PDF", "pdf", "application/pdf"),
    DOCX("docx", "Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("xlsx", "Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "PowerPoint", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    TXT("txt", "Text", "txt", "text/plain"),
    EPUB("epub", "EPUB", "epub", "application/epub+zip");

    val isTextLike: Boolean get() = this == TXT || this == DOCX || this == XLSX || this == PPTX || this == EPUB

    companion object {
        val ordered: List<FileKind> = listOf(PDF, DOCX, XLSX, PPTX, TXT, EPUB)

        fun fromKey(key: String?): FileKind? = ordered.firstOrNull { it.key == key }

        fun fromName(name: String): FileKind? {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return ordered.firstOrNull { it.ext == ext }
        }

        fun fromMime(mime: String?): FileKind? {
            if (mime.isNullOrBlank()) return null
            val m = mime.lowercase(Locale.US)
            return when {
                m.contains("pdf") -> PDF
                m.contains("wordprocessingml") || m.contains("msword") -> DOCX
                m.contains("spreadsheetml") || m.contains("ms-excel") -> XLSX
                m.contains("presentationml") || m.contains("ms-powerpoint") -> PPTX
                m.contains("epub") -> EPUB
                m.startsWith("text/") -> TXT
                else -> null
            }
        }
    }
}

enum class SortField(val label: String) {
    NAME("Name"),
    DATE_ADDED("Date added"),
    LAST_OPENED("Last opened"),
    SIZE("File size")
}

enum class SortDirection { ASCENDING, DESCENDING }

/** Reading canvas themes (spec 3.8). */
enum class ReadingTheme(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    PAPER("paper", "Paper"),
    SEPIA("sepia", "Sepia"),
    NIGHT("night", "Night"),
    OLED("oled", "OLED");

    companion object {
        fun fromKey(key: String?): ReadingTheme = values().firstOrNull { it.key == key } ?: AUTO
    }
}

enum class ScrollDirection(val key: String, val label: String) {
    VERTICAL("vertical", "Vertical"),
    HORIZONTAL("horizontal", "Horizontal");

    companion object {
        fun fromKey(key: String?): ScrollDirection = values().firstOrNull { it.key == key } ?: VERTICAL
    }
}

enum class ReadingTypeface(val key: String, val label: String) {
    SERIF("serif", "Serif"),
    SANS("sans", "Sans");

    companion object {
        fun fromKey(key: String?): ReadingTypeface = values().firstOrNull { it.key == key } ?: SERIF
    }
}

enum class AppThemeMode(val key: String, val label: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System");

    companion object {
        fun fromKey(key: String?): AppThemeMode = values().firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class OrientationLock(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape");

    companion object {
        fun fromKey(key: String?): OrientationLock = values().firstOrNull { it.key == key } ?: AUTO
    }
}

/** The five highlight colours (spec 5.1). */
enum class HighlightColor(val key: String, val label: String, val tint: Color) {
    YELLOW("yellow", "Yellow", HighlightYellow),
    GREEN("green", "Green", HighlightGreen),
    BLUE("blue", "Blue", HighlightBlue),
    PINK("pink", "Pink", HighlightPink),
    ORANGE("orange", "Orange", HighlightOrange);

    companion object {
        fun fromKey(key: String?): HighlightColor = values().firstOrNull { it.key == key } ?: YELLOW
    }
}

enum class LeafDestination(val key: String, val label: String) {
    LIBRARY("library", "Library"),
    RECENTS("recents", "Recents"),
    FAVORITES("favorites", "Favorites"),
    SEARCH("search", "Search"),
    SETTINGS("settings", "Settings");

    companion object {
        fun fromKey(key: String?): LeafDestination = values().firstOrNull { it.key == key } ?: LIBRARY
    }
}

/** Built-in smart collections (spec 2.20). */
enum class SmartRule(val key: String, val label: String) {
    ALL_PDFS("all_pdfs", "All PDFs"),
    ALL_DOCS("all_docs", "All documents"),
    ADDED_WEEK("added_week", "Added this week"),
    ADDED_MONTH("added_month", "Added this month"),
    STILL_READING("still_reading", "Still reading"),
    NOT_FILED("not_filed", "Not filed"),
    HAS_HIGHLIGHTS("has_highlights", "Has highlights");

    companion object {
        fun fromKey(key: String?): SmartRule? = values().firstOrNull { it.key == key }
    }
}

/** A document as the UI sees it. */
data class LeafDocument(
    val id: String,
    val uri: String,
    val name: String,
    val kind: FileKind,
    val folderId: String?,
    val sizeBytes: Long,
    val dateAdded: Long,
    val lastOpened: Long?,
    val pageCount: Int,
    val currentPage: Int,
    val isFavorite: Boolean,
    val favAt: Long?,
    val colorSeed: Int
) {
    val progressPercent: Int
        get() = if (pageCount > 0) (((currentPage + 1) * 100) / pageCount).coerceIn(0, 100) else 0

    val startedReading: Boolean get() = currentPage > 0
}

data class LeafFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val colorIndex: Int,
    val createdAt: Long
)

data class LeafTag(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long
)

data class LeafCollection(
    val id: String,
    val name: String,
    val rule: String,
    val builtin: Boolean,
    val createdAt: Long
)

data class RecentEntry(
    val documentId: String,
    val openedAt: Long,
    val document: LeafDocument?
)

data class LeafBookmark(
    val id: String,
    val documentId: String,
    val page: Int,
    val label: String?,
    val createdAt: Long
)

/** Normalised page-space rectangle (0..1). */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun encode(): String = "$left,$top,$right,$bottom"

    companion object {
        fun decode(raw: String): NormRect? {
            val parts = raw.split(",")
            if (parts.size != 4) return null
            val v = parts.mapNotNull { it.toFloatOrNull() }
            if (v.size != 4) return null
            return NormRect(v[0], v[1], v[2], v[3])
        }
    }
}

data class LeafHighlight(
    val id: String,
    val documentId: String,
    val color: HighlightColor,
    val page: Int,
    val rects: List<NormRect>,
    val textStart: Int,
    val textEnd: Int,
    val quote: String,
    val createdAt: Long
)

/** Per-document reading position (spec 3.1). */
data class ReaderState(
    val documentId: String,
    val page: Int = 0,
    val scrollOffset: Float = 0f,
    val zoom: Float = 1f,
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val reflow: Boolean = false,
    val theme: ReadingTheme = ReadingTheme.AUTO,
    val orientation: OrientationLock = OrientationLock.AUTO,
    val findQuery: String = "",
    val findIndex: Int = 0
)

/** One open reader tab (spec 5.10). */
data class OpenTab(
    val documentId: String,
    val page: Int = 0,
    val zoom: Float = 1f,
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val reflow: Boolean = false,
    val theme: ReadingTheme = ReadingTheme.AUTO,
    val findQuery: String = "",
    val findIndex: Int = 0
)

/** A hit inside a file (spec 4.7). */
data class InFileMatch(
    val documentId: String,
    val documentName: String,
    val documentKind: FileKind,
    val page: Int,
    val count: Int,
    val snippet: String,
    val matchStart: Int,
    val matchEnd: Int
)

data class SearchSummary(
    val documents: Int,
    val filesContaining: Int,
    val hits: Int
)

data class LeafSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val readingTheme: ReadingTheme = ReadingTheme.AUTO,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val language: String = "en",
    val defaultScrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val defaultReflow: Boolean = false,
    val defaultZoom: Float = 1f,
    val typeface: ReadingTypeface = ReadingTypeface.SERIF,
    val rememberLastPage: Boolean = true,
    val showPageNumbers: Boolean = true,
    val autoRotate: Boolean = true,
    val lockOrientation: OrientationLock = OrientationLock.AUTO,
    val maxTabs: Int = 6,
    val keepAwake: Boolean = false,
    val destination: LeafDestination = LeafDestination.LIBRARY,
    val librarySortField: SortField = SortField.DATE_ADDED,
    val librarySortDirection: SortDirection = SortDirection.DESCENDING,
    val favoritesSortField: SortField = SortField.DATE_ADDED,
    val favoritesSortDirection: SortDirection = SortDirection.DESCENDING,
    val groupByType: Boolean = false,
    val searchHistory: List<String> = emptyList()
)
