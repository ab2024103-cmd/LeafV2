package com.leaf.reader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch

class LeafApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        // Seed the offline sample library the first time the app runs.
        container.appScope.launch {
            runCatching {
                com.leaf.reader.data.DemoLibrary.ensureSeeded(container.appContext, container.repository)
            }
        }
    }
}
