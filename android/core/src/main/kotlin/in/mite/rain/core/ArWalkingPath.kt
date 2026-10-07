package `in`.mite.rain.core

import kotlin.math.*

data class ArWalkingState(val arcIndex: Int, val offsetM: Double, val alongM: Double,
                          val remainingM: Double, val arrived: Boolean, val failure: String?, val offPath: Boolean = false)

/** Route-relative visual-inertial odometry. No global localisation is inferred.
 * Only the current and next segment may match, preventing jumps at crossings.
 * Failure is latched: a new confirmed location is needed before another walk. */
class ArWalkingPath(val lengths: List<Double>, val bearings: List<Double>, val initialOffset: Double = 0.0) {
    private data class Segment(val x: Double, val z: Double, val dx: Double, val dz: Double, val length: Double)
    private val segments: List<Segment>
    private val starts: List<Double>
    private var index = 0
    private var along = 0.0
    private var failure: String? = null
    private var lastX = 0.0
    private var lastZ = 0.0
    private var lastTime = -1L
    private var outsideSince = -1L
    val total: Double
    init {
        require(lengths.isNotEmpty() && lengths.size == bearings.size)
        require(lengths.all { it.isFinite() && it > 0 } && bearings.all { it.isFinite() })
        require(initialOffset.isFinite() && initialOffset in 0.0..lengths.first())
        var x = 0.0; var z = 0.0
        segments = lengths.indices.map { i ->
            val angle = Math.toRadians(signedTurnDegrees(bearings.first(), bearings[i]))
            val length = lengths[i] - if (i == 0) initialOffset else 0.0
            Segment(x, z, sin(angle), -cos(angle), length).also { x += it.dx * length; z += it.dz * length }
        }
        starts = segments.runningFold(0.0) { a, s -> a + s.length }
        total = starts.last()
    }
    fun invalidate(reason: String) { if (failure == null) failure = reason }
    fun snapshot(): ArWalkingState {
        val offset = (along - starts[index]).coerceIn(0.0, segments[index].length)
        return ArWalkingState(index, offset + if (index == 0) initialOffset else 0.0,
            along, (total - along).coerceAtLeast(0.0), total - along <= 0.4 && failure == null && outsideSince < 0,
            failure, outsideSince >= 0)
    }
    fun update(x: Double, z: Double, timestampNs: Long): ArWalkingState {
        require(x.isFinite() && z.isFinite() && timestampNs >= 0)
        if (failure != null || timestampNs == lastTime) return snapshot()
        if (lastTime >= 0) {
            val seconds = (timestampNs - lastTime) / 1e9
            if (seconds <= 0 || seconds > 1.0 || hypot(x - lastX, z - lastZ) > 0.20 + 3.0 * seconds) {
                invalidate("Tracking discontinuity. Confirm your location on the map."); return snapshot()
            }
        } else if (hypot(x, z) > 0.5) {
            invalidate("Alignment moved before tracking began. Confirm your location."); return snapshot()
        }
        val travel = hypot(x - lastX, z - lastZ)
        lastX = x; lastZ = z; lastTime = timestampNs
        data class Match(val i: Int, val t: Double, val distance: Double)
        fun match(i: Int): Match {
            val s = segments[i]
            val t = ((x - s.x) * s.dx + (z - s.z) * s.dz).coerceIn(0.0, s.length)
            return Match(i, t, hypot(x - s.x - s.dx * t, z - s.z - s.dz * t))
        }
        var best = match(index)
        // Require physical entry into the next segment, not simply proximity to its endpoint.
        if (index < segments.lastIndex && starts[index + 1] - along <= 1.0) {
            val next = match(index + 1)
            if (next.t > 0.15 && (next.distance + 0.05 < best.distance ||
                    (starts[index + 1] - along <= 0.4 && starts[index] + best.t < along - 0.15 &&
                        next.distance <= best.distance + 0.05))) best = next
        }
        val candidate = starts[best.i] + best.t
        val outside = best.distance > 1.5 || candidate < along - 1.5
        if (outside) {
            if (outsideSince < 0) outsideSince = timestampNs
            if (timestampNs - outsideSince >= 1_000_000_000L) invalidate("You may be off route. Return to the map and confirm a room sign.")
        } else {
            outsideSince = -1L
            // Allow a rounded corner within the junction tolerance. Elsewhere only translation plus jitter is accepted.
            val tolerance = if (best.i == index + 1) 2.0 else 0.25
            if (candidate > along && candidate - along <= travel + tolerance) { index = best.i; along = candidate }
        }
        return snapshot()
    }
    /** A rolling six-metre window in the original alignment coordinates. */
    fun arrows(): List<ArArrow> {
        if (failure != null || snapshot().arrived || outsideSince >= 0) return emptyList()
        val result = mutableListOf<ArArrow>()
        var sample = (floor(along / 0.75) + 1) * 0.75
        while (sample <= min(total, along + 6.0)) {
            val i = segments.indices.firstOrNull { sample <= starts[it + 1] } ?: break
            val s = segments[i]; val t = sample - starts[i]
            result += ArArrow((s.x + s.dx * t).toFloat(), (s.z + s.dz * t).toFloat(),
                signedTurnDegrees(bearings.first(), bearings[i]).toFloat())
            sample += 0.75
        }
        return result
    }
}
