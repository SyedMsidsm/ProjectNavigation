package `in`.mite.rain.platform

import android.content.Context
import android.util.AtomicFile
import `in`.mite.rain.core.CsvGraphLoader
import `in`.mite.rain.core.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class LoadedFloor(val graph: Graph, val name: String, val source: String, val digest: String) {
    val synthetic get() = source == "synthetic_floor"
}
data class FloorPack(val floor: LoadedFloor, val json: String)

/** Imported maps are validated in full before one atomic file replaces the active override. */
class FloorPackStore(private val context: Context) {
    private val active = AtomicFile(File(context.filesDir, "active-floor.json"))
    suspend fun load(): LoadedFloor = withContext(Dispatchers.IO) {
        if (active.baseFile.exists() || File(active.baseFile.path + ".bak").exists()) {
            active.openRead().use { parse(readLimited(it)).floor }
        } else {
            val graph = GraphRepository(context).load()
            val rooms = context.assets.open("rooms.csv").bufferedReader().use { it.readText() }
            val edges = context.assets.open("edges.csv").bufferedReader().use { it.readText() }
            LoadedFloor(graph, "Demo floor / Level 01", "synthetic_floor", digest(rooms, edges))
        }
    }
    suspend fun activate(pack: FloorPack) = withContext(Dispatchers.IO) {
        parse(pack.json) // Revalidate the committed payload.
        val stream = active.startWrite()
        try { stream.write(pack.json.toByteArray(Charsets.UTF_8)); active.finishWrite(stream) }
        catch (error: Exception) { active.failWrite(stream); throw error }
    }
    suspend fun restoreDemo() = withContext(Dispatchers.IO) { active.delete() }
    companion object {
        const val MAX_BYTES = 1_000_000
        fun readLimited(input: InputStream): String {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk); if (count < 0) break
                require(buffer.size() + count <= MAX_BYTES) { "Floor pack exceeds 1 MB." }
                buffer.write(chunk, 0, count)
            }
            return buffer.toString("UTF-8")
        }
        fun parse(text: String): FloorPack {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Floor pack exceeds 1 MB." }
            val json = JSONObject(text.removePrefix("\uFEFF"))
            require(json.getInt("schema_version") == 1) { "Unsupported floor pack version." }
            val name = json.getString("name").trim()
            require(name.length in 1..80 && name.none { it.isISOControl() }) { "Use a short, single-line floor name." }
            val source = json.getString("data_source")
            require(source in setOf("synthetic_floor", "user_provided_floor", "plan_derived_floor", "recorded_trail")) { "Unknown map source." }
            val rooms = json.getString("rooms_csv"); val edges = json.getString("edges_csv")
            val graph = CsvGraphLoader.load(rooms, edges)
            require(graph.nodes.size in 2..300 && graph.edges.size <= 900) { "Use a single floor with 2–300 nodes and at most 900 segments." }
            require(graph.nodes.values.all { kotlin.math.abs(it.xM) <= 10_000 && kotlin.math.abs(it.yM) <= 10_000 } && graph.edges.all { it.lengthM <= 10_000 }) { "Use local floor coordinates and segment lengths within 10,000 metres." }
            // A navigable floor must have paths in both directions, including one-way layouts.
            fun reachable(reverse: Boolean): Int {
                val next = graph.arcs.groupBy { if (reverse) it.toNode else it.fromNode }
                val visited = mutableSetOf<String>(); val queue = java.util.ArrayDeque<String>()
                queue.add(graph.nodes.keys.first())
                while (queue.isNotEmpty()) {
                    val id = queue.removeFirst()
                    if (visited.add(id)) next[id].orEmpty().forEach { queue.add(if (reverse) it.fromNode else it.toNode) }
                }
                return visited.size
            }
            require(reachable(false) == graph.nodes.size && reachable(true) == graph.nodes.size) { "Some locations cannot be reached in both directions. Check one-way segments." }
            require(graph.nodes.values.any { it.roomName.isNotBlank() }) { "Add at least one named destination." }
            return FloorPack(LoadedFloor(graph, name, source, digest(rooms, edges)), json.toString())
        }
        private fun digest(rooms: String, edges: String) = MessageDigest.getInstance("SHA-256")
            .digest((rooms + "\u0000" + edges).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
