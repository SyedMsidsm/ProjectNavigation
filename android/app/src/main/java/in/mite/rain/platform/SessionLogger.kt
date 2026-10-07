package `in`.mite.rain.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

/** Single ordered writer: sensor callbacks never perform disk IO on the UI thread. */
class SessionLogger(private val context: Context) {
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile var sessionId: String? = null
        private set
    @Volatile private var active = false
    private var metadata: Map<String, Any?> = emptyMap()
    @Volatile private var file: File? = null
    @Volatile var lastError: String? = null
        private set
    val hasSession: Boolean get() = file != null

    @Synchronized fun start(fields: Map<String, Any?>) {
        if (active) finish(false)
        sessionId = UUID.randomUUID().toString()
        file = File(context.filesDir, "sessions/session_${sessionId}.jsonl")
        metadata = mapOf("schema_version" to "0.2") + fields.filterKeys { it in setOf("schema_version", "demo_mode", "data_source", "model_version", "simulation") }
        active = true
        event("session_start", fields)
    }
    @Synchronized fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        if (!active) return
        val target = file ?: return
        metadata = metadata + fields.filterKeys { it in setOf("schema_version", "demo_mode", "data_source", "model_version", "simulation") }
        require(fields.keys.none { it.lowercase() in setOf("ssid", "bssid", "ocr_raw_text", "raw_text", "image") })
        val record = JSONObject().put("session_id", sessionId).put("ts_ms", System.currentTimeMillis()).put("event", name)
        metadata.forEach { (key, value) -> record.put(key, jsonValue(value)) }
        fields.forEach { (key, value) -> record.put(key, jsonValue(value)) }
        val line = record.toString() + "\n"
        writer.execute {
            try { target.parentFile?.mkdirs(); target.appendText(line) }
            catch (_: Exception) { lastError = "Session could not be saved; check free storage." }
        }
    }
    @Synchronized fun finish(completed: Boolean) {
        if (!active) return
        event("session_end", mapOf("completed" to completed))
        active = false
    }
    /** Queue an immutable export after pending writes, then open Android's share sheet. */
    fun share(): Boolean {
        val source = file ?: return false
        writer.execute {
            try {
                val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                val copy = File(directory, source.name)
                source.copyTo(copy, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/x-ndjson"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("RAIN session", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try { context.startActivity(Intent.createChooser(intent, "Export RAIN session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { lastError = "No app is available to receive this export." }
                }
            } catch (_: Exception) { lastError = "Export failed; start a session and try again." }
        }
        return true
    }
    fun hashText(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    fun close() { finish(false); writer.shutdown() }
    private fun jsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { obj -> value.forEach { (k, v) -> obj.put(k.toString(), jsonValue(v)) } }
        is Iterable<*> -> JSONArray().also { array -> value.forEach { array.put(jsonValue(it)) } }
        is Array<*> -> JSONArray().also { array -> value.forEach { array.put(jsonValue(it)) } }
        else -> value
    }
}
