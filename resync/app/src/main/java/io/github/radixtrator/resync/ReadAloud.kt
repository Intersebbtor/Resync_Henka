package io.github.radixtrator.resync

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sync's "Read aloud" for comments, on the device's text-to-speech engine. The engine starts on first use,
 * and text asked for before it's ready is spoken once it is.
 */
class ReadAloud(context: Context) {
    private val context = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun speak(text: String) {
        val tts = engine ?: TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                queued?.let(::say)
            } else {
                Toast.makeText(context, "Text-to-speech isn't available", Toast.LENGTH_SHORT).show()
            }
            queued = null
        }.also { created ->
            created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }
            })
            engine = created
        }
        if (ready) say(text) else queued = text
    }

    fun stop() {
        queued = null
        engine?.stop()
        _speaking.value = false
    }

    private fun say(text: String) {
        // Long comments go over the engine's per-utterance limit, so they're queued in pieces.
        val limit = TextToSpeech.getMaxSpeechInputLength()
        text.chunked(limit).forEachIndexed { i, piece ->
            engine?.speak(piece, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "resync-$i")
        }
    }
}
