package io.github.radixtrator.resync

import android.content.SharedPreferences
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.data.ExploreStore
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.data.Recent
import io.github.radixtrator.resync.data.VisitedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Just enough SharedPreferences for the stores, which only read and write strings. */
private class FakePrefs : SharedPreferences {
    val values = HashMap<String, Any?>()
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}

class StoresTest {
    @Test
    fun `explore counts visits, keeps recents and survives a reload`() {
        val prefs = FakePrefs()
        val store = ExploreStore(prefs)
        repeat(3) { store.recordVisit("pics") }
        store.recordVisit("Pics") // same feed, any case
        store.recordVisit(null)
        store.recordSearch("secret lair", "magicTCG")
        store.recordVisit("pics") // moves back to the top of recents

        val state = store.state.value
        assertEquals(listOf("pics" to 5, "" to 1), state.mostVisited().map { it.name to it.count })
        assertEquals(
            listOf(Recent(subreddit = "pics"), Recent("magicTCG", "secret lair"), Recent(subreddit = "")),
            state.recents,
        )

        store.toggleFavorite("pics")
        store.setDefault("pics")
        store.setVisible(ExploreSection.MOST_VISITED, false)
        val reloaded = ExploreStore(prefs).state.value
        assertTrue(reloaded.isFavorite("PICS"))
        assertTrue(reloaded.isDefault("pics"))
        assertEquals(setOf(ExploreSection.MOST_VISITED), reloaded.hidden)

        store.setDefault("")
        assertNull(store.state.value.defaultFeed)
        assertTrue(store.state.value.isDefault(""))
        store.toggleFavorite("Pics")
        assertTrue(store.state.value.favorites.isEmpty())
    }

    @Test
    fun `post memory and visited keep the most recent entries`() {
        val prefs = FakePrefs()
        val memory = PostMemoryStore(prefs)
        memory.saveScroll("abc", 12, 340)
        memory.saveCollapsed("abc", setOf("c1"))
        val back = PostMemoryStore(prefs)["abc"]!!
        assertEquals(12, back.index)
        assertEquals(setOf("c1"), back.collapsed)
        assertEquals("abc/c9", PostMemoryStore.key("abc", "c9"))

        val visited = VisitedStore(prefs)
        visited.mark("p1")
        visited.mark("p2")
        assertEquals(setOf("p1", "p2"), VisitedStore(prefs).ids.value)
    }
}
