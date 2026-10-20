package com.leaf.reader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCE7D2),
    onPrimaryContainer = Color(0xFF06301E),
    secondary = Bark,
    onSecondary = Color.White,
    secondaryContainer = Linen,
    onSecondaryContainer = Color(0xFF2C2213),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6DCC0),
    onTertiaryContainer = Color(0xFF2F1704),
    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = ParchmentDim,
    onSurfaceVariant = InkSoft,
    surfaceContainer = Color(0xFFF6F1E7),
    surfaceContainerHigh = Color(0xFFF0EADE),
    surfaceContainerHighest = Color(0xFFE9E2D4),
    outline = Color(0xFF7C847D),
    outlineVariant = Color(0xFFCFC8BA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = LeafGreenLight,
    onPrimary = Color(0xFF00381F),
    primaryContainer = Color(0xFF14513A),
    onPrimaryContainer = Color(0xFFBCE7D2),
    secondary = Color(0xFFD2C2AB),
    onSecondary = Color(0xFF3A2F1F),
    secondaryContainer = Color(0xFF53462F),
    onSecondaryContainer = Color(0xFFEFE0C8),
    tertiary = Color(0xFFE9BE93),
    onTertiary = Color(0xFF442A0B),
    tertiaryContainer = Color(0xFF5F3F17),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = NightBase,
    onBackground = Mist,
    surface = NightBase,
    onSurface = Mist,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFBFC8C0),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF28312B),
    outline = NightOutline,
    outlineVariant = Color(0xFF3E4941),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun LeafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LeafTypography,
        content = content
    )
}
