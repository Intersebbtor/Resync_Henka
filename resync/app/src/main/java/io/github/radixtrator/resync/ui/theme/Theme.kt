package io.github.radixtrator.resync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF4F5B6E),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF15181D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFBFCFE),
    surfaceVariant = Color(0xFFE3E7EF),
    onSurfaceVariant = Color(0xFF5A6270),
    outlineVariant = Color(0xFFDDE1E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF16457F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EC),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E6EC),
    surfaceContainerLowest = Color(0xFF14181E),
    surfaceContainerLow = Color(0xFF181C23),
    surfaceContainer = Color(0xFF1C2028),
    surfaceVariant = Color(0xFF2A303A),
    onSurfaceVariant = Color(0xFFA9B1BE),
    outlineVariant = Color(0xFF2C323C),
)

object ResyncColors {
    val Upvote = Color(0xFFFF6A33)
    val Downvote = Color(0xFF7193FF)
    val Nsfw = Color(0xFFE5484D)
    val Moderator = Color(0xFF2EA043)
    val Saved = Color(0xFFF2B01E)

    /** Comment thread rails, cycling by depth. */
    val Depth = listOf(
        Color(0xFF3B82F6),
        Color(0xFF22C55E),
        Color(0xFFF59E0B),
        Color(0xFFEF4444),
        Color(0xFFA855F7),
        Color(0xFF14B8A6),
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
    MaterialTheme(
        colorScheme = if (settings.theme == ThemeMode.BLACK) base.blackened() else base,
        content = content,
    )
}
