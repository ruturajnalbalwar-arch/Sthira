package app.sthira.timers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.sthira.voice.Voice
import app.sthira.voice.rememberVoice

/**
 * Spoken cues for the timer screens. They speak only when the user turned voice on in settings and
 * the phone has a voice for the app language. Speech stops when the screen is left.
 */
class TimerVoice internal constructor(private val voice: Voice, private val enabled: Boolean) {
    fun say(text: String) {
        if (enabled && voice.available) voice.speak(text)
    }

    fun stop() = voice.stop()
}

@Composable
fun rememberTimerVoice(): TimerVoice {
    val voice = rememberVoice()
    val enabled = app.sthira.settings.LocalAppSettings.current.voiceOn
    DisposableEffect(voice) { onDispose { voice.stop() } }
    return TimerVoice(voice, enabled)
}

/**
 * Speaks [text] once each time [key] changes to a new non-null value ([key] must be saveable, such as
 * a number). The last spoken key survives
 * rotation, so turning the phone does not repeat the cue.
 */
@Composable
fun SpeakOnChange(voice: TimerVoice, key: Any?, text: String) {
    var spokenFor by rememberSaveable { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != null && key != spokenFor) voice.say(text)
        spokenFor = key
    }
}
