package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class TrailWorkflowTest {
    @Test fun confirmedReturnReusesTheEntranceCorridorThroughItsMarkedPlaces() {
        val r = TrailRecorder(.7,"Entrance")
        repeat(4){r.step()}; r.mark("Kola")
        repeat(5){r.step()}; r.mark("Yusuf")
        repeat(7){r.step()}; r.turn(90.0)
        repeat(5){r.step()}; r.turn(90.0)
        repeat(5){r.step()}; r.turn(90.0)
        repeat(3){r.step()}; r.turn(-90.0)
        repeat(11){r.step()}
        val graph = r.finish().closeRecordedReturn("T1",r.currentPlaceId)
        val junction = graph.nodes.values.single { graph.degree(it.id)==3 }
        assertEquals(0.0,junction.xM,.001)
        assertEquals(listOf(junction.id,"T3","T2","T1"),RoutePlanner(graph).plan(junction.id,"T1")!!.nodes)
        assertFalse(graph.nodes.values.any { it.roomName=="End of walk" })
    }
    @Test fun spokenPrefixesSpacingAndTranspositionResolveAgainstSavedPlaces() {
        val places = listOf(Node("W",NodeType.WASHROOM,"Washroom"), Node("L",NodeType.JUNCTION,"Library"))
        listOf("I'm at the wash room", "navigate to washrom please", "washorom").forEach {
            assertEquals(it, "W", DestinationSearch.matches(it,places).single().id)
        }
        assertTrue(DestinationSearch.matches("I want to wash my face",places).isEmpty())
        assertEquals(listOf("W"),DestinationSearch.semanticCatalog("I want to wash my face",places).map { it.id })
        assertTrue(DestinationSearch.semanticCatalog("wash my hands",places.drop(1)).isEmpty())
    }
    @Test fun savingUnmarkedWalkCreatesSelectableDestination() {
        val r = TrailRecorder(1.0, "Entrance")
        repeat(6) { r.step() }
        val g = r.finish()
        val end = DestinationSearch.matches("End of walk", g.nodes.values).single()
        assertEquals(6.0, RoutePlanner(g).plan("T1", end.id)!!.totalLengthM, .001)
        assertEquals(g.nodes, r.finish().nodes)
        assertEquals(g.edges, r.finish().edges)
    }
    @Test fun savingAtMarkedPlacePreservesNameAndGeometry() {
        val r = TrailRecorder(1.0, "Entrance")
        repeat(6) { r.step() }; r.mark("Office")
        val g = r.finish()
        assertEquals(2, g.nodes.size)
        assertEquals("Office", g.nodes.values.last().roomName)
        assertEquals(1, g.edges.size)
    }
    @Test fun endpointNameDoesNotCollideWithEarlierPlace() {
        val r = TrailRecorder(1.0, "End of walk")
        repeat(3) { r.step() }
        assertEquals("End of walk 2", r.finish().nodes.values.last().roomName)
    }

    @Test fun rejectedDuplicateMarkCanStillJoinExistingPlace() {
        val r=TrailRecorder(1.0,"Lobby")
        repeat(5){r.step()}; r.mark("Office"); r.turn(180.0); repeat(5){r.step()}
        try { r.mark("Lobby"); fail("Duplicate place accepted") } catch(_:IllegalArgumentException) {}
        r.join("T1")
        assertEquals(2,r.finish().nodes.size)
        assertEquals(10.0,r.finish().edges.sumOf{it.lengthM},.001)
    }
    @Test fun rejectedAliasDoesNotSplitTheRecordedSegment() {
        val r=TrailRecorder(1.0,"Lobby"); repeat(5){r.step()}
        try { r.mark("Office",listOf("bad|alias")); fail("Invalid alias accepted") } catch(_:IllegalArgumentException) {}
        repeat(5){r.step()};r.mark("Office")
        assertEquals(2,r.finish().nodes.size)
        assertEquals(10.0,r.finish().edges.single().lengthM,.001)
    }
    @Test fun walkingAwayCannotCreateASecondPlaceWithStartingName() {
        val r=TrailRecorder(1.0,"Lobby"); repeat(5){r.step()}
        try { r.mark("Lobby"); fail("Duplicate start accepted") } catch(_:IllegalArgumentException) {}
        r.mark("Office"); assertEquals(listOf("Lobby","Office"),r.finish().nodes.values.map{it.roomName})
    }
    @Test fun confirmedSensorTurnReplaysThroughRecordedRoute() {
        val r=TrailRecorder(1.0,"Start"); repeat(5){r.step()};r.turn(90.0);repeat(5){r.step()};r.mark("End")
        val g=r.finish();val t=NavigationTracker(g,RoutePlanner(g).plan("T1","T3")!!,1.0)
        repeat(5){t.step()}
        val s=ConfirmedTurnSensor();rotate(s,0,90.0);s.rotation(0.0,1200)
        val events=s.step(1400)+s.step(1900)
        events.forEach { when(it){WalkEvent.Step->t.step();is WalkEvent.Turn->t.turn(it.degrees)} }
        repeat(3){t.step()};assertTrue(t.snapshot().arrived);assertNull(t.snapshot().offRouteReason)
    }
    @Test fun recordingPreservesLengthsAndAliases() {
        val r = TrailRecorder(.7, "Entrance")
        repeat(10) { r.step() }; r.turn(90.0); repeat(5) { r.step() }; r.mark("Office", listOf("Fees"))
        val g = r.finish()
        assertEquals(3, g.nodes.size); assertEquals(10.5, g.edges.sumOf { it.lengthM }, .001)
        assertEquals("Office", DestinationSearch.matches("fees",g.nodes.values).single().roomName)
        assertEquals("Office", DestinationSearch.matches("ofice",g.nodes.values).single().roomName)
        assertNotNull(RoutePlanner(g).plan("T1",g.nodes.values.last().id))
    }
    @Test fun loopRetainsMeasuredDistances() {
        val r = TrailRecorder(1.0,"Start")
        repeat(3) { repeat(5) { r.step() }; r.turn(90.0) }
        repeat(5) { r.step() }; r.join("T1")
        val g = r.finish(); assertEquals(4,g.nodes.size); assertEquals(20.0,g.edges.sumOf { it.lengthM },.001)
    }
    @Test fun floorTransitionWaitsForUser() {
        val r=TrailRecorder(1.0,"Lobby"); repeat(3){r.step()}; r.changeFloor(1,"lift"); repeat(3){r.step()}; r.mark("Library")
        val g=r.finish(); val route=RoutePlanner(g).plan("T1",g.nodes.values.last().id)!!
        val tracker=NavigationTracker(g,route,1.0)
        repeat(3){tracker.step()}; assertTrue(tracker.snapshot().floorChangePending)
        repeat(20){tracker.step()}; assertTrue(tracker.snapshot().floorChangePending)
        tracker.confirmFloorChange(); repeat(3){tracker.step()}; assertTrue(tracker.snapshot().arrived)
        assertFalse(tracker.snapshot().floorChangePending)
    }
    @Test fun zeroLengthDestinationIsSafe() {
        val r=TrailRecorder(1.0,"Start"); repeat(3){r.step()}; r.mark("End"); val g=r.finish()
        val tracker=NavigationTracker(g,RoutePlanner(g).plan("T1","T1")!!)
        assertTrue(tracker.snapshot().arrived)
    }
    @Test fun modelCannotCreateAPlaceOrExecuteInstructions() {
        val n=listOf(Node("A",NodeType.JUNCTION,"Office"))
        assertNull(DestinationSearch.validatedSuggestion("Go to A",n)); assertNull(DestinationSearch.validatedSuggestion("B",n))
        assertEquals("A",DestinationSearch.validatedSuggestion("A",n)!!.id)
    }
    @Test fun ambiguousNamesStayAmbiguous() {
        val n=listOf(Node("A",NodeType.JUNCTION,"Lab"),Node("B",NodeType.JUNCTION,"Lab",floor=1))
        assertEquals(2,DestinationSearch.matches("lab",n).size)
    }
    private fun rotate(s: ConfirmedTurnSensor, from: Long, rate: Double) { for(i in 0..10) s.rotation(rate,from+i*100) }
    @Test fun twoStepsConfirmTurnBeforeForwardSteps() {
        val s=ConfirmedTurnSensor(); rotate(s,0,90.0); s.rotation(0.0,1200)
        assertTrue(s.step(1400).isEmpty())
        assertEquals(listOf(WalkEvent.Turn(90.0),WalkEvent.Step,WalkEvent.Step),s.step(1900))
    }
    @Test fun lookingAroundDoesNotCreateATurn() {
        val s=ConfirmedTurnSensor(); rotate(s,0,90.0); rotate(s,1100,-90.0)
        assertEquals(listOf(WalkEvent.Step),s.step(2500))
    }
    @Test fun expiredCandidateReleasesPendingSteps() {
        val s=ConfirmedTurnSensor(); rotate(s,0,90.0); s.rotation(0.0,1200); s.step(1500)
        assertEquals(listOf(WalkEvent.Step,WalkEvent.Step),s.step(10000))
    }
    @Test fun confidenceDropsAndConfirmationRestoresIt() {
        val r=TrailRecorder(1.0,"Start"); repeat(30){r.step()}; r.mark("End"); val g=r.finish()
        val t=NavigationTracker(g,RoutePlanner(g).plan("T1",g.nodes.values.last().id)!!)
        val initial=t.snapshot().confidence; repeat(15){t.step()}; assertTrue(t.snapshot().confidence<initial)
        assertEquals(initial,t.fixAtNode("T1").confidence,.001)
    }
}
