package app.sthira.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Spoken instructions through the phone's own text-to-speech engine, in the app's current language,
 * so the person hears "Stand up" or "Go" from across the room. Only the instruction text is passed
 * to the engine; nothing about the person. [available] is false when the phone has no voice for the
 * language, and then [speak] does nothing (it never falls back to another language).
 */
class Voice internal constructor(context: Context, private val locale: Locale) {
    var available by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            available = status == TextToSpeech.SUCCESS && engine != null &&
                engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    /** Replaces anything still being spoken, so the cue always matches the screen. */
    fun speak(text: String) {
        if (available) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        tts?.stop()
    }

    internal fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        available = false
    }
}

/** A [Voice] for the current app language, released when it leaves the screen or the language changes. */
@Composable
fun rememberVoice(): Voice {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val voice = remember(locale) { Voice(context, locale) }
    DisposableEffect(voice) { onDispose { voice.release() } }
    return voice
}
