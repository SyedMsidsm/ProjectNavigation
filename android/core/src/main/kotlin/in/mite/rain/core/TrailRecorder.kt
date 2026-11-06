package `in`.mite.rain.core

import kotlin.math.*

/** A recorded trail is local geometry, not a surveyed building plan. */
class TrailRecorder(val stepLengthM: Double, startName: String, startFloor: Int = 0) {
    private val nodes = mutableListOf(Node("T1", NodeType.JUNCTION, clean(startName), floor = startFloor))
    private val edges = mutableListOf<Edge>()
    private var last = nodes.first()
    val currentPlaceId get() = last.id
    private var segmentSteps = 0
    var steps = 0; private set
    var heading = 0.0; private set
    var floor = startFloor; private set
    var x = 0.0; private set
    var y = 0.0; private set
    init { require(stepLengthM.isFinite() && stepLengthM in .1..2.0) }
    fun step() { steps++; segmentSteps++; x += sin(Math.toRadians(heading)) * stepLengthM; y += cos(Math.toRadians(heading)) * stepLengthM }
    fun turn(angle: Double) {
        require(angle.isFinite())
        flush()
        heading = normaliseBearing(heading + (angle / 90.0).roundToInt() * 90.0)
    }
    fun mark(name: String, aliases: List<String> = emptyList()) {
        val cleaned = clean(name)
        val cleanedAliases = aliases.map(::clean).distinct()
        // Validate before flushing: a rejected label must not consume the segment
        // needed by Join a place or introduce a spurious corner.
        require(nodes.none { (segmentSteps > 0 || it.id != last.id) && it.floor == floor && it.roomName.equals(cleaned, true) }) { "That place already exists on this floor. Use Join a place to reconnect." }
        flush()
        last = last.copy(roomName = cleaned, aliases = cleanedAliases)
        nodes[nodes.indexOfFirst { it.id == last.id }] = last
    }
    fun changeFloor(target: Int, method: String) {
        require(target != floor && target in -20..100) { "Choose a different floor." }
        require(method in setOf("stairs", "lift", "escalator"))
        flush()
        val next = Node("T${nodes.size + 1}", NodeType.JUNCTION, "$method · floor $target", xM = x, yM = y, floor = target)
        // Transition cost is handled by the planner; it adds no invented walking distance.
        edges += Edge("R${edges.size + 1}", last.id, next.id, 0.0, heading,
            hasStairs = method == "stairs", transition = method)
        nodes += next; last = next; floor = target
    }
    fun join(nodeId: String) {
        val target = nodes.first { it.id == nodeId }
        require(target.floor == floor) { "Change floors before joining this place." }
        require(target.id != last.id && segmentSteps > 0) { "Walk to a different saved place before joining." }
        edges += Edge("R${edges.size + 1}", last.id, target.id, segmentSteps * stepLengthM, heading)
        last = target; x = target.xM; y = target.yM; segmentSteps = 0
    }
    fun places(): List<Node> = nodes.filter { it.roomName.isNotBlank() }
    fun preview(): Graph {
        if (segmentSteps == 0) return Graph(nodes, edges.toList())
        val tip = Node("TIP", NodeType.CORRIDOR_POINT, xM = x, yM = y, floor = floor)
        return Graph(nodes + tip, edges + Edge("TIP_EDGE", last.id, tip.id, segmentSteps * stepLengthM, heading))
    }
    fun finish(): Graph {
        require(edges.isNotEmpty() || segmentSteps > 0) { "Walk to another place before saving." }
        flush()
        // Match the reference recording flow: the final position must be selectable.
        // Preserve a place already marked here and do not add a zero-length edge.
        if (last.roomName.isBlank()) {
            var name = "End of walk"
            var suffix = 2
            while (nodes.any { it.floor == floor && it.roomName.equals(name, true) }) {
                name = "End of walk ${suffix++}"
            }
            last = last.copy(roomName = name)
            nodes[nodes.indexOfFirst { it.id == last.id }] = last
        }
        return Graph(nodes, edges.toList())
    }
    private fun flush() {
        if (segmentSteps == 0) return
        require(nodes.size < 300) { "This recording has reached 300 places and corners. Save it before continuing." }
        val next = Node("T${nodes.size + 1}", NodeType.JUNCTION, xM = x, yM = y, floor = floor)
        edges += Edge("R${edges.size + 1}", last.id, next.id, segmentSteps * stepLengthM, heading)
        nodes += next; last = next; segmentSteps = 0
    }
    companion object {
        fun clean(value: String): String = value.trim().also {
            require(it.length in 1..80 && it.none { c -> c.isISOControl() || c == '|' }) { "Use a name of 1–80 characters, without line breaks or |." }
        }
    }
}

sealed interface WalkEvent {
    data object Step : WalkEvent
    data class Turn(val degrees: Double) : WalkEvent
}

