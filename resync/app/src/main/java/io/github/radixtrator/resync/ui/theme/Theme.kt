package io.github.radixtrator.resync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeMode

// Sync builds its theme from Monet tonal palettes (N1/N2 neutrals, A1–A3 accents). These are the tones
// Sync showed on the reference device (sync-reference/SYNC_PARITY_LOG.md §1), mapped onto M3 roles the
// way Sync uses them: window, cards/sheets, nav bar and selection pills are successive neutral steps.
private val LightColors = lightColorScheme(
    primary = Color(0xFF505E81), // A1-600
    onPrimary = Color.White,
    primaryContainer = Color(0xFF505E81), // Sync's FAB is the saturated accent in light mode too
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF575E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC0C6DC), // A2-200: nav indicator
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF6A567F),
    tertiaryContainer = Color(0xFFEFDBFF),
    background = Color(0xFFEEEDF3),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFEEEDF3),
    onSurface = Color(0xFF1A1B20),
    surfaceContainerLowest = Color(0xFFEEEDF3),
    surfaceContainerLow = Color(0xFFFEFBFF), // cards, sheets, chips
    surfaceContainer = Color(0xFFE1E2ED), // bottom nav
    surfaceContainerHigh = Color(0xFFE3E2E7),
    surfaceContainerHighest = Color(0xFFDCDBE2),
    surfaceVariant = Color(0xFFF1F0F6), // neutral flair chips
    onSurfaceVariant = Color(0xFF5C5E67),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DABD1), // A1-300: Sync's accent text (sub names, links, headers)
    onPrimary = Color(0xFF0B1A3A),
    primaryContainer = Color(0xFF394668), // A1-700: FAB
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF454957), // nav indicator
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFD5BDEC),
    tertiaryContainer = Color(0xFF513F66),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE6E5EC),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFE6E5EC),
    surfaceContainerLowest = Color(0xFF121418),
    surfaceContainerLow = Color(0xFF1C1D21), // cards, sheets, chips
    surfaceContainer = Color(0xFF27292D), // bottom nav
    surfaceContainerHigh = Color(0xFF2D2F3A), // selected rows, highlighted comment
    surfaceContainerHighest = Color(0xFF34363E),
    surfaceVariant = Color(0xFF282A2E), // neutral flair chips
    onSurfaceVariant = Color(0xFFA3A5AD),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFF2E3038),
)

object ResyncColors {
    val Upvote = Color(0xFFFF7500)
    val Downvote = Color(0xFF9494FF)
    val Nsfw = Color(0xFFE5484D)
    val Moderator = Color(0xFF4CAF50)
    val Saved = Color(0xFFECC940)
    val Reply = Color(0xFF3ABF6D)
    val Hide = Color(0xFFB71C1C)

    /** Comment thread rails, cycling by depth (Sync's order: blue, purple, green, orange, pink). */
    val Depth = listOf(
        Color(0xFF4484F6),
        Color(0xFF9C27B0),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFFE91E63),
    )
}

/**
 * Sync bundles Roboto and draws everything in it rather than the device font (One UI's is wider, and
 * drew italics upright). res/font/roboto.ttf is Android's variable Roboto: one file, every weight.
 */
private val Roboto = FontFamily(
    (100..900 step 100).flatMap { weight ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
            Font(R.font.roboto, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontWeight(weight), style))
        }
    },
)

/**
 * Sync's TextViews set Roboto about 1.5% wider than Compose does at the same size (titles, bodies and the
 * 34sp header all measure so in sync-reference), so this stands in for Material's own letter spacing.
 */
private val SYNC_LETTER_SPACING = 0.015.em

/** Material's roles in Roboto, spaced like Sync's text. */
private val ResyncTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        displayMedium = displayMedium.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        displaySmall = displaySmall.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        headlineLarge = headlineLarge.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        headlineMedium = headlineMedium.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        headlineSmall = headlineSmall.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        titleLarge = titleLarge.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        titleMedium = titleMedium.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        titleSmall = titleSmall.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        bodyLarge = bodyLarge.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        bodyMedium = bodyMedium.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        bodySmall = bodySmall.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        labelLarge = labelLarge.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        labelMedium = labelMedium.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
        labelSmall = labelSmall.copy(fontFamily = Roboto, letterSpacing = SYNC_LETTER_SPACING),
    )
}

/** Pure black surfaces for OLED screens; cards stay a hair lighter so they still read as cards. */
private fun ColorScheme.blackened() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = Color(0xFF111316),
    outlineVariant = Color(0xFF1F2329),
)

/**
 * Sync's "Base font size" is "Smaller" by default on phones, and it shrinks every sp in the app by this
 * much: its 14sp rows and bodies, 16sp titles and 20sp sheet headers all measure at 0.9x the device's
 * font scale in sync-reference. MainActivity applies it to its configuration, so sheets, menus and
 * dialogs (their own windows) get it too. Sync's settings screen keeps full-size text; see [FullSizeText].
 */
const val BASE_FONT_SCALE = 0.9f

/** [content] at the device's own font scale, without [BASE_FONT_SCALE], like Sync's settings. */
@Composable
fun FullSizeText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale / BASE_FONT_SCALE), content = content)
}

@Composable
fun ResyncTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (settings.theme == ThemeMode.BLACK) base.blackened() else base,
            typography = ResyncTypography,
            content = content,
        )
    }
}

/** Whether the resolved theme is dark, for actions that flip it (Sync's "Dark mode" quick action). */
val LocalDarkTheme = staticCompositionLocalOf { false }
