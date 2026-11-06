package io.github.radixtrator.resync.data

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A theme saved in Theme management: the combination of [mode], accent [seedColor] (ARGB, null = Sync's own
 * blue palette) and [extraColorful] that [Settings] applies, under a [name] the user picked.
 */
@Serializable
data class SavedTheme(
    val name: String,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val seedColor: Int? = null,
    val extraColorful: Boolean = false,
) {
    /** Whether this is the combination [settings] are using now (names aside). */
    fun matches(settings: Settings): Boolean =
        mode == settings.theme && seedColor == settings.seedColor && extraColorful == settings.extraColorful

    /** [settings] with this theme applied. Material You is switched off, since it would hide the accent. */
    fun applyTo(settings: Settings): Settings =
        settings.copy(theme = mode, seedColor = seedColor, extraColorful = extraColorful, dynamicColor = false)

    companion object {
        /** The theme [settings] are using now, called [name]. */
        fun from(settings: Settings, name: String) =
            SavedTheme(name.trim(), settings.theme, settings.seedColor, settings.extraColorful)
    }
}

/** [themes] with [theme] added, replacing a saved theme of the same name (ignoring case) in place. */
fun withSavedTheme(themes: List<SavedTheme>, theme: SavedTheme): List<SavedTheme> {
    val at = themes.indexOfFirst { it.name.equals(theme.name, ignoreCase = true) }
    return if (at < 0) themes + theme else themes.toMutableList().also { it[at] = theme }
}

/**
 * Sync's "Share theme" / "Load theme": a theme as one line of text to paste anywhere. It's [PREFIX] then
 * URL-safe base64 of a small JSON object, so it survives chat apps that mangle quotes and braces.
 */
object ThemeCode {
    const val PREFIX = "resync-theme:"
    private const val VERSION = 1

    /** The JSON inside a code: short keys keep the code short. The seed is "RRGGBB" so it reads plainly when decoded. */
    @Serializable
    private data class Payload(
        val v: Int = VERSION,
        val n: String = "",
        val m: String = ThemeMode.SYSTEM.name,
        val s: String? = null,
        val x: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(theme: SavedTheme): String {
        val payload = Payload(
            n = theme.name,
            m = theme.mode.name,
            s = theme.seedColor?.let { "%06X".format(it and 0xFFFFFF) },
            x = theme.extraColorful,
        )
        val bytes = json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * The theme in [text], or null if it holds none. [text] may be a whole pasted message: the code is found
     * after [PREFIX] wherever it is, and a bare code without the prefix is accepted too. Standard and URL-safe
     * base64, with or without padding, both decode.
     */
    fun decode(text: String): SavedTheme? {
        val trimmed = text.trim()
        val start = trimmed.indexOf(PREFIX, ignoreCase = true)
        val body = (if (start >= 0) trimmed.substring(start + PREFIX.length) else trimmed)
            .takeWhile { it.isLetterOrDigit() || it in "-_+/=" }
            .trimEnd('=')
            .replace('+', '-')
            .replace('/', '_')
        if (body.isEmpty()) return null
        val payload = runCatching {
            json.decodeFromString(Payload.serializer(), String(Base64.getUrlDecoder().decode(body), Charsets.UTF_8))
        }.getOrNull() ?: return null
        if (payload.v > VERSION) return null
        val mode = ThemeMode.entries.firstOrNull { it.name == payload.m } ?: return null
        val seed = payload.s?.let { hex ->
            if (hex.length != 6 || !hex.all { it in '0'..'9' || it.uppercaseChar() in 'A'..'F' }) return null
            (hex.toIntOrNull(16) ?: return null) or 0xFF000000.toInt()
        }
        return SavedTheme(name = payload.n.trim().take(MAX_NAME), mode = mode, seedColor = seed, extraColorful = payload.x)
    }

    /** Longest theme name kept from a loaded code. */
    const val MAX_NAME = 40
}
