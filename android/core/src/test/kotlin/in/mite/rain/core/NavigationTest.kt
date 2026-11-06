package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test fun explicitBacktrackingCountsWithoutRequiringAHeadingReversal() {
        val tracker=tracker()
        val overshot=tracker.advance(15.0)
        assertEquals("missed_turn",overshot.offRouteReason)
        assertEquals(8,overshot.returnSteps)
        assertTrue(tracker.beginReturn().returning)
        repeat(8) { tracker.step() }
        assertEquals(0,tracker.snapshot().returnSteps)
        assertEquals("missed_turn",tracker.snapshot().offRouteReason)
        assertNull(tracker.fixAtNode("B").offRouteReason)
        tracker.advance(17.0)
        assertEquals("overrun",tracker.snapshot().offRouteReason)
        tracker.beginReturn()
        assertEquals(0,tracker.advance(7.0).returnSteps)
        assertTrue(tracker.fixAtNode("C").arrived)
    }
    @Test fun classroomMarksShareOneInstructionUntilTheRealTurn() {
        val recording = TrailRecorder(1.0,"Entrance")
        repeat(5) { recording.step() }; recording.mark("Class 1")
        repeat(5) { recording.step() }; recording.mark("Class 2")
        repeat(5) { recording.step() }; recording.turn(90.0)
        repeat(5) { recording.step() }; recording.mark("Library")
        val graph = recording.finish()
        val route = RoutePlanner(graph).plan("T1",recording.currentPlaceId)!!
        val tracker = NavigationTracker(graph,route,1.0)
        assertEquals("Walk 15 m, then turn right.",tracker.snapshot().instruction)
        val end = route.guidanceEndIndex(0)
        val halfway = tracker.advance(7.0)
        assertEquals(end,route.guidanceEndIndex(halfway.currentArcIndex))
        assertEquals(8.0,route.distanceToGuidanceEnd(halfway),.001)
        assertFalse(halfway.instruction.contains("Class"))
        tracker.advance(8.0)
        tracker.turn(90.0)
        assertEquals("Walk straight for 5 m to Library.",tracker.snapshot().instruction)
        assertTrue(tracker.advance(5.0).arrived)
        val reverse = NavigationTracker(graph,RoutePlanner(graph).plan(recording.currentPlaceId,"T1")!!,1.0)
        reverse.advance(5.0); reverse.turn(-90.0)
        assertEquals("Walk straight for 15 m to Entrance.",reverse.snapshot().instruction)
    }
    private fun graph(angle: Double = 90.0): Graph = Graph(listOf(node("A", "A"), node("B", "B"), node("C", "C")),
        listOf(Edge("AB", "A", "B", 10.0, 0.0), Edge("BC", "B", "C", 10.0, angle)))
    private fun tracker(angle: Double = 90.0): NavigationTracker {
        val graph = graph(angle)
        return NavigationTracker(graph, RoutePlanner(graph).plan("A", "C", PlannerKind.SHORTEST)!!)
    }

    @Test fun `planned right turn requires matching evidence before advancing`() {
        val tracker = tracker()
        assertEquals(0.7, tracker.step().offsetM, 1e-9)
        tracker.advance(9.3)
        assertEquals(0, tracker.snapshot().currentArcIndex)
        assertTrue(tracker.snapshot().instruction.contains("Turn right"))
        val turned = tracker.turn(90.0)
        assertNull(turned.offRouteReason)
        assertEquals(1, turned.currentArcIndex)
        assertEquals(0.0, turned.offsetM, 1e-9)
        assertTrue(tracker.advance(10.0).arrived)
    }

    @Test fun `turn evidence up to three metres before junction is retained`() {
        val tracker = tracker()
        tracker.advance(7.0)
        tracker.turn(70.0)
        assertEquals(1, tracker.advance(3.0).currentArcIndex)
        assertNull(tracker.snapshot().offRouteReason)
    }

    @Test fun `wide turns accumulate partial sensor events before taking the branch`() {
        val tracker = tracker(130.0)
        tracker.advance(10.0)
        assertNull(tracker.turn(51.0).offRouteReason)
        assertEquals(0, tracker.snapshot().currentArcIndex)
        assertEquals(1, tracker.turn(51.0).currentArcIndex)
        assertEquals(2.0, tracker.snapshot().driftM, 1e-9)
    }

    @Test fun `U turns accept clockwise or counterclockwise partial evidence`() {
        listOf(51.0, -51.0).forEach { partial ->
            val tracker = tracker(180.0)
            tracker.advance(10.0)
            assertNull(tracker.turn(partial).offRouteReason)
            assertNull(tracker.turn(partial).offRouteReason)
            assertEquals(0, tracker.snapshot().currentArcIndex)
            assertEquals(1, tracker.turn(partial).currentArcIndex)
            assertEquals(2.0, tracker.snapshot().driftM, 1e-9)
        }
    }

    @Test fun `slight bend does not wait for an impossible subthreshold sensor event`() {
        val tracker = tracker(40.0)
        val state = tracker.advance(10.7)
        assertEquals(1, state.currentArcIndex)
        assertEquals(0.7, state.offsetM, 1e-9)
        assertNull(state.offRouteReason)
    }

    @Test fun `missing a turn by more than four metres triggers scan`() {
        val tracker = tracker()
        assertNull(tracker.advance(14.0).offRouteReason)
        val state = tracker.advance(0.1)
        assertEquals("missed_turn", state.offRouteReason)
        assertEquals("off_route", state.promptReason)
        assertEquals(0, state.currentArcIndex)
    }

    @Test fun `mid segment unexpected and wrong direction turns are flagged`() {
        val middle = tracker()
        middle.advance(5.0)
        assertEquals("unexpected_turn", middle.turn(90.0).offRouteReason)
        val wrongDirection = tracker()
        wrongDirection.advance(8.0)
        assertEquals("unexpected_turn", wrongDirection.turn(-90.0).offRouteReason)
        val straight = tracker(0.0)
        straight.advance(8.0)
        assertEquals("unexpected_turn", straight.turn(90.0).offRouteReason)
    }

    @Test fun `walking beyond estimated arrival can still detect overrun`() {
        val tracker = tracker(0.0)
        assertTrue(tracker.advance(20.0).arrived)
        assertTrue(tracker.advance(6.0).arrived)
        val state = tracker.advance(0.1)
        assertFalse(state.arrived)
        assertEquals("overrun", state.offRouteReason)
        assertEquals("off_route", state.promptReason)
    }

    @Test fun `recognized fix clears off route and accumulated drift`() {
        val tracker = tracker()
        tracker.advance(5.0)
        tracker.turn(90.0)
        assertNotNull(tracker.snapshot().offRouteReason)
        val fixed = tracker.fixAtNode("B", 0.95)
        assertNull(fixed.offRouteReason)
        assertNull(fixed.promptReason)
        assertEquals(0.0, fixed.driftM, 0.0)
        assertEquals(1, fixed.currentArcIndex)
        assertEquals(10.0, fixed.remainingDistanceM, 0.0)
        fails<IllegalArgumentException> { tracker.fixAtNode("ELSEWHERE") }
    }

    @Test fun `zero edge route starts arrived and remains stable`() {
        val graph = graph()
        val tracker = NavigationTracker(graph, RoutePlanner(graph).plan("B", "B")!!)
        assertTrue(tracker.snapshot().arrived)
        assertEquals("B", tracker.step().currentNodeId)
        assertTrue(tracker.fixAtNode("B").arrived)
    }

    @Test fun `scheduled prompt is available immediately at a signed starting node`() {
        val graph = graph()
        val settings = PlannerSettings(askCost = 0.0)
        val route = RoutePlanner(graph, RiskModel { 1.0 }).plan("A", "C", settings = settings)!!
        val tracker = NavigationTracker(graph, route, settings = settings)
        assertEquals("scheduled", tracker.snapshot().promptReason)
        assertNull(tracker.dismissPrompt().promptReason)
        assertNull(tracker.step().promptReason)
    }

    @Test fun `actual floor schedules a useful junction scan from room 210 to lab 204`() {
        val graph = sampleGraph()
        val route = RoutePlanner(graph).plan("N10", "N04")!!
        assertEquals(listOf("J01"), route.scheduledPrompts)
        val tracker = NavigationTracker(graph, route)
        assertNull(tracker.snapshot().promptReason)
        val state = tracker.advance(1.0)
        assertEquals("scheduled", state.promptReason)
        assertEquals("N10", state.currentNodeId)
    }

    @Test fun `prompt rate limit is fifteen metres with off route override`() {
        val policy = PromptPolicy()
        fun ask(distance: Double, offRoute: Boolean = false) = policy.evaluate(distance, distance, 0, 0.9, 2.0, 1.0, true, offRoute)
        assertEquals("scheduled", ask(0.0))
        assertNull(ask(14.9))
        assertEquals("scheduled", ask(15.0))
        assertEquals("off_route", ask(15.1, true))
    }

    @Test fun `no visible sign means no scheduled or uncertainty prompt`() {
        val policy = PromptPolicy(PlannerSettings(lambda = 100.0, askCost = 0.0))
        assertNull(policy.evaluate(100.0, 100.0, 10, 0.1, 1.0, 1.0, false))
    }

    @Test fun `uncertainty prompt compares cost only near a recognizable sign`() {
        val policy = PromptPolicy(PlannerSettings(lambda = 100.0, askCost = 6.0))
        assertEquals("uncertainty", policy.evaluate(100.0, 100.0, 10, 0.1, null, 1.0, true))
        val zeroLambda = PromptPolicy(PlannerSettings(lambda = 0.0, askCost = 0.0))
        assertNull(zeroLambda.evaluate(100.0, 100.0, 10, 0.1, null, 1.0, true))
    }

    @Test fun `fixed interval and never baselines have explicit behavior`() {
        val fixed = PromptPolicy(mode = PromptMode.FIXED_INTERVAL, fixedIntervalM = 20.0)
        fun ask(distance: Double) = fixed.evaluate(distance, distance, 0, 0.9, null, 0.1, true)
        assertNull(ask(19.9))
        assertEquals("fixed_interval", ask(20.0))
        assertNull(ask(39.9))
        assertEquals("fixed_interval", ask(40.0))
        val never = PromptPolicy(mode = PromptMode.NEVER)
        assertNull(never.evaluate(100.0, 100.0, 5, 0.1, 1.0, 1.0, true))
        assertEquals("off_route", never.evaluate(100.0, 100.0, 5, 0.1, null, 1.0, false, true))
    }

    @Test fun `turn detector handles yaw wrap and excludes expired history`() {
        val detector = TurnDetector()
        assertNull(detector.update(350.0, 0))
        assertNull(detector.update(10.0, 500))
        assertEquals(60.0, detector.update(50.0, 1000)!!, 1e-9)
        assertNull(detector.update(55.0, 4000))
        assertNull(detector.update(90.0, 4500))
        assertNull(detector.update(90.0, 100))
    }
}
