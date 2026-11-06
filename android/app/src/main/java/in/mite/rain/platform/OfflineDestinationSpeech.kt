package `in`.mite.rain.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Never falls back to a recognizer that may send audio to a remote service. */
class OfflineDestinationSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    fun listen(onText: (String) -> Unit, onStatus: (String) -> Unit) {
        close()
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onStatus("Offline speech is unavailable. Type a destination or install an offline speech language pack."); return
        }
        val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context); recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onStatus("Listening on this phone…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onStatus("Matching your destination…") }
            override fun onError(error: Int) { onStatus("Speech unavailable or no words recognised ($error). Type a place name instead."); close() }
            override fun onResults(results: Bundle?) { val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); close(); if(text!=null) onText(text) else onStatus("No words recognised.") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try { engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)) }
        catch (_: Exception) { close(); onStatus("Offline speech could not start. Type a destination instead.") }
    }
    fun close() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
}
