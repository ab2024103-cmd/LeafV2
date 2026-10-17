package com.leaf.reader.reader

import androidx.compose.ui.graphics.Color
import com.leaf.reader.model.HighlightColor
import com.leaf.reader.model.ReadingTheme

/** Colours for the reading canvas (spec 3.10 / 3.11). */
data class ReaderColors(
    val background: Color,
    val text: Color,
    val rule: Color,
    val link: Color,
    val selection: Color,
    val toolbar: Color,
    val tints: Map<HighlightColor, Color> = emptyMap()
) {
    fun tint(color: HighlightColor): Color = tints[color] ?: color.tint
}

private val paperTints = mapOf(
    HighlightColor.YELLOW to Color(0xFFF3D06B),
    HighlightColor.GREEN to Color(0xFFA9D8A9),
    HighlightColor.BLUE to Color(0xFFA9C7E8),
    HighlightColor.PINK to Color(0xFFF0B6C6),
    HighlightColor.ORANGE to Color(0xFFF3BE8A)
)

private val sepiaTints = mapOf(
    HighlightColor.YELLOW to Color(0xFFD9B24C),
    HighlightColor.GREEN to Color(0xFF93B87A),
    HighlightColor.BLUE to Color(0xFF7FA3C4),
    HighlightColor.PINK to Color(0xFFD89BA6),
    HighlightColor.ORANGE to Color(0xFFC98F4E)
)

private val nightTints = mapOf(
    HighlightColor.YELLOW to Color(0xFF6B5A1E),
    HighlightColor.GREEN to Color(0xFF2F5A35),
    HighlightColor.BLUE to Color(0xFF244A6B),
    HighlightColor.PINK to Color(0xFF5E2A3C),
    HighlightColor.ORANGE to Color(0xFF6B3D1C)
)

private val oledTints = mapOf(
    HighlightColor.YELLOW to Color(0xFF7A6620),
    HighlightColor.GREEN to Color(0xFF34603A),
    HighlightColor.BLUE to Color(0xFF27567D),
    HighlightColor.PINK to Color(0xFF6E2F45),
    HighlightColor.ORANGE to Color(0xFF7A4620)
)

fun readerColors(theme: ReadingTheme, systemDark: Boolean): ReaderColors {
    val resolved = when (theme) {
        ReadingTheme.AUTO -> if (systemDark) ReadingTheme.NIGHT else ReadingTheme.PAPER
        else -> theme
    }
    return when (resolved) {
        ReadingTheme.SEPIA -> ReaderColors(
            background = Color(0xFFF3E5C8),
            text = Color(0xFF46371F),
            rule = Color(0xFFD8C39B),
            link = Color(0xFF7A4E20),
            selection = Color(0x33805A22),
            toolbar = Color(0xFFEBDCC0),
            tints = sepiaTints
        )

        ReadingTheme.NIGHT -> ReaderColors(
            background = Color(0xFF161B18),
            text = Color(0xFFD8E0DA),
            rule = Color(0xFF2A332D),
            link = Color(0xFF7FC8A6),
            selection = Color(0x557FC8A6),
            toolbar = Color(0xFF1E2621),
            tints = nightTints
        )

        ReadingTheme.OLED -> ReaderColors(
            background = Color(0xFF000000),
            text = Color(0xFFC8D2CB),
            rule = Color(0xFF1A1A1A),
            link = Color(0xFF6FD3A8),
            selection = Color(0x556FD3A8),
            toolbar = Color(0xFF0B0B0B),
            tints = oledTints
        )

        else -> ReaderColors(
            background = Color(0xFFFBF8F2),
            text = Color(0xFF1D241F),
            rule = Color(0xFFE1D9C8),
            link = Color(0xFF2B7A5B),
            selection = Color(0x332B7A5B),
            toolbar = Color(0xFFF3EEE3),
            tints = paperTints
        )
    }
}
