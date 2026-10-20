package com.leaf.reader.ui

import com.leaf.reader.model.FileKind
import com.leaf.reader.model.LeafDocument
import com.leaf.reader.model.LeafFolder
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Sorts documents the way the library, favorites and file-type groups do (spec 4.14). */
fun List<LeafDocument>.sortDocuments(
    field: SortField,
    direction: SortDirection
): List<LeafDocument> {
    val sorted = when (field) {
        SortField.NAME -> sortedBy { it.name.lowercase(Locale.getDefault()) }
        SortField.DATE_ADDED -> sortedBy { it.dateAdded }
        SortField.LAST_OPENED -> sortedBy { it.lastOpened ?: 0L }
        SortField.SIZE -> sortedBy { it.sizeBytes }
    }
    return if (direction == SortDirection.ASCENDING) sorted else sorted.reversed()
}

/** All folder ids below (and including) the given folder. */
fun List<LeafFolder>.descendantIds(folderId: String): Set<String> {
    val result = mutableSetOf(folderId)
    var changed = true
    while (changed) {
        changed = false
        this.forEach { folder ->
            val parent = folder.parentId
            if (parent != null && parent in result && folder.id !in result) {
                result.add(folder.id)
                changed = true
            }
        }
    }
    return result
}

fun List<LeafDocument>.inFolder(folderId: String?, folders: List<LeafFolder>): List<LeafDocument> {
    if (folderId == null) return this
    val ids = folders.descendantIds(folderId)
    return filter { it.folderId in ids }
}

fun List<LeafDocument>.withTag(tagId: String, documentTags: Map<String, List<com.leaf.reader.model.LeafTag>>): List<LeafDocument> =
    filter { doc -> documentTags[doc.id].orEmpty().any { it.id == tagId } }

fun List<LeafDocument>.withAllTags(tagIds: Set<String>, documentTags: Map<String, List<com.leaf.reader.model.LeafTag>>): List<LeafDocument> =
    filter { doc ->
        val tags = documentTags[doc.id].orEmpty().map { it.id }.toSet()
        tagIds.all { it in tags }
    }

fun List<LeafDocument>.addedWithin(days: Int, now: Long = System.currentTimeMillis()): List<LeafDocument> {
    val cutoff = now - TimeUnit.DAYS.toMillis(days.toLong())
    return filter { it.dateAdded >= cutoff }
}

/** Evaluates a smart-collection rule against a document. */
fun matchesRule(rule: String, document: LeafDocument, state: LibraryState): Boolean {
    return when {
        rule == "all_pdfs" -> document.kind == FileKind.PDF
        rule == "all_docs" -> true
        rule == "added_week" -> document in state.documents.addedWithin(7)
        rule == "added_month" -> document in state.documents.addedWithin(30)
        rule == "still_reading" -> document.startedReading && document.progressPercent < 100
        rule == "not_filed" -> document.folderId == null
        rule == "has_highlights" -> state.highlightsFor(document.id).isNotEmpty()
        rule.startsWith("type:") -> document.kind.key == rule.removePrefix("type:")
        rule.startsWith("folder:") -> {
            val id = rule.removePrefix("folder:")
            val ids = state.folders.descendantIds(id)
            document.folderId in ids
        }
        rule.startsWith("tag:") -> {
            val id = rule.removePrefix("tag:")
            state.tagsFor(document.id).any { it.id == id }
        }
        rule.startsWith("added:") -> {
            val days = rule.removePrefix("added:").toIntOrNull() ?: 7
            document in state.documents.addedWithin(days)
        }
        else -> true
    }
}

/** Human readable description of a rule, used in the collection manager. */
fun describeRule(rule: String, state: LibraryState): String = when {
    rule == "all_pdfs" -> "Every PDF in the library"
    rule == "all_docs" -> "Every document in the library"
    rule == "added_week" -> "Documents added in the last 7 days"
    rule == "added_month" -> "Documents added in the last 30 days"
    rule == "still_reading" -> "Documents you started but have not finished"
    rule == "not_filed" -> "Documents that are not in a folder"
    rule == "has_highlights" -> "Documents that contain highlights"
    rule.startsWith("type:") -> "All ${FileKind.fromKey(rule.removePrefix("type:"))?.label ?: "documents"}"
    rule.startsWith("folder:") -> "Everything in ${state.folderName(rule.removePrefix("folder:")) ?: "a folder"}"
    rule.startsWith("tag:") -> "Documents tagged ${state.tags.firstOrNull { it.id == rule.removePrefix("tag:") }?.name ?: "with a tag"}"
    rule.startsWith("added:") -> "Documents added in the last ${rule.removePrefix("added:")} days"
    else -> "Custom rule"
}

/** Sort label such as "Name · A–Z" (spec 4.16). */
fun sortLabel(field: SortField, direction: SortDirection): String {
    val arrow = if (direction == SortDirection.ASCENDING) "A–Z" else "Z–A"
    return "${field.label} · $arrow"
}
