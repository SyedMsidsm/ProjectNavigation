package `in`.mite.rain.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PromptMode { COST_SENSITIVE, ALWAYS, FIXED_INTERVAL, NEVER }

class PromptPolicy(private val settings: PlannerSettings = PlannerSettings(),
                   private val mode: PromptMode = PromptMode.COST_SENSITIVE,
                   private val fixedIntervalM: Double = 20.0) {
    private var lastPromptDistanceM = Double.NEGATIVE_INFINITY
    init { require(fixedIntervalM.isFinite() && fixedIntervalM > 0) }

    fun evaluate(distanceWalkedM: Double, distanceSinceFixM: Double, turnsSinceFix: Int,
                 lastFixConfidence: Double, scheduledDistanceAheadM: Double?, riskAhead: Double,
                 recognizableDoorNearby: Boolean, offRoute: Boolean = false): String? {
        if (offRoute) return "off_route"
        if (mode == PromptMode.NEVER || !recognizableDoorNearby || distanceWalkedM - lastPromptDistanceM < 15.0) return null
        val reason = when (mode) {
            PromptMode.NEVER -> null
            PromptMode.ALWAYS -> "every_door"
            PromptMode.FIXED_INTERVAL -> if (distanceWalkedM >= fixedIntervalM &&
                (lastPromptDistanceM == Double.NEGATIVE_INFINITY || distanceWalkedM - lastPromptDistanceM >= fixedIntervalM)) "fixed_interval" else null
            PromptMode.COST_SENSITIVE -> when {
                scheduledDistanceAheadM != null && scheduledDistanceAheadM in 0.0..4.0 -> "scheduled"
                sigmoid(0.04 * distanceSinceFixM + 0.5 * turnsSinceFix - 3.0 * lastFixConfidence - 1.0) * settings.lambda * riskAhead > settings.askCost -> "uncertainty"
                else -> null
            }
        }
        if (reason != null) lastPromptDistanceM = distanceWalkedM
        return reason
    }
}

data class NavigationState(val distanceWalkedM: Double, val remainingDistanceM: Double,
                           val currentNodeId: String, val currentArcIndex: Int, val offsetM: Double,
                           val arrived: Boolean, val offRouteReason: String?, val promptReason: String?,
                           val instruction: String, val driftM: Double,
                           val confidence: Double = 0.9, val floorChangePending: Boolean = false,
                           val returnSteps: Int? = null, val returning: Boolean = false)

/** Intermediate room marks along the same corridor share one walking instruction. */
fun Route.guidanceEndIndex(currentIndex: Int): Int {
    var end = currentIndex
    while (end < arcs.lastIndex && arcs[end].transition.isBlank() &&
        arcs[end + 1].transition.isBlank() && transitions[end + 1].turnClass == TurnClass.STRAIGHT) end++
    return end
}

fun Route.distanceToGuidanceEnd(state: NavigationState): Double =
    if (arcs.isEmpty()) 0.0 else max(0.0,
        (state.currentArcIndex..guidanceEndIndex(state.currentArcIndex)).sumOf { arcs[it].lengthM } - state.offsetM)

/** Lightweight route-following PDR. It estimates progress, not an independently measured position.
 * At a planned turn of at least 60 degrees the tracker waits for matching relative-yaw
 * evidence before taking that branch. Slight bends can be below the sensor's 50-degree
 * event threshold and therefore do not require a turn event. */
