package com.leaf.reader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LeafApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
