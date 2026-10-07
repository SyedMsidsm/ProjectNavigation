package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

internal fun node(id: String, sign: String = "", type: NodeType = NodeType.CORRIDOR_POINT) =
    Node(id, type, roomName = id, signText = sign,
        signCondition = if (sign.isBlank()) SignCondition.MISSING else SignCondition.PRINTED_CLEAR)

internal fun sampleGraph(): Graph {
    fun asset(name: String) = requireNotNull(object {}.javaClass.classLoader.getResourceAsStream(name)) {
        "Missing actual app asset $name"
    }.bufferedReader().use { it.readText() }
    return CsvGraphLoader.load(asset("rooms.csv"), asset("edges.csv"))
}

internal inline fun <reified T : Throwable> fails(block: () -> Unit) {
    try { block() } catch (error: Throwable) { if (error is T) return else throw error }
    fail("Expected ${T::class.java.simpleName}")
}

class GraphAndPlannerTest {
    @Test fun `app floor has distinct shorter and lower risk demo routes`() {
        val planner = RoutePlanner(sampleGraph())
        val shortest = planner.plan("ENT", "N04", PlannerKind.SHORTEST)!!
        val simplest = planner.plan("ENT", "N04", PlannerKind.SIMPLEST)!!
        val risk = planner.plan("ENT", "N04", PlannerKind.RISK)!!
        assertEquals(35.0, shortest.totalLengthM, 1e-9)
        assertEquals(listOf("ENT", "N01", "J01", "N02", "J02", "N03", "J03", "N04"), shortest.nodes)
        assertEquals(40.0, risk.totalLengthM, 1e-9)
        assertEquals(listOf("ENT", "N05", "J04", "N06", "N07", "J03", "N04"), risk.nodes)
        assertEquals(risk.nodes, simplest.nodes)
        assertTrue(risk.expectedErrors < shortest.expectedErrors)
    }

    @Test fun `lambda zero exactly matches shortest for every pair on actual app floor`() {
        val graph = sampleGraph()
        val planner = RoutePlanner(graph)
        graph.nodes.keys.forEach { from -> graph.nodes.keys.forEach { to ->
            val shortest = planner.plan(from, to, PlannerKind.SHORTEST)!!
            val risk = planner.plan(from, to, PlannerKind.RISK, PlannerSettings(lambda = 0.0))!!
            assertEquals("$from to $to", shortest.arcs.map { it.id }, risk.arcs.map { it.id })
            assertEquals(shortest.totalCost, risk.totalCost, 0.0)
            assertTrue(risk.scheduledPrompts.isEmpty())
        } }
    }

    @Test fun `turn aware search retains a longer arrival with a better heading`() {
        val graph = Graph(listOf("S", "B", "J", "D").map { node(it) }, listOf(
            Edge("SJ", "S", "J", 1.0, 90.0, bidirectional = false),
            Edge("SB", "S", "B", 2.0, 0.0, bidirectional = false),
            Edge("BJ", "B", "J", 2.0, 0.0, bidirectional = false),
            Edge("JD", "J", "D", 1.0, 0.0, bidirectional = false)))
        assertEquals(listOf("S", "J", "D"), RoutePlanner(graph).plan("S", "D", PlannerKind.SHORTEST)!!.nodes)
        val result = RoutePlanner(graph).plan("S", "D", PlannerKind.SIMPLEST)!!
        assertEquals(listOf("S", "B", "J", "D"), result.nodes)
        assertEquals(5.0, result.totalCost, 0.0)
    }

    @Test fun `one way connected graph can have an unreachable route`() {
        val graph = Graph(listOf(node("A"), node("B")), listOf(Edge("AB", "A", "B", 5.0, 90.0, bidirectional = false)))
        assertNull(RoutePlanner(graph).plan("B", "A"))
        assertNotNull(RoutePlanner(graph).plan("A", "B"))
    }

    @Test fun `reversing a bidirectional edge reverses its bearing`() {
        val graph = Graph(listOf(node("A"), node("B")), listOf(Edge("AB", "A", "B", 5.0, 350.0)))
        assertEquals(350.0, graph.outgoing("A").single().bearingDeg, 0.0)
        assertEquals(170.0, graph.outgoing("B").single().bearingDeg, 0.0)
        assertEquals(20.0, signedTurnDegrees(350.0, 10.0), 0.0)
        assertEquals(-20.0, signedTurnDegrees(10.0, 350.0), 0.0)
    }

    @Test fun `missing signs cannot receive fictitious scan benefit`() {
        val graph = Graph(listOf(node("A"), node("B", "ROOM B")), listOf(Edge("AB", "A", "B", 10.0, 0.0)))
        val planner = RoutePlanner(graph, RiskModel { 1.0 })
        val missing = planner.plan("A", "B", settings = PlannerSettings(askCost = 0.0))!!
        assertTrue(missing.scheduledPrompts.isEmpty())
        assertEquals(50.0, missing.totalCost, 0.0)
        val signed = planner.plan("B", "A", settings = PlannerSettings(askCost = 0.0))!!
        assertEquals(listOf("B"), signed.scheduledPrompts)
        assertEquals(22.0, signed.totalCost, 1e-9)
        assertEquals(0.3, signed.expectedErrors, 1e-9)
    }

