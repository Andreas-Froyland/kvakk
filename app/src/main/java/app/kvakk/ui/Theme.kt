package app.kvakk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.kvakk.R

/** Kvakk's own palette; Material's color scheme is derived from it for stock components. */
@Immutable
data class KvakkColors(
    val isNight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceEdge: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentEdge: Color,
    val onAccent: Color,
    val soft: Color,
    val softEdge: Color,
    val onSoft: Color,
    val success: Color,
    val successEdge: Color,
    val track: Color,
    val bluetooth: Color,
)

val DayColors = KvakkColors(
    isNight = false,
    background = Color(0xFFFFF6E6),
    surface = Color.White,
    surfaceEdge = Color(0xFFF0DCC0),
    text = Color(0xFF24324A),
    textMuted = Color(0xFF7D8596),
    accent = Color(0xFFFF7A00),
    accentEdge = Color(0xFFC95C00),
    onAccent = Color.White,
    soft = Color(0xFFFFE7C2),
    softEdge = Color(0xFFF0CF9C),
    onSoft = Color(0xFF7A4A00),
    success = Color(0xFF22C38E),
    successEdge = Color(0xFF159A6E),
    track = Color(0xFFFFE0B8),
    bluetooth = Color(0xFF3D7BFF),
)

val NightColors = KvakkColors(
    isNight = true,
    background = Color(0xFF161D45),
    surface = Color(0xFF242C5E),
    surfaceEdge = Color(0xFF0D1230),
    text = Color(0xFFF3F1FF),
    textMuted = Color(0xFFA9AED0),
    accent = Color(0xFFFFD88A),
    accentEdge = Color(0xFFC9A35C),
    onAccent = Color(0xFF161D45),
    soft = Color(0xFF323B75),
    softEdge = Color(0xFF1B2250),
    onSoft = Color(0xFFFFD88A),
    success = Color(0xFF3DD6A3),
    successEdge = Color(0xFF1E9C74),
    track = Color(0xFF323B75),
    bluetooth = Color(0xFF8FB4FF),
)

val LocalKvakkColors = staticCompositionLocalOf { DayColors }

private val Nunito = FontFamily(
    listOf(600, 700, 800, 900).map { weight ->
        Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private fun TextStyle.nunito(weight: Int) = copy(fontFamily = Nunito, fontWeight = FontWeight(weight))

private val KvakkTypography = Typography().run {
    copy(
        displayLarge = displayLarge.nunito(900),
        displayMedium = displayMedium.nunito(900),
        displaySmall = displaySmall.nunito(900),
        headlineLarge = headlineLarge.nunito(900),
        headlineMedium = headlineMedium.nunito(900),
        headlineSmall = headlineSmall.nunito(900),
        titleLarge = titleLarge.nunito(900),
        titleMedium = titleMedium.nunito(800),
        titleSmall = titleSmall.nunito(800),
        bodyLarge = bodyLarge.nunito(700),
        bodyMedium = bodyMedium.nunito(700),
        bodySmall = bodySmall.nunito(700),
        labelLarge = labelLarge.nunito(800),
        labelMedium = labelMedium.nunito(800),
        labelSmall = labelSmall.nunito(800),
    )
}

@Composable
fun KvakkTheme(night: Boolean, content: @Composable () -> Unit) {
    val colors = if (night) NightColors else DayColors
    val scheme = if (night) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.soft, onPrimaryContainer = colors.onSoft,
            secondaryContainer = colors.soft, onSecondaryContainer = colors.onSoft,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.track, onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            outline = colors.textMuted, outlineVariant = colors.track,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.soft, onPrimaryContainer = colors.onSoft,
            secondaryContainer = colors.soft, onSecondaryContainer = colors.onSoft,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.track, onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            outline = colors.textMuted, outlineVariant = colors.track,
        )
    }
    CompositionLocalProvider(LocalKvakkColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = KvakkTypography, content = content)
    }
}

/** Screen background: warm cream by day, a starry night sky at bedtime. */
@Composable
fun KvakkBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalKvakkColors.current
    val background = if (colors.isNight) {
        Modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF2D3A7A), Color(0xFF161D45), Color(0xFF0D1230)),
                    center = Offset(size.width * 0.8f, 0f),
                    radius = size.maxDimension,
                ),
            )
            STARS.forEach { (x, y, alpha) ->
                drawCircle(Color.White.copy(alpha = alpha), radius = 1.5f * density, center = Offset(x * size.width, y * size.height))
            }
        }
    } else {
        Modifier.background(colors.background)
    }
    Box(modifier.then(background), content = content)
}

/** Fixed pseudo-random star field: (x, y, alpha) as fractions of the screen. */
private val STARS = List(40) { i ->
    Triple((i * 0.618034f) % 1f, (i * 0.414214f + 0.05f) % 1f, 0.25f + (i % 5) / 8f)
}
