package `in`.mite.rain.core

data class CalibrationResult(val distanceM: Double, val steps: Int, val stepLengthM: Double)

/** A trial cannot silently resume after leaving the foreground and missing steps. */
class StepCalibration {
    var recording = false; private set
    var steps = 0; private set
    var distanceM = 0.0; private set
    var interrupted = false; private set
    fun start(distance: Double) {
        require(distance.isFinite() && distance in 5.0..100.0) { "Enter a measured distance from 5 to 100 metres." }
        distanceM = distance; steps = 0; interrupted = false; recording = true
    }
    fun step() { if (recording) steps++ }
    fun interrupt() { if (recording) { recording = false; interrupted = true } }
    fun stop(): CalibrationResult {
        require(recording && !interrupted) { "Start a new uninterrupted calibration walk." }
        recording = false
        require(steps >= 8) { "Too few steps. Use a longer measured distance and try again." }
        val length = distanceM / steps
        require(length in .3..1.2) { "The result is outside 0.30–1.20 m per step. Check distance and step detection, then repeat." }
        return CalibrationResult(distanceM, steps, length)
    }
}