    @Test fun `equal ask and avoid costs do not schedule unnecessary prompt`() {
        val graph = Graph(listOf(node("A", "A"), node("B", "B")), listOf(Edge("AB", "A", "B", 10.0, 0.0)))
        val route = RoutePlanner(graph, RiskModel { 1.0 }).plan("A", "B", settings = PlannerSettings(40.0, 20.0, 0.5))!!
        assertTrue(route.scheduledPrompts.isEmpty())
    }

    @Test fun `avoiding stairs can make a directed route unreachable`() {
        val graph = Graph(listOf(node("A"), node("B")), listOf(Edge("AB", "A", "B", 4.0, 0.0, hasStairs = true)))
        assertNull(RoutePlanner(graph).plan("A", "B", settings = PlannerSettings(avoidStairs = true)))
    }

    @Test fun `zero length self route has no transitions`() {
        val result = RoutePlanner(sampleGraph()).plan("N04", "N04")!!
        assertEquals(listOf("N04"), result.nodes)
        assertEquals(0.0, result.totalCost, 0.0)
        assertTrue(result.arcs.isEmpty())
    }

    @Test fun `invalid planner parameters and model outputs fail explicitly`() {
        fails<IllegalArgumentException> { PlannerSettings(lambda = -1.0) }
        fails<IllegalArgumentException> { PlannerSettings(askCost = Double.NaN) }
        fails<IllegalArgumentException> { PlannerSettings(scanEffectiveness = 1.1) }
        fails<IllegalArgumentException> { RoutePlanner(sampleGraph(), RiskModel { Double.NaN }).plan("ENT", "N04") }
        fails<IllegalArgumentException> { RoutePlanner(sampleGraph()).plan("UNKNOWN", "N04") }
    }

    @Test fun `v0 scores are finite and preserve documented illustrative ranges`() {
        val model = HandWeightedRiskModel()
        val difficult = model.predict(RiskFeatures(2.0, 1.0, 0.8, 0.0, 8.0))
        val easy = model.predict(RiskFeatures(0.0, 0.0, 0.05, 0.0, 8.0))
        assertTrue(difficult in 0.35..0.45)
        assertTrue(easy < 0.05)
        assertEquals(1.0, sigmoid(1000.0), 0.0)
        assertEquals(0.0, sigmoid(-1000.0), 0.0)
    }

    @Test fun `csv accepts BOM escaped quotes commas and multiline notes`() {
        val rooms = "\uFEFFnode_id,node_type,room_name,sign_text,aliases,x_m,y_m,corridor_side,sign_condition,similar_doors_nearby,notes\r\n" +
            "A,door,\"Room, A\",\"ROOM \"\"A\"\"\",A,0,0,left,printed_clear,0,\"first\nsecond\"\r\n"
        val edges = "edge_id,from_node,to_node,length_m,bearing_deg,corridor_id,bidirectional,has_stairs\n"
        val graph = CsvGraphLoader.load(rooms, edges)
        assertEquals("Room, A", graph.nodes.getValue("A").roomName)
        assertEquals("ROOM \"A\"", graph.nodes.getValue("A").signText)
        assertEquals("first\nsecond", graph.nodes.getValue("A").notes)
    }

    @Test fun `csv rejects malformed quoting and missing schema`() {
        fails<GraphValidationException> { CsvGraphLoader.parseCsv("a,\"unclosed") }
        fails<GraphValidationException> { CsvGraphLoader.parseCsv("a,\"closed\"junk") }
        fails<GraphValidationException> { CsvGraphLoader.load("node_id,node_id\nA,A", "") }
    }

    @Test fun `graph rejects disconnected duplicates unknown endpoints nonfinite and invalid lengths`() {
        fails<GraphValidationException> { Graph(listOf(node("A"), node("B")), emptyList()) }
        fails<GraphValidationException> { Graph(listOf(node("A"), node("A")), emptyList()) }
        fails<GraphValidationException> { Graph(listOf(node("A")), listOf(Edge("AB", "A", "B", 1.0, 0.0))) }
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { length ->
            fails<GraphValidationException> { Graph(listOf(node("A"), node("B")), listOf(Edge("AB", "A", "B", length, 0.0))) }
        }
        fails<GraphValidationException> { Graph(listOf(node("A").copy(xM = Double.NaN)), emptyList()) }
    }

    @Test fun `door nodes cannot disguise junctions`() {
        val rooms = listOf(node("A", type = NodeType.DOOR), node("B"), node("C"), node("D"))
        fails<GraphValidationException> { Graph(rooms, listOf("B", "C", "D").map { Edge("A$it", "A", it, 1.0, 0.0) }) }
    }
}
