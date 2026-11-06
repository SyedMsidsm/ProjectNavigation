package `in`.mite.rain.core

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

enum class PlannerKind { SHORTEST, SIMPLEST, RISK }

data class PlannerSettings(val lambda: Double = 40.0, val askCost: Double = 6.0,
                           val scanEffectiveness: Double = 0.7, val avoidStairs: Boolean = false) {
    init {
        require(lambda.isFinite() && lambda >= 0) { "lambda must be finite and non-negative" }
        require(askCost.isFinite() && askCost >= 0) { "askCost must be finite and non-negative" }
        require(scanEffectiveness.isFinite() && scanEffectiveness in 0.0..1.0) { "scanEffectiveness must be in [0, 1]" }
    }
}

data class RiskFeatures(val junctionExcess: Double, val turnAngleQuarters: Double,
                        val unreadability: Double, val similarDoors: Double, val edgeLengthM: Double) {
    fun values(): List<Double> = listOf(junctionExcess, turnAngleQuarters, unreadability, similarDoors, edgeLengthM)
    companion object {
        val names = listOf("junction_excess", "turn_angle_quarters", "unreadability", "similar_doors", "edge_length_m")
    }
}

fun interface RiskModel { fun predict(features: RiskFeatures): Double }

/** A stable logistic evaluator, also usable with later offline-trained weights in this feature order. */
open class LogisticRiskModel(weights: List<Double>, private val bias: Double,
                             means: List<Double> = List(5) { 0.0 },
                             scales: List<Double> = List(5) { 1.0 }) : RiskModel {
    private val weights = weights.toList()
    private val means = means.toList()
    private val scales = scales.toList()
    init {
        require(weights.size == 5 && means.size == 5 && scales.size == 5) { "Expected five features in RiskFeatures.names order" }
        require(weights.all(Double::isFinite) && bias.isFinite() && means.all(Double::isFinite) && scales.all { it.isFinite() && it > 0 })
    }
    override fun predict(features: RiskFeatures): Double {
        val x = features.values()
        require(x.all(Double::isFinite))
        val z = bias + x.indices.sumOf { weights[it] * (x[it] - means[it]) / scales[it] }
        return sigmoid(z)
    }
}

/** Uncalibrated hand weights for demo routing. Outputs are heuristic scores, not measured error rates. */
class HandWeightedRiskModel : LogisticRiskModel(listOf(0.65, 0.95, 1.7, 0.08, 0.008), -4.0)

fun sigmoid(value: Double): Double = if (value >= 0) 1.0 / (1.0 + exp(-value)) else exp(value) / (1.0 + exp(value))

data class RouteTransition(val nodeId: String, val incomingArcId: String?, val outgoingArc: Arc,
                           val angleDeg: Double, val turnClass: TurnClass, val risk: Double,
                           val scheduledPrompt: Boolean, val scanEffectiveness: Double)

data class Route(val nodes: List<String>, val arcs: List<Arc>, val transitions: List<RouteTransition>,
                 val totalLengthM: Double, val totalCost: Double, val expectedErrors: Double,
                 val scheduledPrompts: List<String>)

class RoutePlanner(private val graph: Graph, private val model: RiskModel = HandWeightedRiskModel()) {
    private data class State(val nodeId: String, val incomingArcId: String?)
    private data class QueueItem(val state: State, val cost: Double, val sequence: Long)
    private data class Previous(val state: State, val transition: RouteTransition)

