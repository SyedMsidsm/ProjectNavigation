package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class ReferenceMotionTest {
    @Test fun twentyWalkingPeaksProduceTwentySteps() {
        val detector = ReferenceStepDetector()
        var steps = 0
        // Raw magnitude profile, 50 Hz, one footfall each 500 ms.
        for (t in 0L..10000L step 20) {
            val phase = t % 500
            val magnitude = when (phase) { 180L -> 10.8; 200L -> 12.3; 220L -> 11.0; else -> 9.81 }
            if (detector.sample(t, magnitude) != null) steps++
        }
        assertEquals(20, steps)
    }
    @Test fun stationaryNoiseAndDuplicateSamplesDoNotCount() {
        val detector = ReferenceStepDetector()
        for (t in 0L..3000L step 20) {
            assertNull(detector.sample(t, 9.81 + .1 * kotlin.math.sin(t.toDouble())))
            assertNull(detector.sample(t, 30.0))
        }
    }
    @Test fun interruptionDoesNotFinishAPreviousPeak() {
        val detector = ReferenceStepDetector()
        detector.sample(0, 9.81); detector.sample(20, 13.0)
        assertNull(detector.sample(1000, 9.81))
    }
    private fun corner(p: ReferenceTurnPipeline, start: Long, rate: Double) {
        p.rotation(0.0, start)
        for (t in 20L..1000L step 20) assertTrue(p.rotation(rate, start+t).isEmpty())
        for (t in 1020L..1320L step 20) assertTrue(p.rotation(0.0, start+t).isEmpty())
    }
    @Test fun settledCornerPrecedesItsTwoConfirmingSteps() {
        val p = ReferenceTurnPipeline(); corner(p, 0, 90.0)
        assertTrue(p.step(1500).isEmpty())
        val events = p.step(2000)
        assertEquals(3, events.size)
        assertEquals(90.0, (events[0] as WalkEvent.Turn).degrees, .001)
        assertEquals(listOf(WalkEvent.Step, WalkEvent.Step), events.drop(1))
    }
    @Test fun lookingLeftThenRightWithoutWalkingCreatesNoCorner() {
        val p = ReferenceTurnPipeline(); corner(p, 0, 90.0); corner(p, 1500, -90.0)
        assertTrue(p.step(3000).isEmpty())
        assertEquals(listOf(WalkEvent.Step, WalkEvent.Step), p.step(3500))
    }
    @Test fun expiredCandidateReleasesEveryStep() {
        val p = ReferenceTurnPipeline(); corner(p, 0, 90.0)
        p.step(1500)
        assertEquals(listOf(WalkEvent.Step, WalkEvent.Step), p.step(10000))
        assertTrue(p.reset().isEmpty())
    }
    @Test fun pauseReleasesHeldStepAndDropsTurn() {
        val p = ReferenceTurnPipeline(); corner(p, 0, 90.0); p.step(1500)
        assertEquals(listOf(WalkEvent.Step), p.reset())
        assertEquals(listOf(WalkEvent.Step), p.step(2000))
    }
    @Test fun isolatedHandlingPeaksStayAtZeroAndWalkingRetainsTheFirstFootfall() {
        val cadence = WalkingCadence()
        assertTrue(cadence.peak(0).isEmpty())
        assertTrue(cadence.peak(2000).isEmpty())
        assertTrue(cadence.peak(4000).isEmpty())
        assertEquals(listOf(4000L,4550L),cadence.peak(4550))
        assertEquals(listOf(5060L),cadence.peak(5060))
        assertTrue(cadence.peak(8000).isEmpty())
    }
    @Test fun replayQuietWalkingAcrossRecordingPauses() {
        var cadence = WalkingCadence()
        var detector = ReferenceStepDetector(true)
        var turns = ReferenceTurnPipeline(1)
        val events = mutableListOf<WalkEvent>()
        javaClass.getResourceAsStream("/reference-motion/s24-paused-walk.csv")!!.bufferedReader().useLines { rows ->
            rows.forEach { row ->
                val f = row.split(',')
                val t = f[1].toLong()
                when (f[0]) {
                    "start" -> { cadence = WalkingCadence(); detector = ReferenceStepDetector(true); turns = ReferenceTurnPipeline(1) }
                    "stop" -> events.addAll(turns.reset())
                    "motion" -> {
                        detector.sample(t, f[2].toDouble())?.let { peak -> 
                            val steps = cadence.peak(peak)
                            if (steps.isEmpty()) events.addAll(turns.confirmRecordingHeading(peak))
                            steps.forEach { events.addAll(turns.step(it)) } }
                        events.addAll(turns.rotation(f[3].toDouble(), t))
                    }
                }
            }
        }
        val count = events.count { it == WalkEvent.Step }
        val angles = events.filterIsInstance<WalkEvent.Turn>().map { it.degrees }
        println("S24 replay steps=$count turns=$angles")
        assertTrue(count > 15)
        assertEquals(2, angles.size)
        assertTrue(angles.all { it in 60.0..115.0 })
    }}
