package com.leaf.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf.reader.model.AppThemeMode
import com.leaf.reader.ui.LeafApp
import com.leaf.reader.ui.theme.LeafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LeafApp).container
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LeafTheme(darkTheme = dark) {
                LeafApp(container)
            }
        }
    }
}
