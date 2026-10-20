package com.leaf.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leaf.reader.AppContainer
import com.leaf.reader.data.DemoLibrary
import com.leaf.reader.model.AppThemeMode
import com.leaf.reader.model.LeafSettings
import com.leaf.reader.model.OrientationLock
import com.leaf.reader.model.ReadingTheme
import com.leaf.reader.model.ReadingTypeface
import com.leaf.reader.model.ScrollDirection
import com.leaf.reader.ui.LibraryState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    state: LibraryState,
    onOpenAbout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val store = container.settingsStore

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsHeader("Appearance") }
            item {
                ChoiceRow(
                    title = "App theme",
                    options = AppThemeMode.values().toList(),
                    selected = settings.themeMode,
                    label = { it.label },
                    onSelect = { scope.launch { store.setThemeMode(it) } }
                )
            }
            item {
                ChoiceRow(
                    title = "Default reading theme",
                    options = ReadingTheme.values().toList(),
                    selected = settings.readingTheme,
                    label = { it.label },
                    onSelect = { scope.launch { store.setReadingTheme(it) } }
                )
            }
            item {
                SwitchRow(
                    title = "Animations",
                    subtitle = "Page turn and leaf sway transitions",
                    checked = settings.animations,
                    onChange = { scope.launch { store.setAnimations(it) } }
                )
            }
            item {
                SwitchRow(
                    title = "Haptic feedback",
                    subtitle = "Vibration on stars, actions and long presses",
                    checked = settings.haptics,
                    onChange = { scope.launch { store.setHaptics(it) } }
                )
            }
            item {
                ChoiceRow(
                    title = "Language",
                    options = listOf("en"),
                    selected = settings.language,
                    label = { if (it == "en") "English" else it },
                    onSelect = { scope.launch { store.setLanguage(it) } }
                )
            }

            item { SettingsHeader("Reading") }
            item {
                ChoiceRow(
                    title = "Default scroll direction",
                    options = ScrollDirection.values().toList(),
                    selected = settings.defaultScrollDirection,
                    label = { it.label },
                    onSelect = { scope.launch { store.setDefaultScrollDirection(it) } }
                )
            }
            item {
                SwitchRow(
                    title = "Default text reflow",
                    subtitle = "Reflow extracted text to the screen width",
                    checked = settings.defaultReflow,
                    onChange = { scope.launch { store.setDefaultReflow(it) } }
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Default zoom", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "${(settings.defaultZoom * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = settings.defaultZoom,
                        onValueChange = { value ->
                            scope.launch { store.setDefaultZoom((value * 100).toInt() / 100f) }
                        },
                        valueRange = 0.7f..2.5f,
                        steps = 17
                    )
                }
            }
            item {
                ChoiceRow(
                    title = "Reading typeface",
                    options = ReadingTypeface.values().toList(),
                    selected = settings.typeface,
                    label = { it.label },
                    onSelect = { scope.launch { store.setTypeface(it) } }
                )
            }
            item {
                SwitchRow(
                    title = "Remember last page",
                    subtitle = "Resume where you stopped reading",
                    checked = settings.rememberLastPage,
                    onChange = { scope.launch { store.setRememberLastPage(it) } }
                )
            }
            item {
                SwitchRow(
                    title = "Show page numbers on pages",
                    subtitle = "Hide the page number printed on the page",
                    checked = settings.showPageNumbers,
                    onChange = { scope.launch { store.setShowPageNumbers(it) } }
                )
            }

            item { SettingsHeader("Device") }
            item {
                SwitchRow(
                    title = "Auto-rotate",
                    subtitle = "Follow the device orientation while reading",
                    checked = settings.autoRotate,
                    onChange = { scope.launch { store.setAutoRotate(it) } }
                )
            }
            item {
                ChoiceRow(
                    title = "Lock orientation",
                    options = OrientationLock.values().toList(),
                    selected = settings.lockOrientation,
                    label = { it.label },
                    onSelect = { scope.launch { store.setLockOrientation(it) } }
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Maximum open tabs", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = "${settings.maxTabs}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = settings.maxTabs.toFloat(),
                        onValueChange = { value -> scope.launch { store.setMaxTabs(value.toInt()) } },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Keep screen awake while reading",
                    checked = settings.keepAwake,
                    onChange = { scope.launch { store.setKeepAwake(it) } }
                )
            }

            item { SettingsHeader("Data & storage") }
            item {
                ActionRow("Clear recents", "Removes the history list only") {
                    scope.launch { container.repository.clearRecents() }
                }
            }
            item {
                ActionRow("Clear search history", "Removes the last 10 queries") {
                    scope.launch { store.setSearchHistory(emptyList()) }
                }
            }
            item {
                ActionRow("Clear reading positions", "Every document starts from page 1") {
                    scope.launch { container.repository.clearReadingPositions() }
                }
            }
            item {
                ActionRow("Clear all highlights", "Removes every highlight in the library") {
                    scope.launch { container.repository.clearAllHighlights() }
                }
            }
            item {
                ActionRow("Reset demo library", "Recreates the bundled sample documents") {
                    scope.launch {
                        container.repository.wipeLibrary()
                        DemoLibrary.ensureSeeded(container.appContext, container.repository)
                    }
                }
            }

            item { SettingsHeader("About") }
            item {
                ActionRow("Leaf · spec coverage", "Version, live counts and feature list") {
                    onOpenAbout()
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAbout() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text("About Leaf", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}
