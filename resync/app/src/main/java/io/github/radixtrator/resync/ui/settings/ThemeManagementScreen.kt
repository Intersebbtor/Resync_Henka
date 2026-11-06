@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.SavedTheme
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeCode
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.withSavedTheme
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.theme.LocalDarkTheme
import io.github.radixtrator.resync.ui.theme.THEME_SEEDS
import io.github.radixtrator.resync.ui.theme.resyncColorScheme
import kotlin.math.roundToInt

/**
 * Sync's Theme management (Settings › Appearance): light and dark mini-phone previews of the theme in use, Sync's
 * Automatic / Manual selection buttons (Automatic is Material You where Android has it, else the default palette),
 * then the mode and accent pickers (applied at once, like Sync's), Color intensity and Extra colorful for a picked
 * accent, Dark tweaks' Dark overlay, the user's saved themes (tap to apply, share, delete), and Sync's Extra rows
 * "View palette", "Share theme" / "Load theme", which pass a theme around as one line of text ([ThemeCode]). Sync's
 * text colour overrides aren't here.
 */
@Composable
fun ThemeManagementScreen(container: AppContainer) {
    val nav = LocalNavigator.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val update = container.settings::update
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemDark = isSystemInDarkTheme()
    var saving by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedTheme?>(null) }
    var viewingPalette by rememberSaveable { mutableStateOf(false) }

    SwipeBack(enabled = settings.swipeBack, onBack = nav::pop) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Text("Theme management") },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                // Sync's ThemePreviewPreference: the theme in light and dark side by side.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    MiniPhone(previewScheme(settings, dark = false), "Light", Modifier.weight(1f))
                    MiniPhone(previewScheme(settings, dark = true), if (settings.theme == ThemeMode.BLACK) "Black" else "Dark", Modifier.weight(1f))
                }
                // Sync's Automatic / Manual selection buttons. Automatic is Material You where Android has it, else
                // Resync's default palette; Manual is a hand-picked accent (the swatches below), with Color intensity.
                val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val manual = !dynamic && settings.seedColor != null
                val automatic = if (canDynamic) dynamic else settings.seedColor == null
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = automatic,
                        onClick = { update { it.copy(dynamicColor = canDynamic, seedColor = null) } },
                        label = { Text("Automatic") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = manual,
                        onClick = { update { it.copy(dynamicColor = false, seedColor = it.seedColor ?: THEME_SEEDS.first()) } },
                        label = { Text("Manual selection") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    when {
                        dynamic -> "Following your wallpaper's colors (Material You). Pick a color below to use it instead."
                        manual -> "Using the color you picked below."
                        canDynamic -> "Using Resync's default palette. Automatic follows your wallpaper's colors instead."
                        else -> "Using Resync's default palette: this Android version has no wallpaper colors."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Section("Mode")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { update { it.copy(theme = mode) } },
                            label = { Text(mode.label) },
                        )
                    }
                }

                Section("Theme color")
                val swatchDark = LocalDarkTheme.current
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    (listOf<Int?>(null) + THEME_SEEDS).forEach { seed ->
                        ThemeSwatch(
                            scheme = resyncColorScheme(swatchDark, black = settings.theme == ThemeMode.BLACK, seed = seed, extra = settings.extraColorful, intensity = settings.colorIntensity),
                            selected = !dynamic && settings.seedColor == seed,
                            description = seed?.let { "Theme color ${hex(it)}" } ?: "Default theme color",
                            onClick = { update { it.copy(seedColor = seed, dynamicColor = false) } },
                        )
                    }
                }
                if (manual) {
                    // Sync's Manual setup › Color intensity: 0–2 in steps of 0.2.
                    ThemeValueSlider(
                        title = "Color intensity",
                        valueLabel = "%.1f×".format(settings.colorIntensity),
                        value = settings.colorIntensity,
                        range = 0f..Settings.COLOR_INTENSITY_MAX,
                        steps = 9,
                    ) { v -> update { it.copy(colorIntensity = v) } }
                    Toggle("Extra colorful", "Tint backgrounds and cards with the accent too", settings.extraColorful) { v -> update { it.copy(extraColorful = v) } }
                }

                Section("Dark tweaks")
                DarkOverlayRows(settings, update)

                Section("Saved themes")
                if (settings.savedThemes.isEmpty()) {
                    ListItem(
                        headlineContent = { Text("None yet") },
                        supportingContent = { Text("Save the theme you're using to switch back to it later") },
                    )
                }
                settings.savedThemes.forEach { theme ->
                    val inUse = !dynamic && theme.matches(settings)
                    ListItem(
                        leadingContent = {
                            val dark = when (theme.mode) {
                                ThemeMode.SYSTEM -> systemDark
                                ThemeMode.LIGHT -> false
                                ThemeMode.DARK, ThemeMode.BLACK -> true
                            }
                            ThemeSwatch(
                                scheme = resyncColorScheme(dark, theme.mode == ThemeMode.BLACK, theme.seedColor, theme.extraColorful),
                                selected = inUse,
                                description = null,
                                onClick = null,
                            )
                        },
                        headlineContent = { Text(theme.name) },
                        supportingContent = { Text(describe(theme) + if (inUse) " · In use" else "") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { nav.share(ThemeCode.encode(theme), theme.name) }) {
                                    Icon(Icons.Outlined.Share, "Share ${theme.name}")
                                }
                                IconButton(onClick = { deleting = theme }) { Icon(Icons.Outlined.Delete, "Delete ${theme.name}") }
                            }
                        },
                        modifier = Modifier.clickable { update { theme.applyTo(it) } },
                    )
                }
                ListItem(
                    headlineContent = { Text("Save current theme") },
                    supportingContent = { Text(describe(SavedTheme.from(settings, ""))) },
                    modifier = Modifier.clickable { saving = true },
                )

                Section("Extra")
                ListItem(
                    headlineContent = { Text("View palette") },
                    supportingContent = { Text("The colors the theme in use is built from") },
                    modifier = Modifier.clickable { viewingPalette = true },
                )
                ListItem(
                    headlineContent = { Text("Share theme") },
                    supportingContent = { Text("Send the theme you're using as a code to paste") },
                    modifier = Modifier.clickable {
                        val name = settings.savedThemes.firstOrNull { it.matches(settings) }?.name ?: "Resync theme"
                        nav.share(ThemeCode.encode(SavedTheme.from(settings, name)), name)
                    },
                )
                ListItem(
                    headlineContent = { Text("Load theme") },
                    supportingContent = { Text("Paste a shared theme code") },
                    modifier = Modifier.clickable { loading = true },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (saving) {
        var name by rememberSaveable { mutableStateOf("Theme ${settings.savedThemes.size + 1}") }
        val replaces = settings.savedThemes.any { it.name.equals(name.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save theme") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ThemeCode.MAX_NAME) },
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = if (replaces) ({ Text("Replaces the saved theme with this name") }) else null,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        update { it.copy(savedThemes = withSavedTheme(it.savedThemes, SavedTheme.from(it, name))) }
                        saving = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }

    if (loading) {
        var code by rememberSaveable { mutableStateOf("") }
        val theme = remember(code) { ThemeCode.decode(code) }
        AlertDialog(
            onDismissRequest = { loading = false },
            title = { Text("Load theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Theme code") },
                        placeholder = { Text("${ThemeCode.PREFIX}…") },
                        isError = code.isNotBlank() && theme == null,
                        supportingText = if (code.isNotBlank() && theme == null) ({ Text("That isn't a theme code") }) else null,
                        maxLines = 4,
                    )
                    if (theme != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val dark = when (theme.mode) {
                                ThemeMode.SYSTEM -> systemDark
                                ThemeMode.LIGHT -> false
                                ThemeMode.DARK, ThemeMode.BLACK -> true
                            }
                            ThemeSwatch(resyncColorScheme(dark, theme.mode == ThemeMode.BLACK, theme.seedColor, theme.extraColorful), false, null, null)
                            Column {
                                Text(theme.name.ifBlank { "Unnamed theme" }, style = MaterialTheme.typography.titleSmall)
                                Text(describe(theme), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val loaded = theme ?: return@TextButton
                        val named = if (loaded.name.isBlank()) loaded.copy(name = "Loaded theme") else loaded
                        update { named.applyTo(it).copy(savedThemes = withSavedTheme(it.savedThemes, named)) }
                        loading = false
                    },
                    enabled = theme != null,
                ) { Text("Apply and save") }
            },
            dismissButton = { TextButton(onClick = { loading = false }) { Text("Cancel") } },
        )
    }

    deleting?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${theme.name}?") },
            text = { Text("The theme you're using stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    update { s -> s.copy(savedThemes = s.savedThemes.filterNot { it.name == theme.name }) }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (viewingPalette) {
        // Sync's "Visualise monet palette": here, the main roles of the scheme the app is drawn with right now.
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { viewingPalette = false },
            title = { Text("Palette") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    paletteRoles(scheme).forEach { (name, color) -> PaletteRow(name, color) }
                }
            },
            confirmButton = { TextButton(onClick = { viewingPalette = false }) { Text("Close") } },
        )
    }
}

