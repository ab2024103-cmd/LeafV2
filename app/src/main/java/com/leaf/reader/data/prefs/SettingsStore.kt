package com.leaf.reader.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leaf.reader.model.AppThemeMode
import com.leaf.reader.model.LeafDestination
import com.leaf.reader.model.LeafSettings
import com.leaf.reader.model.OpenTab
import com.leaf.reader.model.OrientationLock
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.model.ReadingTypeface
import com.leaf.reader.model.ScrollDirection
import com.leaf.reader.model.SortDirection
import com.leaf.reader.model.SortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** DataStore-backed preferences (spec 10.7): settings, navigation state and reader tabs. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val readingTheme = stringPreferencesKey("reading_theme")
        val animations = booleanPreferencesKey("animations")
        val haptics = booleanPreferencesKey("haptics")
        val language = stringPreferencesKey("language")
        val defaultScroll = stringPreferencesKey("default_scroll")
        val defaultReflow = booleanPreferencesKey("default_reflow")
        val defaultZoom = floatPreferencesKey("default_zoom")
        val typeface = stringPreferencesKey("typeface")
        val rememberLastPage = booleanPreferencesKey("remember_last_page")
        val showPageNumbers = booleanPreferencesKey("show_page_numbers")
        val autoRotate = booleanPreferencesKey("auto_rotate")
        val lockOrientation = stringPreferencesKey("lock_orientation")
        val maxTabs = intPreferencesKey("max_tabs")
        val keepAwake = booleanPreferencesKey("keep_awake")
        val destination = stringPreferencesKey("destination")
        val libSortField = stringPreferencesKey("lib_sort_field")
        val libSortDir = stringPreferencesKey("lib_sort_dir")
        val favSortField = stringPreferencesKey("fav_sort_field")
        val favSortDir = stringPreferencesKey("fav_sort_dir")
        val groupByType = booleanPreferencesKey("group_by_type")
        val searchHistory = stringPreferencesKey("search_history")
        val tabs = stringPreferencesKey("tabs")
        val activeTab = stringPreferencesKey("active_tab")
    }

    val settings: Flow<LeafSettings> = dataStore.data.map { prefs ->
        LeafSettings(
            themeMode = AppThemeMode.fromKey(prefs[Keys.themeMode]),
            readingTheme = ReadingTheme.fromKey(prefs[Keys.readingTheme]),
            animations = prefs[Keys.animations] ?: true,
            haptics = prefs[Keys.haptics] ?: true,
            language = prefs[Keys.language] ?: "en",
            defaultScrollDirection = ScrollDirection.fromKey(prefs[Keys.defaultScroll]),
            defaultReflow = prefs[Keys.defaultReflow] ?: false,
            defaultZoom = prefs[Keys.defaultZoom] ?: 1f,
            typeface = ReadingTypeface.fromKey(prefs[Keys.typeface]),
            rememberLastPage = prefs[Keys.rememberLastPage] ?: true,
            showPageNumbers = prefs[Keys.showPageNumbers] ?: true,
            autoRotate = prefs[Keys.autoRotate] ?: true,
            lockOrientation = OrientationLock.fromKey(prefs[Keys.lockOrientation]),
            maxTabs = (prefs[Keys.maxTabs] ?: 6).coerceIn(1, 10),
            keepAwake = prefs[Keys.keepAwake] ?: false,
            destination = LeafDestination.fromKey(prefs[Keys.destination]),
            librarySortField = runCatching { SortField.valueOf(prefs[Keys.libSortField] ?: SortField.DATE_ADDED.name) }
                .getOrDefault(SortField.DATE_ADDED),
            librarySortDirection = runCatching { SortDirection.valueOf(prefs[Keys.libSortDir] ?: SortDirection.DESCENDING.name) }
                .getOrDefault(SortDirection.DESCENDING),
            favoritesSortField = runCatching { SortField.valueOf(prefs[Keys.favSortField] ?: SortField.DATE_ADDED.name) }
                .getOrDefault(SortField.DATE_ADDED),
            favoritesSortDirection = runCatching { SortDirection.valueOf(prefs[Keys.favSortDir] ?: SortDirection.DESCENDING.name) }
                .getOrDefault(SortDirection.DESCENDING),
            groupByType = prefs[Keys.groupByType] ?: false,
            searchHistory = decodeHistory(prefs[Keys.searchHistory])
        )
    }

    suspend fun setThemeMode(value: AppThemeMode) = put(Keys.themeMode, value.key)
    suspend fun setReadingTheme(value: ReadingTheme) = put(Keys.readingTheme, value.key)
    suspend fun setAnimations(value: Boolean) = put(Keys.animations, value)
    suspend fun setHaptics(value: Boolean) = put(Keys.haptics, value)
    suspend fun setLanguage(value: String) = put(Keys.language, value)
    suspend fun setDefaultScrollDirection(value: ScrollDirection) = put(Keys.defaultScroll, value.key)
    suspend fun setDefaultReflow(value: Boolean) = put(Keys.defaultReflow, value)
    suspend fun setDefaultZoom(value: Float) = put(Keys.defaultZoom, value)
    suspend fun setTypeface(value: ReadingTypeface) = put(Keys.typeface, value.key)
    suspend fun setRememberLastPage(value: Boolean) = put(Keys.rememberLastPage, value)
    suspend fun setShowPageNumbers(value: Boolean) = put(Keys.showPageNumbers, value)
    suspend fun setAutoRotate(value: Boolean) = put(Keys.autoRotate, value)
    suspend fun setLockOrientation(value: OrientationLock) = put(Keys.lockOrientation, value.key)
    suspend fun setMaxTabs(value: Int) = put(Keys.maxTabs, value.coerceIn(1, 10))
    suspend fun setKeepAwake(value: Boolean) = put(Keys.keepAwake, value)
    suspend fun setDestination(value: LeafDestination) = put(Keys.destination, value.key)
    suspend fun setLibrarySort(field: SortField, direction: SortDirection) {
        dataStore.edit {
            it[Keys.libSortField] = field.name
            it[Keys.libSortDir] = direction.name
        }
    }

    suspend fun setFavoritesSort(field: SortField, direction: SortDirection) {
        dataStore.edit {
            it[Keys.favSortField] = field.name
            it[Keys.favSortDir] = direction.name
        }
    }

    suspend fun setGroupByType(value: Boolean) = put(Keys.groupByType, value)
    suspend fun setSearchHistory(values: List<String>) = put(Keys.searchHistory, encodeHistory(values))

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    // ---------------------------------------------------------------- tabs

    val tabs: Flow<List<OpenTab>> = dataStore.data.map { decodeTabs(it[Keys.tabs]) }

    val activeTab: Flow<String?> = dataStore.data.map { prefs -> prefs[Keys.activeTab] }

    suspend fun setActiveTab(documentId: String?) {
        dataStore.edit {
            if (documentId == null) it.remove(Keys.activeTab) else it[Keys.activeTab] = documentId
        }
    }

    suspend fun setTabs(value: List<OpenTab>) {
        dataStore.edit { it[Keys.tabs] = encodeTabs(value) }
    }

    // ------------------------------------------------------------- helpers

    companion object {
        fun encodeTabs(tabs: List<OpenTab>): String {
            val arr = JSONArray()
            tabs.forEach { tab ->
                val o = JSONObject()
                o.put("documentId", tab.documentId)
                o.put("page", tab.page)
                o.put("zoom", tab.zoom.toDouble())
                o.put("scroll", tab.scrollDirection.key)
                o.put("reflow", tab.reflow)
                o.put("theme", tab.theme.key)
                o.put("findQuery", tab.findQuery)
                o.put("findIndex", tab.findIndex)
                arr.put(o)
            }
            return arr.toString()
        }

        fun decodeTabs(raw: String?): List<OpenTab> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    OpenTab(
                        documentId = o.optString("documentId"),
                        page = o.optInt("page", 0),
                        zoom = o.optDouble("zoom", 1.0).toFloat(),
                        scrollDirection = ScrollDirection.fromKey(o.optString("scroll")),
                        reflow = o.optBoolean("reflow", false),
                        theme = ReadingTheme.fromKey(o.optString("theme")),
                        findQuery = o.optString("findQuery"),
                        findIndex = o.optInt("findIndex", 0)
                    )
                }.filter { it.documentId.isNotBlank() }
            }.getOrDefault(emptyList())
        }

        fun encodeHistory(values: List<String>): String {
            val arr = JSONArray()
            values.forEach { arr.put(it) }
            return arr.toString()
        }

        fun decodeHistory(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrDefault(emptyList())
        }
    }
}
