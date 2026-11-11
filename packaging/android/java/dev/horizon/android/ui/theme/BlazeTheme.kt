package dev.horizon.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BlazePalette(
    val key: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val glow: Color,
    val soft: Color,
    val playStart: Color,
    val playMid: Color,
    val playEnd: Color,
)

object BlazePalettes {
    val Cyan = BlazePalette(
        key = "cyan",
        name = "Cyan",
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFF0284C7),
        glow = Color(0x5938BDF8),
        soft = Color(0x2638BDF8),
        playStart = Color(0xFFE0F2FE),
        playMid = Color(0xFFBAE6FD),
        playEnd = Color(0xFF38BDF8),
    )

    val Green = BlazePalette(
        key = "emerald",
        name = "Green",
        primary = Color(0xFF34D399),
        secondary = Color(0xFF059669),
        glow = Color(0x5934D399),
        soft = Color(0x2634D399),
        playStart = Color(0xFFD1FAE5),
        playMid = Color(0xFF6EE7B7),
        playEnd = Color(0xFF10B981),
    )

    val Red = BlazePalette(
        key = "crimson",
        name = "Red",
        primary = Color(0xFFFB7185),
        secondary = Color(0xFFE11D48),
        glow = Color(0x59FB7185),
        soft = Color(0x26FB7185),
        playStart = Color(0xFFFFE4E6),
        playMid = Color(0xFFFDA4AF),
        playEnd = Color(0xFFF43F5E),
    )

    val Violet = BlazePalette(
        key = "violet",
        name = "Violet",
        primary = Color(0xFFA78BFA),
        secondary = Color(0xFF7C3AED),
        glow = Color(0x59A78BFA),
        soft = Color(0x26A78BFA),
        playStart = Color(0xFFEDE9FE),
        playMid = Color(0xFFC4B5FD),
        playEnd = Color(0xFF8B5CF6),
    )

    val Gold = BlazePalette(
        key = "amber",
        name = "Gold",
        primary = Color(0xFFFBBF24),
        secondary = Color(0xFFD97706),
        glow = Color(0x59FBBF24),
        soft = Color(0x26FBBF24),
        playStart = Color(0xFFFEF3C7),
        playMid = Color(0xFFFDE68A),
        playEnd = Color(0xFFF59E0B),
    )

    val All = listOf(Cyan, Green, Red, Violet, Gold)

    fun fromKey(key: String): BlazePalette = when (key.lowercase()) {
        "emerald", "green" -> Green
        "crimson", "red" -> Red
        "violet" -> Violet
        "amber", "gold" -> Gold
        else -> Cyan
    }
}

@Immutable
data class BlazeColors(
    val background: Color = Color(0xFF05070B),
    val textPrimary: Color = Color(0xFFF1F5F9),
    val textMuted: Color = Color(0xFF94A3B8),
    val glassBg: Color = Color(0xB80F1523),
    val glassBorder: Color = Color(0x26FFFFFF),
    val pillBg: Color = Color(0x1AFFFFFF),
    val pillBorder: Color = Color(0x1AFFFFFF),
    val cardHeroBg: Color = Color(0x990A1120),
    val activePalette: BlazePalette = BlazePalettes.Cyan,
    val cardOpacity: Float = 0.72f,
    val cardBlurDp: Float = 16f,
)

val LocalBlazeColors = staticCompositionLocalOf { BlazeColors() }

@Composable
fun BlazeTheme(
    colors: BlazeColors = BlazeColors(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBlazeColors provides colors,
        content = content
    )
}
