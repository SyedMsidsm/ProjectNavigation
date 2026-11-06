package `in`.mite.rain

import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.platform.FloorPackStore
import `in`.mite.rain.core.PlannerKind
import `in`.mite.rain.core.RoutePlanner
import `in`.mite.rain.core.RoomNameMatcher
import org.junit.Assert.*
import org.junit.Test

class GroundFloorTest {
    private fun floor() = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("ground-floor.json").use { FloorPackStore.parse(FloorPackStore.readLimited(it)).floor }

    @Test fun drawingProvenanceAndUnknownSignsArePreserved() {
        val floor = floor()
        assertEquals("plan_derived_floor", floor.source)
        assertFalse(floor.synthetic)
        assertTrue(floor.graph.nodes.size > 50)
        assertEquals(3, floor.graph.nodes.values.count { it.type == `in`.mite.rain.core.NodeType.STAIRS })
        assertTrue(floor.graph.nodes.values.none { it.isRecognizable })
    }

    @Test fun allNamedDestinationsHaveReturnRoutesWithoutTransitingOtherRooms() {
        val graph = floor().graph
        val planner = RoutePlanner(graph)
        val destinations = graph.nodes.values.filter { it.roomName.isNotBlank() && it.id != "ENT" }
        for (destination in destinations) for (kind in PlannerKind.entries) {
            for ((a,b) in listOf("ENT" to destination.id, destination.id to "ENT")) {
                val route = requireNotNull(planner.plan(a,b,kind))
                assertTrue(route.totalLengthM > 0)
                assertTrue(route.nodes.drop(1).dropLast(1).none { graph.nodes.getValue(it).roomName.isNotBlank() })
                assertTrue(route.scheduledPrompts.isEmpty())
            }
        }
    }

    @Test fun classroomCodesAreDistinctAndDuplicateExamLabelsRequireConfirmation() {
        val matcher = RoomNameMatcher(floor().graph.nodes.values)
        for (i in 1..7) {
            val code = "A%03d".format(i)
            val result = matcher.match("LH-$code")
            assertTrue("$code should match",result.accepted)
            assertEquals(code,result.best?.nodeId)
        }
        val ambiguous = matcher.match("EXAMINATION CENTRE")
        assertFalse(ambiguous.accepted)
        assertEquals(setOf("EXAM_W","EXAM_S"),ambiguous.topCandidates.take(2).map { it.nodeId }.toSet())
        assertFalse(matcher.match("LH-A008").accepted)
    }
}
