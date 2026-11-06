package `in`.mite.rain.core

import kotlin.math.abs

/** Kotlin port of breadcrumb's StepDetector (31c8476; unchanged in breadcrumb-fyp).
 * Uses raw acceleration magnitude, not a second high-pass filter. */
class ReferenceStepDetector(private val detectQuietSteps: Boolean = false) {
    // A quiet footfall must have both a trough and a rebound within one stride.
    private var troughAt = -1L
    private var trough = Double.POSITIVE_INFINITY
    private var lastT: Long? = null
    private var previous: Double? = null
    private var rising = false
    private var peakT: Long? = null
    private var lastStepT: Long? = null
    fun sample(t: Long, acceleration: Double): Long? {
        if (t < 0 || !acceleration.isFinite() || (lastT != null && t <= lastT!!)) return null
        if (lastT != null && t - lastT!! > 200) {
            troughAt = t; trough = acceleration
            lastT = t; previous = acceleration; rising = false; peakT = null; lastStepT = null
            return null
        }
        if (troughAt < 0 || t - troughAt > 430 || acceleration <= trough) {
            trough = acceleration
            troughAt = t
        }
        val prior = previous; val priorT = lastT
        previous = acceleration; lastT = t
        if (prior == null) return null
        if (acceleration > prior) { rising = true; peakT = t }
        else if (acceleration < prior) {
            val peak = peakT ?: priorT!!
            val quietFootfall = detectQuietSteps && prior >= 10.55 && trough < 9.65 &&
                prior - trough >= 1.45 && peak - troughAt in 40..430
            val qualified = rising && (prior >= 11.5 || quietFootfall)
            rising = false; peakT = null
            if (qualified && (lastStepT == null || peak - lastStepT!! >= 300)) {
                trough = acceleration; troughAt = t
                lastStepT = peak; return peak
            }
        }
        return null
    }
}

/** Ports TurnDetector + StepTurnConfirmer + ordered held steps from breadcrumb.
 * Input is clockwise degrees/second to match RAIN's geometry (reference uses left-positive).
 * Uses the reference defaults: 15 deg/s, 300 ms settling, 60 deg minimum, two steps/8 s.
 * Phone handling and pocket gates are disabled in the reference production defaults too. */
class ReferenceTurnPipeline(private val confirmationSteps: Int = 2) {
    private var lastMotion: Long? = null
    private var settleStart: Long? = null
    private var angle = 0.0
    private var candidateAt: Long? = null
    private var net = 0.0
    private var confirmingSteps = 0
    private var burst = false
    private var held = 0

    fun rotation(rate: Double, t: Long): List<WalkEvent> {
        if (t < 0 || !rate.isFinite() || (lastMotion != null && t <= lastMotion!!)) return emptyList()
        val previous = lastMotion; lastMotion = t
        if (previous == null) return emptyList()
        if (t - previous > 200) { angle = 0.0; settleStart = null; return emptyList() }
        if (abs(rate) > 15) { angle += rate * (t - previous) / 1000.0; settleStart = null }
        else {
            if (settleStart == null) settleStart = t
            if (t - settleStart!! >= 300) {
                val completed = angle; angle = 0.0; settleStart = null
                if (abs(completed) >= 60) return candidate(completed, t)
            }
        }
        return emptyList()
    }
    private fun candidate(degrees: Double, t: Long): List<WalkEvent> {
        val released = mutableListOf<WalkEvent>()
        if (candidateAt != null && t - candidateAt!! > 8000) { drop(); released.addAll(release()) }
        if (candidateAt != null) {
            if (confirmingSteps == 0) burst = true
            else if (burst) { drop(); released.addAll(release()) }
        }
        net += degrees; candidateAt = t; confirmingSteps = 0
        return released
    }
    // A single footfall after a settled rotation can establish direction even
    // when the cadence filter withholds its distance (e.g. stop at a corner).
    fun confirmRecordingHeading(t: Long): List<WalkEvent> {
        val since = candidateAt ?: return emptyList()
        if (confirmationSteps != 1 || t-since !in 0..8000 || burst) return emptyList()
        val turn = WalkEvent.Turn(net)
        drop()
        return listOf(turn)
    }
    fun step(t: Long): List<WalkEvent> {
        val candidate = candidateAt ?: return listOf(WalkEvent.Step)
        held++
        if (t - candidate > 8000) { drop(); return release() }
        confirmingSteps++
        if (confirmingSteps < confirmationSteps) return emptyList()
        val accepted = !burst && abs(net) >= 60
        val completed = net
        drop()
        return (if (accepted) listOf(WalkEvent.Turn(completed)) else emptyList()) + release()
    }
    fun reset(): List<WalkEvent> {
        lastMotion = null; settleStart = null; angle = 0.0; drop()
        return release()
    }
    private fun drop() { candidateAt = null; net = 0.0; confirmingSteps = 0; burst = false }
    private fun release(): List<WalkEvent> = List(held) { WalkEvent.Step }.also { held = 0 }
}

/** Keep the first footfall pending until a second peak establishes walking cadence. */
class WalkingCadence {
    private var previous: Long? = null
    private var walking = false
    fun peak(t: Long): List<Long> {
        val last = previous
        previous = t
        val consistent = last != null && t - last in 300..1200
        val accepted = when {
            !consistent -> emptyList()
            walking -> listOf(t)
            else -> listOf(checkNotNull(last), t)
        }
        walking = consistent
        return accepted
    }
}