class NavigationTracker(private val graph: Graph, val route: Route, val stepLengthM: Double = 0.7,
                        settings: PlannerSettings = PlannerSettings(),
                        promptMode: PromptMode = PromptMode.COST_SENSITIVE) {
    private var arcIndex = 0
    private var offset = 0.0
    private var walked = 0.0
    private var distanceSinceFix = 0.0
    private var turnsSinceFix = 0
    private var confidence = 0.9
    private var acceptedTurnIndex: Int? = null
    private var evidenceTurnIndex: Int? = null
    private var evidenceAngleDeg = 0.0
    private var offRoute: String? = null
    private var returning = false
    private fun canRetrace() = offRoute == "missed_turn" || offRoute == "overrun"
    fun beginReturn(): NavigationState {
        if (canRetrace()) returning = true
        return snapshot()
    }
    private var prompt: String? = null
    private var arrived = route.arcs.isEmpty()
    private val policy = PromptPolicy(settings, promptMode)
    private val starts = route.arcs.runningFold(0.0) { value, arc -> value + arc.lengthM }

    init {
        require(stepLengthM.isFinite() && stepLengthM in 0.1..2.0) { "Step length must be between 0.1 and 2 metres" }
        require(route.nodes.isNotEmpty() && route.nodes.all { it in graph.nodes })
        require(route.arcs.size == route.transitions.size && route.nodes.size == route.arcs.size + 1)
        require(route.arcs.indices.all { route.arcs[it].fromNode == route.nodes[it] && route.arcs[it].toNode == route.nodes[it + 1] })
        if (route.arcs.firstOrNull()?.let { it.lengthM == 0.0 && it.transition.isBlank() } == true) progress()
        updatePrompt()
    }

    fun step(): NavigationState = advance(stepLengthM)

    fun advance(distanceM: Double): NavigationState {
        require(distanceM.isFinite() && distanceM >= 0)
        if (canRetrace()) {
            walked += distanceM
            offset = if (returning) max(route.arcs[arcIndex].lengthM, offset-distanceM) else offset+distanceM
            return snapshot()
        }
        if (route.arcs.isEmpty() || offRoute != null || route.arcs[arcIndex].transition.isNotBlank()) return snapshot()
        walked += distanceM
        distanceSinceFix += distanceM
        offset += distanceM
        progress()
        updatePrompt()
        return snapshot()
    }

    fun turn(angleDeg: Double): NavigationState {
        require(angleDeg.isFinite())
        if (arrived || offRoute != null || route.arcs[arcIndex].transition.isNotBlank() || abs(angleDeg) < 30) return snapshot()
        val arc = route.arcs[arcIndex]
        val nearEnd = arc.lengthM - offset <= 3.0
        val next = route.transitions.getOrNull(arcIndex + 1)
        if (offset > 3.0 && !nearEnd) { turnsSinceFix++; flag("unexpected_turn") }
        else if (nearEnd && next != null) {
            if (next.turnClass == TurnClass.STRAIGHT ||
                (next.turnClass != TurnClass.U_TURN && next.angleDeg * angleDeg < 0)) { turnsSinceFix++; flag("unexpected_turn") }
            else {
                // The sensor emits when rotation first exceeds 50 degrees. A 120-degree
                // turn or U-turn can therefore arrive in several partial events.
                if (evidenceTurnIndex != arcIndex + 1) {
                    evidenceTurnIndex = arcIndex + 1
                    evidenceAngleDeg = 0.0
                    turnsSinceFix++
                }
                evidenceAngleDeg += angleDeg
                if (abs(signedTurnDegrees(next.angleDeg, evidenceAngleDeg)) <= 45.0) acceptedTurnIndex = arcIndex + 1
                else if (abs(evidenceAngleDeg) > abs(next.angleDeg) + 45.0) flag("unexpected_turn")
            }
        } else if (arcIndex > 0 && offset <= 3.0) {
            val expected = route.transitions[arcIndex]
            if (evidenceTurnIndex != arcIndex) turnsSinceFix++
            if (expected.turnClass == TurnClass.STRAIGHT || abs(signedTurnDegrees(expected.angleDeg, angleDeg)) > 45.0) flag("unexpected_turn")
        }
        progress()
        updatePrompt()
        return snapshot()
    }

    /** A recognized route node is an explicit location fix. Off-route fixes should plan a new route. */
    fun fixAtNode(nodeId: String, fixConfidence: Double = 0.9): NavigationState {
        require(fixConfidence.isFinite() && fixConfidence in 0.0..1.0)
        val candidates = route.nodes.indices.filter { route.nodes[it] == nodeId }
        require(candidates.isNotEmpty()) { "Recognized node is off the route; plan from it before continuing" }
        val index = candidates.minBy { abs(it - arcIndex) }
        arcIndex = min(index, route.arcs.lastIndex.coerceAtLeast(0))
        arrived = index == route.nodes.lastIndex
        offset = if (arrived && route.arcs.isNotEmpty()) route.arcs.last().lengthM else 0.0
        distanceSinceFix = 0.0
        turnsSinceFix = 0
        confidence = fixConfidence
        offRoute = null
        returning = false
        prompt = null
        acceptedTurnIndex = null
        evidenceTurnIndex = null
        evidenceAngleDeg = 0.0
        return snapshot()
    }

    fun dismissPrompt(): NavigationState { prompt = null; return snapshot() }
    fun requirePositionCheck(reason: String): NavigationState { flag(reason); return snapshot() }

    fun confirmFloorChange(): NavigationState {
        require(route.arcs.isNotEmpty() && route.arcs[arcIndex].transition.isNotBlank() && offRoute == null)
        val end = route.arcs[arcIndex].toNode
        return fixAtNode(end)
    }

    /** Import measured AR route progress without replaying steps or synthetic turn events. */
    fun applyArProgress(index: Int, offsetM: Double, needsFix: Boolean): NavigationState {
        require(index in route.arcs.indices && offsetM.isFinite() && offsetM in 0.0..route.arcs[index].lengthM)
        val before = starts[arcIndex] + min(offset, route.arcs[arcIndex].lengthM)
        val after = starts[index] + offsetM
        require(after + 0.001 >= before) { "AR result cannot rewind the journey" }
        walked += max(0.0, after - before); distanceSinceFix += max(0.0, after - before)
        turnsSinceFix += max(0, index - arcIndex)
        arcIndex = index; offset = offsetM
        acceptedTurnIndex = null; evidenceTurnIndex = null; evidenceAngleDeg = 0.0
        arrived = index == route.arcs.lastIndex && route.totalLengthM - after <= 0.4
        if (needsFix) flag("ar_location_check")
        updatePrompt()
        return snapshot()
    }

    fun snapshot(): NavigationState {
        if (route.arcs.isEmpty()) return NavigationState(walked, 0.0, route.nodes.last(), 0, 0.0, true, offRoute, prompt, instruction(), 0.0, confidence)
        val along = if (arrived) route.totalLengthM else starts[arcIndex] + min(offset, route.arcs[arcIndex].lengthM)
        val node = if (arrived) route.nodes.last() else route.arcs[arcIndex].let { if (offset > it.lengthM / 2) it.toNode else it.fromNode }
        return NavigationState(walked, max(0.0, route.totalLengthM - along), node, arcIndex, offset, arrived,
            offRoute, prompt, instruction(), 0.05 * distanceSinceFix + 1.5 * turnsSinceFix,
            (confidence - .008 * distanceSinceFix - .04 * turnsSinceFix).coerceIn(.05, .95),
            !arrived && route.arcs[arcIndex].transition.isNotBlank(),
            if(canRetrace()) kotlin.math.ceil(((offset-route.arcs[arcIndex].lengthM)/stepLengthM-1e-7).coerceAtLeast(0.0)).toInt() else null, returning)
    }

    private fun progress() {
        if (route.arcs.isEmpty() || offRoute != null) return
        while (true) {
            val arc = route.arcs[arcIndex]
            if (arc.transition.isNotBlank()) { offset = 0.0; return }
            if (offset < arc.lengthM) return
            if (arcIndex == route.arcs.lastIndex) {
                // Arrival is a PDR estimate until the user confirms the destination sign.
                // Keep consuming steps so walking past it can still raise an overrun.
                if (offset > 1.3 * arc.lengthM + 3.0) flag("overrun") else arrived = true
                return
            }
            val next = route.transitions[arcIndex + 1]
            if (next.outgoingArc.transition.isBlank() && arc.transition.isBlank() && next.turnClass in setOf(TurnClass.TURN, TurnClass.U_TURN) && acceptedTurnIndex != arcIndex + 1) {
                if (offset > arc.lengthM + 4.0) flag("missed_turn")
                else if (offset > 1.3 * arc.lengthM + 3.0) flag("overrun")
                return
            }
            offset -= arc.lengthM
            arcIndex++
            acceptedTurnIndex = null
        }
    }

    private fun updatePrompt() {
        if (arrived || prompt != null) return
        val along = starts[arcIndex] + offset
        val ahead = route.transitions.mapIndexed { i, transition -> transition to (starts[i] - along) }
            .filter { (_, distance) -> distance in 0.0..10.0 }
        val scheduled = ahead.filter { it.first.scheduledPrompt }.minOfOrNull { it.second }
        val nearby = route.nodes.indices.any { abs(starts[it] - along) <= 4.0 && graph.nodes.getValue(route.nodes[it]).isRecognizable }
        prompt = policy.evaluate(walked, distanceSinceFix, turnsSinceFix, confidence, scheduled,
            ahead.maxOfOrNull { it.first.risk } ?: 0.0, nearby, offRoute != null)
    }

    private fun flag(reason: String) { offRoute = reason; prompt = "off_route"; arrived = false }

    private fun instruction(): String {
        if(canRetrace()) {
            val target = if(arcIndex == route.arcs.lastIndex) "destination" else "corner"
            return if(returning) "Retrace your steps to the $target and confirm when you recognise it."
                else "You may have passed the $target. Stop and return along the same corridor."
        }
        if (offRoute != null) return "Pause and scan a nearby room sign to confirm your location."
        if (arrived) return "Estimated arrival at ${graph.nodes.getValue(route.nodes.last()).displayName}. Confirm the room sign."
        val arc = route.arcs[arcIndex]
        if (arc.transition.isNotBlank()) return "Take the ${arc.transition} to floor ${graph.nodes.getValue(arc.toNode).floor}. Tap Change floor only after reaching it."
        val endIndex = route.guidanceEndIndex(arcIndex)
        val end = route.arcs[endIndex]
        val remaining = max(0.0, (arcIndex..endIndex).sumOf { route.arcs[it].lengthM } - offset).roundToInt()
        val next = route.transitions.getOrNull(endIndex + 1)
        if (next == null) return "Walk straight for $remaining m to ${graph.nodes.getValue(end.toNode).displayName}."
        if (next.outgoingArc.transition.isNotBlank()) return "Walk $remaining m to the ${next.outgoingArc.transition}."
        val action = when (next.turnClass) {
            TurnClass.STRAIGHT -> "continue straight"
            TurnClass.SLIGHT -> if (next.angleDeg > 0) "bear right" else "bear left"
            TurnClass.TURN -> if (next.angleDeg > 0) "turn right" else "turn left"
            TurnClass.U_TURN -> "turn around"
        }
        val landmark = graph.nodes.getValue(end.toNode).roomName.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()
        return if (remaining == 0) "${action.replaceFirstChar(Char::uppercaseChar)}$landmark."
        else "Walk $remaining m, then $action$landmark."
    }
}

/** Relative yaw detector with a bounded 2.5 s window; call with clockwise-positive degrees. */
class TurnDetector(private val thresholdDeg: Double = 50.0, private val windowMs: Long = 2500) {
    private data class Sample(val timestampMs: Long, val yaw: Double)
    private val history = java.util.ArrayDeque<Sample>()
    private var lastTimestamp = Long.MIN_VALUE
    init { require(thresholdDeg.isFinite() && thresholdDeg > 0 && thresholdDeg <= 180 && windowMs > 0) }
    fun update(yawDeg: Double, timestampMs: Long): Double? {
        require(yawDeg.isFinite() && timestampMs >= 0)
        if (timestampMs < lastTimestamp) history.clear()
        lastTimestamp = timestampMs
        while (history.isNotEmpty() && timestampMs - history.first.timestampMs > windowMs) history.removeFirst()
        val angle = history.firstOrNull()?.let { signedTurnDegrees(it.yaw, yawDeg) }
        history.addLast(Sample(timestampMs, yawDeg))
        if (angle != null && abs(angle) > thresholdDeg) { history.clear(); history.addLast(Sample(timestampMs, yawDeg)); return angle }
        return null
    }
}
