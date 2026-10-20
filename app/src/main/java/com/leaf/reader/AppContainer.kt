package com.leaf.reader

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.leaf.reader.data.DocumentRepository
import com.leaf.reader.data.db.LeafDatabase
import com.leaf.reader.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container — Leaf stays small and fully offline. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: LeafDatabase = LeafDatabase.get(appContext)

    val settingsStore: SettingsStore = SettingsStore(
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("leaf_settings") }
        )
    )

    val repository: DocumentRepository = DocumentRepository(appContext, database)

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A find query handed from the search screen to the reader (spec 4.8). */
    var pendingFind: Pair<String, String>? = null

    /** Pick-a-document mode for opening a new reader tab (spec 5.17). */
    val pickMode = kotlinx.coroutines.flow.MutableStateFlow(false)
}
