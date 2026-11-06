package `in`.mite.rain.core

import kotlin.math.abs

enum class NodeType { DOOR, JUNCTION, CORRIDOR_POINT, ENTRANCE, STAIRS, WASHROOM }
enum class SignCondition(val defaultReadability: Double) {
    PRINTED_CLEAR(0.95), PRINTED_FADED(0.75), HANDWRITTEN(0.55), GLARE(0.65), OBSCURED(0.35), MISSING(0.20)
}

data class Node(
    val id: String,
    val type: NodeType,
    val roomName: String = "",
    val signText: String = "",
    val aliases: List<String> = emptyList(),
    val xM: Double = 0.0,
    val yM: Double = 0.0,
    val signCondition: SignCondition = SignCondition.MISSING,
    val similarDoorsNearby: Int = 0,
    val corridorSide: String = "",
    val notes: String = "",
    val floor: Int = 0
) {
    val displayName: String get() = roomName.ifBlank { id }
    val isRecognizable: Boolean get() = signText.isNotBlank() && signCondition != SignCondition.MISSING
}

data class Edge(
    val id: String,
    val fromNode: String,
    val toNode: String,
    val lengthM: Double,
    val bearingDeg: Double,
    val corridorId: String = "",
    val bidirectional: Boolean = true,
    val hasStairs: Boolean = false,
    val notes: String = "",
    val transition: String = ""
)

/** An edge traversal has its own bearing; reverse travel must add 180 degrees. */
data class Arc(val id: String, val edgeId: String, val fromNode: String, val toNode: String,
               val lengthM: Double, val bearingDeg: Double, val hasStairs: Boolean, val transition: String = "")

class GraphValidationException(val problems: List<String>) : IllegalArgumentException(problems.joinToString("\n"))

class Graph(nodeList: Collection<Node>, val edges: List<Edge>) {
    val nodes: Map<String, Node>
    val arcs: List<Arc>
    private val adjacency: Map<String, List<Arc>>
    private val neighbors: Map<String, Set<String>>

    init {
        val errors = mutableListOf<String>()
        if (nodeList.isEmpty()) errors += "Graph must contain at least one node."
        nodeList.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { errors += "Duplicate node ID: $it" }
        edges.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { errors += "Duplicate edge ID: $it" }
        nodes = nodeList.associateBy { it.id }
        nodeList.forEach { n ->
            if (n.id.isBlank()) errors += "Node IDs cannot be blank."
            if (!n.xM.isFinite() || !n.yM.isFinite()) errors += "Node ${n.id}: coordinates must be finite."
            if (n.floor !in -20..100) errors += "Node ${n.id}: floor must be between -20 and 100."
            if (n.similarDoorsNearby < 0) errors += "Node ${n.id}: similar door count cannot be negative."
            if (n.corridorSide !in setOf("", "left", "right")) errors += "Node ${n.id}: invalid corridor side."
        }
        val neighborMap = nodes.keys.associateWith { mutableSetOf<String>() }
        edges.forEach { e ->
            if (e.id.isBlank()) errors += "Edge IDs cannot be blank."
            if (e.fromNode !in nodes || e.toNode !in nodes) errors += "Edge ${e.id}: unknown endpoint."
            if (e.fromNode == e.toNode) errors += "Edge ${e.id}: self-loops are not walkable segments."
            val a = nodes[e.fromNode]
            val b = nodes[e.toNode]
            val placeAccess = e.notes == "place_access" && a != null && b != null &&
                a.floor == b.floor && a.xM == b.xM && a.yM == b.yM &&
                (a.type == NodeType.DOOR || b.type == NodeType.DOOR)
            if (!e.lengthM.isFinite() || e.lengthM < 0 || (e.lengthM == 0.0 && e.transition.isBlank() && !placeAccess)) errors += "Edge ${e.id}: walking length must be finite and positive."
            if (!e.bearingDeg.isFinite()) errors += "Edge ${e.id}: bearing must be finite."
            if (e.transition !in setOf("", "stairs", "lift", "escalator")) errors += "Unknown floor transition."
            if (e.fromNode in nodes && e.toNode in nodes && (nodes.getValue(e.fromNode).floor != nodes.getValue(e.toNode).floor) != e.transition.isNotBlank()) errors += "Floor transitions must connect different floors."
            neighborMap[e.fromNode]?.add(e.toNode)
            neighborMap[e.toNode]?.add(e.fromNode)
        }
        nodeList.filter { it.type == NodeType.DOOR && (neighborMap[it.id]?.size ?: 0) > 2 }
            .forEach { errors += "Door ${it.id}: degree exceeds 2; split the corridor at a junction."
            }
        if (nodes.isNotEmpty() && edges.all { it.fromNode in nodes && it.toNode in nodes }) {
            val visited = mutableSetOf<String>()
            val queue = java.util.ArrayDeque<String>()
            queue.add(nodes.keys.first())
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (visited.add(n)) neighborMap.getValue(n).filterNot { it in visited }.forEach(queue::addLast)
            }
            if (visited.size != nodes.size) errors += "Graph is disconnected: unreachable component contains ${nodes.keys - visited}."
        }
        if (errors.isNotEmpty()) throw GraphValidationException(errors)
        neighbors = neighborMap.mapValues { it.value.toSet() }
        arcs = edges.flatMap { e ->
            val forward = Arc("${e.id}:forward", e.id, e.fromNode, e.toNode, e.lengthM, normaliseBearing(e.bearingDeg), e.hasStairs, e.transition)
            if (!e.bidirectional) listOf(forward) else listOf(forward,
                Arc("${e.id}:reverse", e.id, e.toNode, e.fromNode, e.lengthM, normaliseBearing(e.bearingDeg + 180), e.hasStairs, e.transition))
        }
        adjacency = arcs.groupBy { it.fromNode }.mapValues { (_, value) -> value.sortedBy { it.id } }
    }

    fun outgoing(nodeId: String): List<Arc> = adjacency[nodeId].orEmpty()
    fun degree(nodeId: String): Int = neighbors[nodeId]?.size ?: 0
}

fun normaliseBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
/** Positive is a right turn for bearings increasing clockwise from building north. */
fun signedTurnDegrees(incomingBearing: Double, outgoingBearing: Double): Double =
    ((outgoingBearing - incomingBearing + 540.0) % 360.0 + 360.0) % 360.0 - 180.0

enum class TurnClass { STRAIGHT, SLIGHT, TURN, U_TURN }
fun classifyTurn(angleDeg: Double): TurnClass = when {
    abs(angleDeg) < 30 -> TurnClass.STRAIGHT
    abs(angleDeg) < 60 -> TurnClass.SLIGHT
    abs(angleDeg) <= 135 -> TurnClass.TURN
    else -> TurnClass.U_TURN
}
