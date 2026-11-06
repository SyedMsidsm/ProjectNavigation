package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class ReferenceTraceReplayTest {
    @Test fun recordedHardwareStepsSurviveTurnConfirmation() {
        val counts = mapOf("straight_walk_talha" to 40, "turns_walk1_talha" to 54,
            "turns_walk2_talha" to 64, "spike_walk_talha" to 38)
        counts.forEach { (name, expectedSteps) ->
            val pipe = ReferenceTurnPipeline()
            val events = mutableListOf<WalkEvent>()
            val fallback = ReferenceStepDetector()
            var fallbackSteps = 0
            val stream = javaClass.getResourceAsStream("/reference-motion/$name.csv")!!
            stream.bufferedReader().useLines { lines -> lines.forEach { line ->
                val fields = line.split(','); val t = fields[1].toLong()
                if (fields[0] == "step") events.addAll(pipe.step(t))
                else {
                    events.addAll(pipe.rotation(fields[2].toDouble(), t))
                    if (fallback.sample(t, fields[3].toDouble()) != null) fallbackSteps++
                }
            } }
            events.addAll(pipe.reset())
            assertEquals(name, expectedSteps, events.count { it == WalkEvent.Step })
            val turns = events.filterIsInstance<WalkEvent.Turn>().map { it.degrees }
            if (name == "straight_walk_talha") assertTrue("Straight walk acquired a corner: $turns", turns.isEmpty())
            else {
                // Independently labelled turn sequence from reference traces/expected_turns.json,
                // converted from left-positive to RAIN's clockwise-positive convention.
                val expected = if (name == "spike_walk_talha") listOf(90.0, -90.0, 90.0)
                    else listOf(-90.0, 90.0, 90.0)
                assertEquals("$name turn count", expected.size, turns.size)
                expected.zip(turns).forEach { (truth, measured) -> assertEquals(name, truth, measured, 15.0) }
            }
            println("REFERENCE_REPLAY $name hardware=$expectedSteps fallback=$fallbackSteps turns=$turns")
        }
    }
}
