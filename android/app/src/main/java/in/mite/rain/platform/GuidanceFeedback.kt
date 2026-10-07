package `in`.mite.rain.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Uses an installed non-network English voice. No speech is queued across screen changes. */
class GuidanceFeedback(context: Context, private val status: (String) -> Unit) {
    private val app = context.applicationContext
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var speech: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var voiceEnabled = false
    private var hapticsEnabled = false
    fun configure(voice: Boolean, haptics: Boolean) {
        voiceEnabled = voice; hapticsEnabled = haptics
        if (!voice) { speech?.stop(); status("Voice guidance off") }
        if (voice && speech == null && !closed) {
            status("Preparing offline voice…")
            speech = TextToSpeech(app) { code ->
                if (!closed) {
                    val engine = speech
                    val chosen = if (code == TextToSpeech.SUCCESS) engine?.voices?.filter {
                        !it.isNetworkConnectionRequired && it.locale.language == Locale.ENGLISH.language &&
                            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                    }?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale == Locale.getDefault() }.thenBy { it.name })?.firstOrNull() else null
                    ready = chosen != null && engine?.setVoice(chosen) == TextToSpeech.SUCCESS
                    status(if (ready) "Offline English voice ready" else "No installed offline English voice. Install one in Android text-to-speech settings; on-screen instructions still work.")
                }
            }
        } else if (voice && ready) status("Offline English voice ready")
    }
    fun announce(text: String, warning: Boolean = false) {
        if (closed) return
        if (voiceEnabled && ready) speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rain-guidance")
        if (hapticsEnabled && vibrator.hasVibrator()) {
            vibrator.vibrate(if (warning) VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1)
                else VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    fun pause() { speech?.stop(); vibrator.cancel() }
    fun close() { closed = true; pause(); speech?.shutdown(); speech = null }
}
