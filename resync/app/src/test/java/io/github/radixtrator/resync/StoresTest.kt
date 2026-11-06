package io.github.radixtrator.resync

import android.content.SharedPreferences
import io.github.radixtrator.resync.data.Draft
import io.github.radixtrator.resync.data.DraftStore
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.data.ExploreStore
import io.github.radixtrator.resync.data.FilterStore
import io.github.radixtrator.resync.data.InboxItem
import io.github.radixtrator.resync.data.NotifiedMessages
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.data.Recent
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.UserTags
import io.github.radixtrator.resync.data.VisitedStore
import io.github.radixtrator.resync.data.WatchStore
import io.github.radixtrator.resync.data.WatchedCommunity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

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
        assertEquals(setOf(ExploreSection.FEEDS, ExploreSection.MOST_VISITED), reloaded.hidden)

        store.setDefault("")
        assertNull(store.state.value.defaultFeed)
        assertTrue(store.state.value.isDefault(""))
        store.toggleFavorite("Pics")
        assertTrue(store.state.value.favorites.isEmpty())
    }

    @Test
    fun `user tags ignore case, drop blanks and survive a reload`() {
        val prefs = FakePrefs()
        val tags = UserTags(prefs)
        tags.set("Spez", "  admin  ")
        assertEquals("admin", UserTags(prefs).tagOf("spez"))
        tags.set("spez", " ")
        assertNull(UserTags(prefs).tagOf("SPEZ"))
    }

    @Test
    fun `drafts keep the newest first, drop blanks and survive a reload`() {
        val prefs = FakePrefs()
        var now = 100L
        val drafts = DraftStore(prefs) { now }
        drafts.save("reply:t1_a", "hello", "Reply")
        now = 200
        drafts.save("submit", "post body", "Post")
        assertEquals(listOf("submit", "reply:t1_a"), DraftStore(prefs).drafts.value.map { it.key })

        now = 300
        drafts.save("reply:t1_a", "hello there", "Reply")
        val reloaded = DraftStore(prefs)
        assertEquals(listOf("reply:t1_a", "submit"), reloaded.drafts.value.map { it.key })
        assertEquals(Draft("reply:t1_a", "hello there", "Reply", 300), reloaded.get("reply:t1_a"))

        // Saving the same text again doesn't bump it.
        now = 400
        drafts.save("submit", "post body", "Post")
        assertEquals(200L, drafts.get("submit")?.savedAt)

        drafts.save("submit", "  ")
        assertNull(DraftStore(prefs).get("submit"))
        drafts.clear("reply:t1_a")
        assertTrue(DraftStore(prefs).drafts.value.isEmpty())
    }

    @Test
    fun `filters hide users and domains everywhere, communities outside themselves`() {
        fun post(sub: String, author: String, domain: String) = Parser.post(
            RedditClient.JSON.parseToJsonElement("""{"id":"x","title":"t","subreddit":"$sub","author":"$author","domain":"$domain"}""").jsonObject,
        )
        val store = FilterStore(FakePrefs())
        store.filterSubreddit("Pics")
        store.filterUser("Spammer")
        store.filterDomain("Example.com")
        val filters = store.filters.value
        assertTrue(filters.hides(post("pics", "a", "i.redd.it"), feedCommunity = null))
        assertTrue(!filters.hides(post("pics", "a", "i.redd.it"), feedCommunity = "PICS"))
        assertTrue(filters.hides(post("gifs", "spammer", "i.redd.it"), feedCommunity = "gifs"))
        assertTrue(filters.hides(post("gifs", "a", "example.com"), feedCommunity = "gifs"))
        store.filterUser("spammer", on = false)
        assertTrue(!store.filters.value.hides(post("gifs", "Spammer", "i.redd.it"), feedCommunity = null))
    }

    @Test
    fun `filters hide keywords in titles and exact flairs`() {
        fun post(title: String, flair: String?) = Parser.post(
            RedditClient.JSON.parseToJsonElement(
                """{"id":"x","title":"$title","subreddit":"s","author":"a","domain":"d"${flair?.let { ""","link_flair_text":"$it"""" } ?: ""}}""",
            ).jsonObject,
        )
        val store = FilterStore(FakePrefs())
        store.filterKeyword("  Spoiler Alert ")
        store.filterFlair("Meme")
        val filters = store.filters.value
        assertTrue(filters.hides(post("Big SPOILER ALERT inside", null), feedCommunity = null))
        assertTrue(filters.hides(post("hello", "meme"), feedCommunity = "s"))
        assertTrue(!filters.hides(post("hello", "memes"), feedCommunity = null))
        store.filterKeyword("spoiler alert", on = false)
        assertTrue(!store.filters.value.hides(post("spoiler alert", null), feedCommunity = null))
    }

    @Test
    fun `watched posts remember their comment count`() {
        val prefs = FakePrefs()
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"t","subreddit":"pics","num_comments":5}""").jsonObject)
        WatchStore(prefs).watchPost(post)
        val store = WatchStore(prefs)
        assertEquals(5, store.posts.value["p1"]?.comments)
        store.markComments("p1", 9)
        store.markComments("p1", 7)
        assertEquals(9, WatchStore(prefs).posts.value["p1"]?.comments)
        store.unwatchPost("p1")
        assertTrue(WatchStore(prefs).isEmpty)
    }

    @Test
    fun `each unread message is announced once`() {
        fun item(id: String) = InboxItem(
            id = id, fullname = "t4_$id", isComment = false, author = "a", dest = "me", subject = "s", postTitle = null,
            subreddit = null, body = "b", bodyHtml = "", createdUtc = 0, isNew = true, postId = null, commentId = null,
        )
        val prefs = FakePrefs()
        val notified = NotifiedMessages(prefs)
        assertEquals(listOf("t4_1", "t4_2"), notified.unseen(listOf(item("1"), item("2"))).map { it.fullname })
        notified.remember(listOf("t4_1", "t4_2"))
        assertEquals(listOf("t4_3"), NotifiedMessages(prefs).unseen(listOf(item("1"), item("2"), item("3"))).map { it.fullname })
    }

    @Test
    fun `watched communities only move forward and survive a reload`() {
        val prefs = FakePrefs()
        val store = WatchStore(prefs)
        store.watch("Pics", since = 100)
        assertTrue(store.isWatching("pics"))
        store.markSeen("pics", 150)
        store.markSeen("PICS", 120) // an older post never rewinds the mark
        assertEquals(WatchedCommunity("Pics", 150), WatchStore(prefs).watched.value["pics"])
        store.unwatch("pics")
        assertTrue(WatchStore(prefs).watched.value.isEmpty())
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

class FavoriteOrderTest {
    @org.junit.Test
    fun `moves favorites`() {
        val store = ExploreStore(FakePrefs())
        store.toggleFavorite("a")
        store.toggleFavorite("b")
        store.toggleFavorite("c")
        store.moveFavorite("c", -1)
        org.junit.Assert.assertEquals(listOf("a", "c", "b"), store.state.value.favorites)
        store.moveFavorite("a", -1)
        org.junit.Assert.assertEquals(listOf("a", "c", "b"), store.state.value.favorites)
    }
}
