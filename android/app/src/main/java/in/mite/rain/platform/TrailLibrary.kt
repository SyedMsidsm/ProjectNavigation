package `in`.mite.rain.platform

import android.content.Context
import android.util.AtomicFile
import `in`.mite.rain.core.Graph
import `in`.mite.rain.core.TrailRecorder
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedTrail(val id: String, val name: String, val places: Int)

/** Separate immutable map IDs; renaming never changes the storage path. */
class TrailLibrary(context: Context) {
    private val directory = File(context.filesDir, "recorded-maps").apply { mkdirs() }
    fun list(): List<SavedTrail> = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
        runCatching { val pack = read(file.nameWithoutExtension); SavedTrail(file.nameWithoutExtension, pack.floor.name, pack.floor.graph.nodes.values.count { it.roomName.isNotBlank() }) }.getOrNull()
    }.sortedBy { it.name.lowercase() }
    fun save(name: String, graph: Graph): String {
        val id = UUID.randomUUID().toString()
        write(id, pack(name, graph)); return id
    }
    fun read(id: String): FloorPack = AtomicFile(file(id)).openRead().use { FloorPackStore.parse(FloorPackStore.readLimited(it)) }
    fun rename(id: String, name: String) {
        val json = JSONObject(read(id).json).put("name", TrailRecorder.clean(name))
        write(id, FloorPackStore.parse(json.toString()))
    }
    fun delete(id: String) { AtomicFile(file(id)).delete() }
    private fun file(id: String): File {
        require(Regex("[a-f0-9-]{36}").matches(id)) { "Invalid saved map ID." }
        return File(directory, "$id.json")
    }
    private fun write(id: String, pack: FloorPack) {
        val atomic = AtomicFile(file(id)); val stream = atomic.startWrite()
        try { stream.write(pack.json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    companion object {
        fun pack(name: String, graph: Graph): FloorPack {
            fun row(values: List<Any>) = values.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }
            val rooms = "node_id,node_type,room_name,sign_text,aliases,x_m,y_m,corridor_side,sign_condition,similar_doors_nearby,notes,floor\n" +
                graph.nodes.values.joinToString("\n") { n -> row(listOf(n.id,n.type,n.roomName,n.signText,n.aliases.joinToString("|"),n.xM,n.yM,n.corridorSide,n.signCondition,n.similarDoorsNearby,n.notes,n.floor)) }
            val edges = "edge_id,from_node,to_node,length_m,bearing_deg,corridor_id,bidirectional,has_stairs,notes,transition\n" +
                graph.edges.joinToString("\n") { e -> row(listOf(e.id,e.fromNode,e.toNode,e.lengthM,e.bearingDeg,e.corridorId,e.bidirectional,e.hasStairs,e.notes,e.transition)) }
            return FloorPackStore.parse(JSONObject().put("schema_version",1).put("name",TrailRecorder.clean(name))
                .put("data_source","recorded_trail").put("rooms_csv",rooms).put("edges_csv",edges).toString())
        }
    }
}
