package io.github.radixtrator.resync.ui.post

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentNode
import io.github.radixtrator.resync.data.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One stop of the comment reader: [label] for the player bar, [text] for the speech engine. */
data class ReaderItem(val label: String, val text: String)

/**
 * What Sync's comment text-to-speech player reads: the post (title, then its text), then each loaded top-level
 * comment, prefixed with its author. Deleted and removed comments are skipped. [plain] turns a body's HTML (or,
 * failing that, its markdown fallback) into speakable text.
 */
fun readerQueue(post: Post, comments: List<CommentNode>, plain: (html: String?, fallback: String) -> String): List<ReaderItem> = buildList {
    val selfText = plain(post.selfTextHtml, post.selfText).trim()
    add(ReaderItem("Post", if (selfText.isEmpty()) post.title else "${post.title}.\n$selfText"))
    comments.forEach { node ->
        val comment = node as? Comment ?: return@forEach
        if (comment.depth != 0) return@forEach
        val text = plain(comment.bodyHtml, comment.body).trim()
        if (text.isEmpty() || text == "[deleted]" || text == "[removed]") return@forEach
        add(ReaderItem("u/${comment.author}", "${comment.author}.\n$text"))
    }
}

/**
 * Sync's comment TTS player (`view_tts`): reads a [readerQueue] item by item on the device's text-to-speech
 * engine, with play/pause, previous, next and stop. The engine can't pause mid-sentence, so resuming starts
 * the current item again. The engine starts on first [start]; call [release] when the page goes away.
 */
class CommentReader(context: Context) {
    data class State(val items: List<ReaderItem> = emptyList(), val index: Int = 0, val playing: Boolean = false) {
        val active: Boolean get() = items.isNotEmpty()
        val current: ReaderItem? get() = items.getOrNull(index)
    }

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: TextToSpeech? = null
    private var ready = false

    /** Bumped whenever speech is cut off, so callbacks from what was flushed are ignored. */
    private var generation = 0

    fun start(items: List<ReaderItem>) {
        if (items.isEmpty()) return
        _state.value = State(items, index = 0, playing = true)
        if (engine == null) createEngine() else speakCurrent()
    }

    fun togglePlay() {
        val s = _state.value
        if (!s.active) return
        if (s.playing) {
            _state.value = s.copy(playing = false)
            silence()
        } else {
            _state.value = s.copy(playing = true)
            speakCurrent()
        }
    }

    fun next() = moveTo(_state.value.index + 1)

    fun previous() = moveTo(_state.value.index - 1)

    fun stop() {
        silence()
        _state.value = State()
    }

    /** Stops and frees the engine; the reader can still be started again afterwards. */
    fun release() {
        main.removeCallbacksAndMessages(null)
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun moveTo(index: Int) {
        val s = _state.value
        if (!s.active || index !in s.items.indices) return
        _state.value = s.copy(index = index)
        if (s.playing) speakCurrent() else silence()
    }

    private fun createEngine() {
        engine = TextToSpeech(context) { status ->
            // The init callback can arrive off the main thread on some engines.
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    if (_state.value.playing) speakCurrent()
                } else {
                    Toast.makeText(context, "Text-to-speech isn't available", Toast.LENGTH_SHORT).show()
                    stop()
                }
            }
        }.also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    main.post { finished(utteranceId) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finished(utteranceId) }
                }
            })
        }
    }

    private fun silence() {
        generation++
        engine?.stop()
    }

    private fun speakCurrent() {
        val tts = engine ?: return
        if (!ready) return // spoken once the engine is up
        val item = _state.value.current ?: return
        generation++
        // Long comments go over the engine's per-utterance limit, so they're queued in pieces.
        val pieces = item.text.ifEmpty { " " }.chunked(TextToSpeech.getMaxSpeechInputLength())
        pieces.forEachIndexed { i, piece ->
            val id = if (i == pieces.lastIndex) endId() else "reader-$generation-$i"
            tts.speak(piece, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    private fun endId() = "reader-$generation-end"

    /** An item finished: go on to the next one, or close the player after the last. */
    private fun finished(utteranceId: String?) {
        if (utteranceId != endId()) return
        val s = _state.value
        if (!s.playing) return
        if (s.index + 1 < s.items.size) {
            _state.value = s.copy(index = s.index + 1)
            speakCurrent()
        } else {
            stop()
        }
    }
}