/** Gravity-projected angular velocity, with two stable steps confirming a candidate.
 * Pending steps are buffered so a confirmed turn reaches the recorder before those steps.
 * Timestamps use the monotonic sensor clock, never wall time. */
class ConfirmedTurnSensor {
    private var lastMs: Long? = null
    private var yaw = 0.0
    private var candidateAt: Long? = null
    private var stableAt = 0L
    private var pendingSteps = 0
    private var previousStepYaw: Double? = null
    fun rotation(clockwiseDegreesPerSecond: Double, ms: Long): List<WalkEvent> {
        require(clockwiseDegreesPerSecond.isFinite() && ms >= 0)
        val previous = lastMs; lastMs = ms
        if (previous == null) return emptyList()
        if (ms < previous || ms - previous > 1000) return reset()
        val rate = if (abs(clockwiseDegreesPerSecond) < 6.0) 0.0 else clockwiseDegreesPerSecond
        yaw += rate * (ms - previous) / 1000.0
        if (rate != 0.0) stableAt = ms
        if (candidateAt == null && abs(yaw) >= 55) candidateAt = ms
        if (candidateAt != null && (abs(yaw) < 30 || ms - candidateAt!! > 8000)) return resetKeepingClock()
        return emptyList()
    }
    fun step(ms: Long): List<WalkEvent> {
        if (candidateAt == null) { if (ms - stableAt > 500) yaw = 0.0; return listOf(WalkEvent.Step) }
        if (ms - candidateAt!! > 8000) return resetKeepingClock() + WalkEvent.Step
        if (ms - stableAt < 180 || abs(yaw) < 55) {
            val old = List(pendingSteps) { WalkEvent.Step }; pendingSteps = 0; previousStepYaw = null
            return old + WalkEvent.Step
        }
        if (previousStepYaw != null && abs(yaw - previousStepYaw!!) > 25) {
            val old = List(pendingSteps) { WalkEvent.Step }; pendingSteps = 1; previousStepYaw = yaw
            return old
        }
        pendingSteps++; previousStepYaw = yaw
        if (pendingSteps < 2) return emptyList()
        val angle = (yaw / 90.0).roundToInt().coerceIn(-2, 2) * 90.0
        val buffered = resetKeepingClock()
        return listOf(WalkEvent.Turn(angle)) + buffered
    }
    fun reset(): List<WalkEvent> { lastMs = null; return resetKeepingClock() }
    private fun resetKeepingClock(): List<WalkEvent> {
        val result = List(pendingSteps) { WalkEvent.Step }
        pendingSteps = 0; previousStepYaw = null; candidateAt = null; yaw = 0.0
        return result
    }
}

/** AI output is a suggestion only: a single existing place ID or nothing. */
object DestinationSearch {
    fun matches(query: String, nodes: Collection<Node>): List<Node> {
        val q = normal(query).replace(Regex("^(?:(?:please|hey) +)*(?:i am at|i m at|im at|i am in|take me to|navigate to|go to|i want to go to) +(?:the +)?"), "").removeSuffix(" please").trim()
        val places = nodes.filter { it.roomName.isNotBlank() }
        if (q.isEmpty()) return places
        fun labels(n: Node) = (listOf(n.roomName, n.id) + n.aliases).map(::normal)
        val exact = places.filter { q in labels(it) }
        if (exact.isNotEmpty()) return exact
        val partial = places.filter { labels(it).any { s -> s.contains(q) || (s.length >= 4 && q.contains(s)) } }
        if (partial.isNotEmpty()) return partial
        val compact = q.replace(" ", "")
        return places.filter { n -> labels(n).any { label ->
            val target = label.replace(" ", "")
            compact.length >= 4 && target.length >= 4 &&
                (editDistance(compact,target) <= (if(target.length>=8) 2 else 1) ||
                    (compact.length==target.length && (0 until compact.lastIndex).any { i ->
                        compact[i]!=target[i] && compact.toCharArray().apply {
                            val c=this[i]; this[i]=this[i+1]; this[i+1]=c
                        }.concatToString()==target
                    }))
        } }
    }
    fun semanticCatalog(request: String, places: List<Node>): List<Node> {
        val words = normal(request).split(" ").toSet()
        val washing = words.any { it in setOf("wash","washing","rinse") } &&
            words.any { it in setOf("face","hand","hands") }
        if (!washing) return places
        return places.filter { node -> node.type == NodeType.WASHROOM ||
            (listOf(node.roomName) + node.aliases).any { label ->
                normal(label).replace(" ", "") in setOf("washroom","bathroom","restroom","toilet","washbasin","sink") ||
                    normal(label).split(" ").any { it in setOf("washroom","bathroom","restroom","toilet","sink") }
            }
        }
    }
    fun validatedSuggestion(output: String, nodes: Collection<Node>): Node? {
        val id = output.trim().removeSurrounding("\"")
        return nodes.singleOrNull { it.id == id && it.roomName.isNotBlank() }
    }
    private fun normal(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun editDistance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1); next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            row = next
        }
        return row.last()
    }
}
