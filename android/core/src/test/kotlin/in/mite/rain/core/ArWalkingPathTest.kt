package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class ArWalkingPathTest {
    private var time = 0L
    private fun sample(p: ArWalkingPath, x: Double, z: Double): ArWalkingState {
        time += 100_000_000L
        return p.update(x, z, time)
    }
    @Test fun rollingWindowContinuesBeyondOriginalSixMetres() {
        val p = ArWalkingPath(listOf(20.0), listOf(270.0))
        for (i in 0..100) sample(p, 0.0, -i / 10.0)
        assertEquals(10.0, p.snapshot().alongM, 0.001)
        assertEquals(10.0, p.snapshot().remainingM, 0.001)
        assertTrue(p.arrows().all { it.z < -10 && it.z >= -16 })
        assertFalse(p.snapshot().arrived)
    }
    @Test fun followsRightThenLeftAndPreservesInitialOffset() {
        val p = ArWalkingPath(listOf(5.0, 3.0, 4.0), listOf(350.0, 80.0, 350.0), 2.0)
        for (i in 0..30) sample(p, 0.0, -i / 10.0)
        for (i in 1..30) sample(p, i / 10.0, -3.0)
        for (i in 1..40) sample(p, 3.0, -3.0 - i / 10.0)
        assertNull(p.snapshot().failure)
        assertEquals(2, p.snapshot().arcIndex)
        assertTrue(p.snapshot().arrived)
        assertTrue(p.arrows().isEmpty())
    }
    @Test fun poseJumpNeverAdvancesRouteAndLatchesFailure() {
        val p = ArWalkingPath(listOf(20.0), listOf(0.0))
        sample(p, 0.0, 0.0); sample(p, 0.0, -8.0)
        assertNotNull(p.snapshot().failure)
        assertEquals(0.0, p.snapshot().alongM, 0.001)
        sample(p, 0.0, -0.1)
        assertNotNull(p.snapshot().failure)
        assertTrue(p.arrows().isEmpty())
    }
    @Test fun roundedCornerDoesNotLeaveProgressStuckAtJunction() {
        val p = ArWalkingPath(listOf(3.0, 5.0), listOf(0.0, 90.0))
        for (i in 0..20) sample(p, 0.0, -i / 10.0)
        for (i in 1..10) sample(p, i / 10.0, -2.0 - i / 10.0)
        for (i in 11..40) sample(p, i / 10.0, -3.0)
        assertNull(p.snapshot().failure)
        assertEquals(1, p.snapshot().arcIndex)
        assertEquals(1.0, p.snapshot().remainingM, 0.001)
    }
    @Test fun crossingCannotJumpToLaterLeg() {
        val p = ArWalkingPath(listOf(4.0, 4.0, 8.0, 8.0), listOf(0.0, 90.0, 180.0, 270.0))
        for (i in 0..20) sample(p, 0.0, -i / 10.0)
        assertEquals(0, p.snapshot().arcIndex)
        assertEquals(22.0, p.snapshot().remainingM, 0.001)
    }
    @Test fun wrongCorridorHidesArrowsAndRequiresFix() {
        val p = ArWalkingPath(listOf(10.0), listOf(0.0))
        for (i in 0..40) sample(p, i / 10.0, 0.0)
        assertNotNull(p.snapshot().failure)
        assertTrue(p.arrows().isEmpty())
        assertEquals(0.0, p.snapshot().alongM, 0.001)
    }
    @Test fun walkingPastTurnCannotAdvanceAlongNextCorridor() {
        val p = ArWalkingPath(listOf(3.0, 5.0), listOf(0.0, 90.0))
        for (i in 0..70) sample(p, 0.0, -i / 10.0)
        assertNotNull(p.snapshot().failure)
        assertEquals(0, p.snapshot().arcIndex)
    }
    @Test fun backwardMovementDoesNotConsumeRemainingRoute() {
        val p = ArWalkingPath(listOf(10.0), listOf(0.0))
        for (i in 0..40) sample(p, 0.0, -i / 10.0)
        for (i in 39 downTo 0) sample(p, 0.0, -i / 10.0)
        assertNotNull(p.snapshot().failure)
        assertEquals(6.0, p.snapshot().remainingM, 0.001)
    }
    @Test fun supportsUTurnOnlyAfterReachingItsEndpoint() {
        val p = ArWalkingPath(listOf(3.0, 3.0), listOf(0.0, 180.0))
        for (i in 0..30) sample(p, 0.0, -i / 10.0)
        for (i in 29 downTo 0) sample(p, 0.0, -i / 10.0)
        assertNull(p.snapshot().failure)
        assertEquals(1, p.snapshot().arcIndex)
        assertTrue(p.snapshot().arrived)
    }
    @Test fun interruptionAndStaleFramesDoNotInventProgress() {
        val p = ArWalkingPath(listOf(10.0), listOf(0.0))
        p.update(0.0, 0.0, 100L)
        p.update(0.0, -2.0, 100L)
        assertEquals(0.0, p.snapshot().alongM, 0.001)
        p.update(0.0, -0.1, 2_000_000_100L)
        assertNotNull(p.snapshot().failure)
    }
    @Test fun mapTrackerResumesWithoutSyntheticTurnsOrDoubleCounting() {
        val graph = Graph(listOf(node("A", "A"), node("B", "B"), node("C", "C")),
            listOf(Edge("AB", "A", "B", 10.0, 0.0), Edge("BC", "B", "C", 10.0, 90.0)))
        val tracker = NavigationTracker(graph, RoutePlanner(graph).plan("A", "C", PlannerKind.SHORTEST)!!)
        tracker.advance(2.0)
        val state = tracker.applyArProgress(1, 4.0, false)
        assertEquals(14.0, state.distanceWalkedM, 0.001)
        assertEquals(6.0, state.remainingDistanceM, 0.001)
        assertEquals(4.7, tracker.step().offsetM, 0.001)
        assertNotNull(tracker.applyArProgress(1, 4.7, true).offRouteReason)
    }
}