    /** Dijkstra over (node, incoming directed arc), retaining the heading necessary for turn costs. */
    fun plan(startId: String, destinationId: String, kind: PlannerKind = PlannerKind.RISK,
             settings: PlannerSettings = PlannerSettings()): Route? {
        require(startId in graph.nodes) { "Unknown start node: $startId" }
        require(destinationId in graph.nodes) { "Unknown destination node: $destinationId" }
        if (startId == destinationId) return Route(listOf(startId), emptyList(), emptyList(), 0.0, 0.0, 0.0, emptyList())
        val byId = graph.arcs.associateBy { it.id }
        val initial = State(startId, null)
        val distance = mutableMapOf(initial to 0.0)
        val previous = mutableMapOf<State, Previous>()
        val queue = PriorityQueue(compareBy<QueueItem> { it.cost }.thenBy { it.sequence })
        var sequence = 0L
        queue.add(QueueItem(initial, 0.0, sequence++))
        var found: State? = null
        while (queue.isNotEmpty()) {
            val (state, cost) = queue.remove()
            if (cost != distance[state]) continue
            if (state.nodeId == destinationId) { found = state; break }
            for (arc in graph.outgoing(state.nodeId)) {
                if (settings.avoidStairs && arc.hasStairs) continue
                val incoming = state.incomingArcId?.let(byId::getValue)
                val angle = incoming?.let { signedTurnDegrees(it.bearingDeg, arc.bearingDeg) } ?: 0.0
                val turn = classifyTurn(angle)
                val risk = model.predict(features(state.nodeId, arc, angle))
                require(risk.isFinite() && risk in 0.0..1.0) { "Risk model must return a finite score in [0, 1]" }
                val current = graph.nodes.getValue(state.nodeId)
                // A scan cannot repair risk at a location with no visible registered sign.
                val effectiveness = if (current.isRecognizable) settings.scanEffectiveness else 0.0
                val avoid = settings.lambda * risk
                val ask = settings.askCost + avoid * (1.0 - effectiveness)
                val prompt = kind == PlannerKind.RISK && ask < avoid
                val extra = when (kind) {
                    PlannerKind.SHORTEST -> 0.0
                    PlannerKind.SIMPLEST -> when (turn) {
                        TurnClass.STRAIGHT -> 0.0
                        TurnClass.SLIGHT -> 20.0
                        TurnClass.TURN, TurnClass.U_TURN -> 100.0
                    }
                    PlannerKind.RISK -> if (prompt) ask else avoid
                }
                val candidateCost = cost + arc.lengthM + extra + if (arc.transition.isNotBlank()) 10.0 else 0.0
                require(candidateCost.isFinite()) { "Route costs overflow; reduce planner parameters or edge lengths" }
                val next = State(arc.toNode, arc.id)
                if (candidateCost < (distance[next] ?: Double.POSITIVE_INFINITY)) {
                    distance[next] = candidateCost
                    previous[next] = Previous(state, RouteTransition(state.nodeId, incoming?.id, arc, angle, turn, risk, prompt, effectiveness))
                    queue.add(QueueItem(next, candidateCost, sequence++))
                }
            }
        }
        val destination = found ?: return null
        val reverse = mutableListOf<RouteTransition>()
        var current = destination
        while (current != initial) {
            val predecessor = previous.getValue(current)
            reverse += predecessor.transition
            current = predecessor.state
        }
        val transitions = reverse.asReversed()
        val arcs = transitions.map { it.outgoingArc }
        return Route(listOf(startId) + arcs.map { it.toNode }, arcs, transitions, arcs.sumOf { it.lengthM },
            distance.getValue(destination), transitions.sumOf { it.risk * if (it.scheduledPrompt) 1.0 - it.scanEffectiveness else 1.0 },
            transitions.filter { it.scheduledPrompt }.map { it.nodeId }.distinct())
    }

    fun features(nodeId: String, outgoing: Arc, angleDeg: Double): RiskFeatures {
        val node = graph.nodes.getValue(nodeId)
        val readability = nextLandmarkReadability(outgoing)
        return RiskFeatures(max(0, graph.degree(nodeId) - 2).toDouble(), abs(angleDeg) / 90.0,
            1.0 - readability, node.similarDoorsNearby.toDouble(), outgoing.lengthM)
    }

    /** Static feature: follow only an unambiguous corridor, stop at its first door or branch.
     * It does not depend on the eventual route or distance since a previous landmark. */
    private fun nextLandmarkReadability(initial: Arc): Double {
        var arc = initial
        var traveled = 0.0
        val visited = mutableSetOf<String>()
        while (visited.add(arc.id) && traveled <= 40.0) {
            traveled += arc.lengthM
            val node = graph.nodes.getValue(arc.toNode)
            if (node.type == NodeType.DOOR || node.isRecognizable)
                return if (node.isRecognizable) node.signCondition.defaultReadability else SignCondition.MISSING.defaultReadability
            val choices = graph.outgoing(node.id).filter { it.toNode != arc.fromNode }
            if (choices.size != 1) break
            arc = choices.single()
        }
        return SignCondition.MISSING.defaultReadability
    }
}
