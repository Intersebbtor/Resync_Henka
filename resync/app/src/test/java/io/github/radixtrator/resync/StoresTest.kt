package io.github.radixtrator.resync

import android.content.SharedPreferences
import io.github.radixtrator.resync.data.Account
import io.github.radixtrator.resync.data.AccountStore
import io.github.radixtrator.resync.data.ApiConfig
import io.github.radixtrator.resync.data.ApiConfigStore
import io.github.radixtrator.resync.data.Backup
import io.github.radixtrator.resync.data.BackupFormatException
import io.github.radixtrator.resync.data.Draft
import io.github.radixtrator.resync.data.DraftStore
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.data.ExploreStore
import io.github.radixtrator.resync.data.FilterStore
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.data.InboxItem
import io.github.radixtrator.resync.data.NotifiedMessages
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.data.Recent
import io.github.radixtrator.resync.data.RecentPost
import io.github.radixtrator.resync.data.RecentPostsStore
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.SettingsStore
import io.github.radixtrator.resync.data.UserTags
import io.github.radixtrator.resync.data.VisitedStore
import io.github.radixtrator.resync.data.WatchStore
import io.github.radixtrator.resync.data.WatchedCommunity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

/** Just enough SharedPreferences for the stores: strings, plus the booleans and longs ApiConfigStore and AccountStore keep. */
private class FakePrefs : SharedPreferences {
    val values = HashMap<String, Any?>()
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
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
    fun `filter editor entries are normalized, removable and clearable per list`() {
        assertEquals("pics", FilterType.SUBREDDITS.normalize(" /r/Pics/ "))
        assertEquals("spez", FilterType.USERS.normalize("u/Spez"))
        assertEquals("spez", FilterType.USERS.normalize("/user/spez"))
        assertEquals("example.com", FilterType.DOMAINS.normalize("https://www.Example.com/a/b?c=d"))
        assertEquals("i.redd.it", FilterType.DOMAINS.normalize("i.redd.it"))
        assertEquals("spoiler alert", FilterType.KEYWORDS.normalize("  Spoiler Alert "))
        assertEquals("r/meta", FilterType.FLAIRS.normalize("R/Meta"))
        assertEquals("r/pics", FilterType.SUBREDDITS.describe("pics"))
        assertEquals("\"word\"", FilterType.KEYWORDS.describe("word"))

        val prefs = FakePrefs()
        val store = FilterStore(prefs)
        store.set(FilterType.SUBREDDITS, "r/Pics")
        store.set(FilterType.SUBREDDITS, "gifs")
        store.set(FilterType.DOMAINS, "http://www.youtube.com/watch?v=x")
        store.set(FilterType.USERS, "   ")
        assertEquals(setOf("pics", "gifs"), FilterType.SUBREDDITS.of(store.filters.value))
        assertEquals(setOf("youtube.com"), FilterType.DOMAINS.of(FilterStore(prefs).filters.value))
        assertTrue(FilterType.USERS.of(store.filters.value).isEmpty())
        store.remove(FilterType.SUBREDDITS, "pics")
        assertEquals(setOf("gifs"), store.filters.value.subreddits)
        store.clear(FilterType.SUBREDDITS)
        assertTrue(store.filters.value.subreddits.isEmpty())
        assertEquals(setOf("youtube.com"), FilterStore(prefs).filters.value.domains)
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

    @Test
    fun `recent posts put the latest first, move reopened ones up and keep fifty`() {
        val prefs = FakePrefs()
        val store = RecentPostsStore(prefs)
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"Hello","subreddit":"pics"}""").jsonObject)
        store.record(post)
        store.record(RecentPost("p2", "Second", "gifs"))
        store.record(post) // reopened: back to the top, not twice
        assertEquals(listOf("p1", "p2"), RecentPostsStore(prefs).posts.value.map { it.id })
        assertEquals(RecentPost("p1", "Hello", "pics"), RecentPostsStore(prefs).posts.value.first())

        repeat(60) { store.record(RecentPost("n$it", "t$it", "s")) }
        val kept = RecentPostsStore(prefs).posts.value
        assertEquals(RecentPostsStore.MAX, kept.size)
        assertEquals("n59", kept.first().id)
        assertEquals("n10", kept.last().id)

        store.clear()
        assertTrue(RecentPostsStore(prefs).posts.value.isEmpty())
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

class BackupTest {
    /** A device with something in every store, a signed-in account and an API setup. */
    private fun filledPrefs(clientId: String = "client_id_one"): FakePrefs {
        val prefs = FakePrefs()
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"Hello","subreddit":"pics","num_comments":5}""").jsonObject)
        SettingsStore(prefs).update { it.copy(hideRead = true) }
        FilterStore(prefs).filterKeyword("spoiler")
        ExploreStore(prefs).toggleFavorite("pics")
        UserTags(prefs).set("spez", "admin")
        UserTags(prefs).setColor("spez", 0xFF0000)
        WatchStore(prefs).watch("pics", since = 100)
        WatchStore(prefs).watchPost(post)
        VisitedStore(prefs).mark("p1")
        PostMemoryStore(prefs).saveScroll("p1", 3, 40)
        RecentPostsStore(prefs).record(post)
        DraftStore(prefs).save("submit", "draft body", "Post")
        NotifiedMessages(prefs).remember(listOf("t4_1"))
        ApiConfigStore(prefs).save(ApiConfig(clientId, "app://redirect", "agent/1.0"))
        val accounts = AccountStore(prefs)
        accounts.upsert(Account("me", "SECRET_REFRESH_TOKEN"))
        accounts.activeUsername = "me"
        accounts.accessToken = "SECRET_ACCESS_TOKEN"
        accounts.accessExpiresAt = 12345L
        return prefs
    }

    @Test
    fun `every store writes the key the backup copies`() {
        val prefs = filledPrefs()
        Backup.LOCAL_KEYS.forEach { key -> assertTrue(key in prefs.values, "nothing stored under $key") }
        assertTrue(Backup.API_CLIENT_ID in prefs.values)
    }

    @Test
    fun `backups never hold account tokens`() {
        val prefs = filledPrefs()
        val text = Backup.export(prefs, createdMillis = 0)
        assertTrue("SECRET_REFRESH_TOKEN" !in text)
        assertTrue("SECRET_ACCESS_TOKEN" !in text)
        assertTrue("auth." !in text)
        assertTrue(Backup.ALLOWED_KEYS.none { it.startsWith("auth.") })

        // Not on the way in either: a hand-edited file can't plant tokens.
        val planted = Backup.filter(mapOf("auth.accounts" to "[]", "auth.active" to "me", "settings.v1" to "{}", "unknown" to "x"))
        assertEquals(mapOf("settings.v1" to "{}"), planted)
    }

    @Test
    fun `filter keeps only allowlisted string values`() {
        val kept = Backup.filter(mapOf("visited.v1" to "a,b", "api.demo" to true, "auth.access_expires_at" to 5L, "drafts.v1" to 3))
        assertEquals(mapOf("visited.v1" to "a,b"), kept)
    }

    @Test
    fun `encode and decode round-trip`() {
        val values = mapOf("visited.v1" to "p1,p2", "settings.v1" to """{"hideRead":true}""", "api.client_id" to "abc")
        val text = Backup.encode(values, createdMillis = 86_400_000L)
        val root = RedditClient.JSON.parseToJsonElement(text).jsonObject
        assertEquals("\"resync-backup\"", root["format"].toString())
        assertEquals("1", root["version"].toString())
        assertEquals("\"1970-01-02T00:00:00Z\"", root["created"].toString())

        val doc = Backup.decode(text)
        assertEquals(1, doc.version)
        assertEquals("1970-01-02T00:00:00Z", doc.created)
        assertEquals(values, doc.prefs)
        assertTrue(doc.hasApiConfig)
        assertEquals("resync-backup-2026-11-08.json", Backup.fileName(java.time.LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `decode rejects other files`() {
        fun rejects(text: String) = assertTrue(runCatching { Backup.decode(text) }.exceptionOrNull() is BackupFormatException, text)
        rejects("not json")
        rejects("[]")
        rejects("""{"format":"resync-theme","version":1,"prefs":{}}""")
        rejects("""{"format":"resync-backup","prefs":{}}""")
        rejects("""{"format":"resync-backup","version":2,"prefs":{}}""")
        rejects("""{"format":"resync-backup","version":1}""")
        val doc = Backup.decode("""{"format":"resync-backup","version":1,"prefs":{"visited.v1":"p1","auth.active":"me","filters.v1":5}}""")
        assertEquals(mapOf("visited.v1" to "p1"), doc.prefs)
        assertNull(doc.created)
    }

    @Test
    fun `restore replaces local data, keeps accounts and leaves the API setup unless asked`() {
        val source = filledPrefs(clientId = "client_id_two")
        val doc = Backup.decode(Backup.export(source))

        val target = FakePrefs()
        ApiConfigStore(target).save(ApiConfig("client_id_one", "app://mine", "mine/1.0"))
        AccountStore(target).upsert(Account("other", "TARGET_REFRESH"))
        AccountStore(target).activeUsername = "other"
        UserTags(target).set("someone", "only here") // replaced by the backup's tags
        RecentPostsStore(target).record(RecentPost("x", "t", "s"))

        val signedOut = Backup.restore(target, doc, includeApiConfig = false)
        assertTrue(!signedOut)
        Backup.LOCAL_KEYS.forEach { key -> assertEquals(source.values[key], target.values[key], key) }
        assertTrue(SettingsStore(target).settings.value.hideRead)
        assertEquals("admin", UserTags(target).tagOf("spez"))
        assertNull(UserTags(target).tagOf("someone"))
        assertEquals("draft body", DraftStore(target).get("submit")?.text)
        assertEquals(listOf("p1"), RecentPostsStore(target).posts.value.map { it.id })
        assertEquals("client_id_one", ApiConfigStore(target).load()?.clientId)
        assertEquals("other", AccountStore(target).active?.username)
    }

    @Test
    fun `restore resets local data the backup lacks`() {
        val target = filledPrefs()
        Backup.restore(target, Backup.decode("""{"format":"resync-backup","version":1,"prefs":{"visited.v1":"p9"}}"""), includeApiConfig = true)
        assertEquals(setOf("p9"), VisitedStore(target).ids.value)
        assertTrue(DraftStore(target).drafts.value.isEmpty())
        assertTrue(FilterStore(target).filters.value.keywords.isEmpty())
        // No API setup in the file: the device's stays, and so do its accounts.
        assertEquals("client_id_one", ApiConfigStore(target).load()?.clientId)
        assertEquals("me", AccountStore(target).active?.username)
    }

    @Test
    fun `restoring a different client ID signs accounts out, the same one keeps them`() {
        val target = filledPrefs(clientId = "client_id_one")
        val same = Backup.decode(Backup.export(filledPrefs(clientId = "client_id_one")))
        assertTrue(!Backup.restore(target, same, includeApiConfig = true))
        assertEquals("me", AccountStore(target).active?.username)

        val other = Backup.decode(Backup.export(filledPrefs(clientId = "client_id_two")))
        assertTrue(Backup.restore(target, other, includeApiConfig = true))
        assertEquals(ApiConfig("client_id_two", "app://redirect", "agent/1.0"), ApiConfigStore(target).load())
        assertTrue(AccountStore(target).accounts.isEmpty())
        assertNull(AccountStore(target).activeUsername)
        assertNull(AccountStore(target).accessToken)
    }

    @Test
    fun `restoring an API setup leaves demo mode`() {
        val target = FakePrefs()
        ApiConfigStore(target).demoMode = true
        AccountStore(target).upsert(Account("demo_user", "demo"))
        val doc = Backup.decode(Backup.export(filledPrefs()))
        assertTrue(Backup.restore(target, doc, includeApiConfig = true))
        assertTrue(!ApiConfigStore(target).demoMode)
        assertTrue(AccountStore(target).accounts.isEmpty())
    }
}
