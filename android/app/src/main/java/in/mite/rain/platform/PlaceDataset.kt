package `in`.mite.rain.platform

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import `in`.mite.rain.core.PlaceEvidence
import `in`.mite.rain.core.VisualReference
import `in`.mite.rain.core.VisualMatching
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit captures only. Private app storage, partitioned by map and embedding model. */
class PlaceDataset(context: Context, private val floor: LoadedFloor, study: String = "pilot") {
    // Activity recreation can briefly leave an IO write and a new reader alive.
    // AtomicFile protects crash recovery, not concurrent access by two instances.
    companion object { private val datasetLock = Any() }
    init { require(study.matches(Regex("[A-Za-z0-9_-]{1,40}"))) }
    private val dir = File(context.filesDir,"place-data/${floor.digest}/${PlaceEncoder.MODEL_ID}/$study").apply { mkdirs() }
    private val index = AtomicFile(File(dir,"index.json"))
    fun read(): JSONObject = synchronized(datasetLock) { if(index.baseFile.exists() || File(index.baseFile.path+".bak").exists()) index.openRead().bufferedReader().use { JSONObject(it.readText()) }
        else JSONObject().put("schema_version",1).put("floor_digest",floor.digest).put("data_source",floor.source)
            .put("model_id",PlaceEncoder.MODEL_ID).put("model_sha256",PlaceEncoder.SHA256)
            .put("feature_names",JSONArray(VisualMatching.featureNames)).put("captures",JSONArray()) }
    fun references(): List<VisualReference> {
        val captures=read().getJSONArray("captures")
        return (0 until captures.length()).map { captures.getJSONObject(it) }.filter { it.getString("split")=="reference" }
            .map { c -> val a=c.getJSONArray("embedding"); VisualReference(c.getString("node_id"),(0 until a.length()).map { a.getDouble(it) }) }
    }
    fun summary(): String {
        val captures=read().getJSONArray("captures")
        return listOf("reference","train","validation","test").joinToString(" · ") { split ->
            "$split: " + (0 until captures.length()).count { captures.getJSONObject(it).getString("split")==split }
        }
    }
    fun save(bitmap: Bitmap, vector: List<Double>, nodeId: String?, split: String, session: String,
             evidence: List<PlaceEvidence>, capturedAt: Long): String = synchronized(datasetLock) {
        require(split in setOf("reference","train","validation","test"))
        require(session.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "Use a session code with letters, numbers, - or _." }
        require(nodeId == null || floor.graph.nodes[nodeId]?.roomName?.isNotBlank()==true)
        require(split != "reference" || nodeId != null) { "Choose a known room for a reference." }
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        val data=read(); val captures=data.getJSONArray("captures")
        require(captures.length()<3000) { "Dataset limit reached. Export before collecting another study." }
        val prior=(0 until captures.length()).map { captures.getJSONObject(it) }
        require(prior.none { it.getString("session_id")==session && it.getString("split")!=split }) {
            "This session already belongs to another split. Use a separate capture session." }
        require(split != "reference" || prior.all { it.getString("split")=="reference" }) {
            "References are frozen after evaluation collection begins. Keep this gallery unchanged for comparable results." }
        require(split == "reference" || prior.filter { it.getString("split")=="reference" }.map { it.getString("node_id") }.distinct().size>=2) { "Save references for at least two distinct rooms first." }
        require(prior.none { it.getLong("captured_at_ms")==capturedAt }) { "This capture was already saved. Take a new photo." }
        val id=UUID.randomUUID().toString(); val image=File(dir,"$id.jpg")
        image.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) }
        val row=JSONObject().put("capture_id",id).put("captured_at_ms",capturedAt).put("session_id",session)
            .put("split",split).put("node_id",nodeId ?: JSONObject.NULL).put("image_file",image.name)
            .put("embedding",JSONArray(vector)).put("candidates",JSONArray(evidence.map {
                JSONObject().put("node_id",it.nodeId).put("features",JSONArray(it.features)) }))
        captures.put(row)
        val stream=index.startWrite()
        try { stream.write(data.toString().toByteArray(Charsets.UTF_8)); index.finishWrite(stream) }
        catch(error:Exception) { index.failWrite(stream); image.delete(); throw error }
        "Capture saved locally. ${summary()}"
    }
    fun export(output: OutputStream) = synchronized(datasetLock) {
        val data=read(); val captures=data.getJSONArray("captures")
        require(captures.length()>0) { "No captures to export yet." }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("dataset.json"));zip.write(data.toString(2).toByteArray(Charsets.UTF_8));zip.closeEntry()
            for(i in 0 until captures.length()) {
                val filename=captures.getJSONObject(i).getString("image_file")
                require(filename.matches(Regex("[a-f0-9-]+\\.jpg")))
                zip.putNextEntry(ZipEntry(filename));File(dir,filename).inputStream().use { it.copyTo(zip) };zip.closeEntry()
            }
        }
    }
}