/** The color roles the palette view lists, in the order Material describes them. */
private fun paletteRoles(scheme: ColorScheme): List<Pair<String, Color>> = listOf(
    "Primary" to scheme.primary,
    "On primary" to scheme.onPrimary,
    "Primary container" to scheme.primaryContainer,
    "On primary container" to scheme.onPrimaryContainer,
    "Secondary" to scheme.secondary,
    "Secondary container" to scheme.secondaryContainer,
    "Tertiary" to scheme.tertiary,
    "Tertiary container" to scheme.tertiaryContainer,
    "Background" to scheme.background,
    "Surface" to scheme.surface,
    "Surface container low" to scheme.surfaceContainerLow,
    "Surface container" to scheme.surfaceContainer,
    "Surface container high" to scheme.surfaceContainerHigh,
    "Surface container highest" to scheme.surfaceContainerHighest,
    "Surface variant" to scheme.surfaceVariant,
    "On surface" to scheme.onSurface,
    "On surface variant" to scheme.onSurfaceVariant,
    "Outline" to scheme.outline,
    "Outline variant" to scheme.outlineVariant,
)

/** One palette swatch: the color in a rounded square, its role and its hex value. */
@Composable
private fun PaletteRow(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(hex(color.toArgb()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Sync's "Dark overlay" switch, and with it on, how dark it makes the app. Shared by Theme management's Dark tweaks
 * and Settings › Appearance.
 */
@Composable
internal fun DarkOverlayRows(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle("Dark overlay", "Dim the whole app, for reading at night", settings.darkOverlay) { v -> update { it.copy(darkOverlay = v) } }
    if (settings.darkOverlay) {
        ThemeValueSlider(
            title = "Overlay darkness",
            valueLabel = "${(settings.darkOverlayLevel * 100).roundToInt()}%",
            value = settings.darkOverlayLevel,
            range = Settings.DARK_OVERLAY_MIN..Settings.DARK_OVERLAY_MAX,
            steps = 11,
        ) { v -> update { it.copy(darkOverlayLevel = v) } }
    }
}

/** A titled slider with its value on the right, like Sync's MonetSlider rows. [steps] are the stops between the ends. */
@Composable
private fun ThemeValueSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

/** The preview for one side: Resync's palette for [settings], black in dark when the mode is Black. */
private fun previewScheme(settings: Settings, dark: Boolean): ColorScheme =
    resyncColorScheme(dark, black = settings.theme == ThemeMode.BLACK, seed = settings.seedColor, extra = settings.extraColorful, intensity = settings.colorIntensity)

private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

/** "Dark · #4484F6 · Extra colorful". */
private fun describe(theme: SavedTheme): String = buildList {
    add(theme.mode.label)
    add(theme.seedColor?.let(::hex) ?: "Default color")
    if (theme.seedColor != null && theme.extraColorful) add("Extra colorful")
}.joinToString(" · ")

/** A round swatch: the theme's window color with its accent in the middle, ringed when [selected]. */
@Composable
private fun ThemeSwatch(scheme: ColorScheme, selected: Boolean, description: String?, onClick: (() -> Unit)?, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.background)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClickLabel = description, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * 0.45f).clip(CircleShape).background(scheme.primary))
    }
}

