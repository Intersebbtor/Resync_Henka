package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.SavedTheme
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeCode
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.withSavedTheme
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemesTest {
    private val blue = SavedTheme("Night blue", ThemeMode.BLACK, 0xFF4484F6.toInt(), extraColorful = true)

    @Test
    fun codeRoundTrips() {
        val code = ThemeCode.encode(blue)
        assertTrue(code.startsWith(ThemeCode.PREFIX))
        assertTrue(code.none { it.isWhitespace() || it == '=' })
        assertEquals(blue, ThemeCode.decode(code))
    }

    @Test
    fun defaultColorAndEveryModeRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            val theme = SavedTheme("Plain", mode, seedColor = null, extraColorful = false)
            assertEquals(theme, ThemeCode.decode(ThemeCode.encode(theme)))
        }
    }

    @Test
    fun unicodeNamesSurvive() {
        val theme = blue.copy(name = "Thème ✨ 夜")
        assertEquals(theme, ThemeCode.decode(ThemeCode.encode(theme)))
    }

    @Test
    fun codeIsFoundInsideAPastedMessage() {
        val code = ThemeCode.encode(blue)
        assertEquals(blue, ThemeCode.decode("Try my theme!\n  $code  \nthanks"))
        // Without the prefix, and in standard base64 with padding.
        val body = code.removePrefix(ThemeCode.PREFIX)
        assertEquals(blue, ThemeCode.decode(body))
        val json = String(Base64.getUrlDecoder().decode(body), Charsets.UTF_8)
        assertEquals(blue, ThemeCode.decode(ThemeCode.PREFIX + Base64.getEncoder().encodeToString(json.toByteArray())))
    }

    @Test
    fun seedAlwaysComesBackOpaque() {
        val json = """{"v":1,"n":"x","m":"DARK","s":"00ff00","x":false}"""
        val theme = ThemeCode.decode(ThemeCode.PREFIX + Base64.getUrlEncoder().encodeToString(json.toByteArray()))
        assertEquals(0xFF00FF00.toInt(), theme?.seedColor)
    }

    @Test
    fun junkIsRejected() {
        fun code(json: String) = ThemeCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertNull(ThemeCode.decode(""))
        assertNull(ThemeCode.decode("hello there"))
        assertNull(ThemeCode.decode(ThemeCode.PREFIX))
        assertNull(ThemeCode.decode(ThemeCode.PREFIX + "!!!!"))
        assertNull(ThemeCode.decode(code("""{"m":"PURPLE"}""")))
        assertNull(ThemeCode.decode(code("""{"m":"DARK","s":"12345"}""")))
        assertNull(ThemeCode.decode(code("""{"m":"DARK","s":"-12345"}""")))
        assertNull(ThemeCode.decode(code("""{"v":99,"m":"DARK"}""")))
        assertNull(ThemeCode.decode(code("""[1,2,3]""")))
    }

    @Test
    fun longNamesAreCut() {
        val theme = ThemeCode.decode(ThemeCode.encode(blue.copy(name = "n".repeat(500))))
        assertEquals(ThemeCode.MAX_NAME, theme?.name?.length)
    }

    @Test
    fun savingReplacesSameNameInPlace() {
        val a = SavedTheme("A", ThemeMode.DARK)
        val b = SavedTheme("B", ThemeMode.LIGHT)
        val list = withSavedTheme(withSavedTheme(emptyList(), a), b)
        assertEquals(listOf(a, b), list)
        val newA = SavedTheme("a", ThemeMode.BLACK, 0xFF9C27B0.toInt())
        assertEquals(listOf(newA, b), withSavedTheme(list, newA))
    }

    @Test
    fun applyAndMatch() {
        val settings = Settings(dynamicColor = true)
        val applied = blue.applyTo(settings)
        assertEquals(ThemeMode.BLACK, applied.theme)
        assertEquals(blue.seedColor, applied.seedColor)
        assertTrue(applied.extraColorful)
        assertFalse(applied.dynamicColor)
        assertTrue(blue.matches(applied))
        assertFalse(blue.matches(settings))
        assertEquals(blue.copy(name = "Saved"), SavedTheme.from(applied, "  Saved "))
    }

    @Test
    fun dualPaneFollowsOrientationSwitches() {
        val off = Settings()
        assertFalse(off.dualPane(landscape = true, widthDp = 1200))

        val tablet = Settings(tabletMode = true)
        assertTrue(tablet.dualPane(landscape = true, widthDp = 1200))
        assertFalse(tablet.dualPane(landscape = false, widthDp = 800), "portrait is off by default")
        assertFalse(tablet.dualPane(landscape = true, widthDp = Settings.DUAL_PANE_MIN_WIDTH_DP - 1))

        val portrait = tablet.copy(dualPanePortrait = true, dualPaneLandscape = false)
        assertTrue(portrait.dualPane(landscape = false, widthDp = 800))
        assertFalse(portrait.dualPane(landscape = false, widthDp = 411), "a phone in portrait is too narrow")
        assertFalse(portrait.dualPane(landscape = true, widthDp = 1200))
    }
}