/**
 * A phone-shaped sketch of a feed in [scheme]: toolbar title, two cards with an accent line, the FAB and the
 * bottom bar with its selected pill. Drawn with plain shapes so it costs nothing to show two.
 */
@Composable
private fun MiniPhone(scheme: ColorScheme, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.55f)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar(scheme.onBackground, widthFraction = 0.5f, height = 8.dp)
                repeat(2) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(scheme.surfaceContainerLow).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Bar(scheme.primary, widthFraction = 0.4f)
                        Bar(scheme.onSurface, widthFraction = 0.9f)
                        Bar(scheme.onSurfaceVariant, widthFraction = 0.6f)
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 32.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.primaryContainer),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp).background(scheme.surfaceContainer),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(20.dp).height(10.dp).clip(CircleShape).background(scheme.secondaryContainer))
                Box(Modifier.size(6.dp).clip(CircleShape).background(scheme.onSurfaceVariant))
                Box(Modifier.size(6.dp).clip(CircleShape).background(scheme.onSurfaceVariant))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A rounded line standing in for text. */
@Composable
private fun Bar(color: Color, widthFraction: Float, height: Dp = 5.dp) {
    Box(Modifier.fillMaxWidth(widthFraction).height(height).clip(CircleShape).background(color.copy(alpha = 0.8f)))
}
