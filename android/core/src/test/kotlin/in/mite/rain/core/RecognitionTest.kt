package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class RecognitionTest {
    @Test fun `actual room label and digit confusions resolve to correct room`() {
        val matcher = RoomNameMatcher(sampleGraph().nodes.values)
        val exact = matcher.match("CSE LAB 204")
        assertTrue(exact.accepted)
        assertEquals("N04", exact.best!!.nodeId)
        val confused = matcher.match("Room 2O1")
        assertTrue(confused.accepted)
        assertEquals("N01", confused.best!!.nodeId)
    }

    @Test fun `conflicting numbers are rejected despite common label prefixes`() {
        val rooms = listOf(node("A", "CLASSROOM 201").copy(roomName = "Classroom 201"))
        val result = RoomNameMatcher(rooms).match("CLASSROOM 999")
        assertFalse(result.accepted)
        assertTrue(result.best!!.score < 0.5)
    }

    @Test fun `generic alias cannot bypass conflicting room number`() {
        val room = node("A", "COMPUTER LAB 204").copy(roomName = "Computer Lab 204", aliases = listOf("Computer Lab"))
        val matcher = RoomNameMatcher(listOf(room))
        assertFalse(matcher.match("Computer Lab 999").accepted)
        assertFalse(matcher.match("Computer Lab\n999").accepted)
        assertTrue(matcher.match("Computer Lab\n204").accepted)
    }

    @Test fun `identical room labels remain ambiguous even when scores are perfect`() {
        val matcher = RoomNameMatcher(listOf(node("A", "CSE LAB").copy(roomName = "CSE Lab"), node("B", "CSE LAB").copy(roomName = "CSE Lab")))
        val result = matcher.match("CSE LAB")
        assertFalse(result.accepted)
        assertEquals(1.0, result.best!!.score, 0.0)
        assertEquals(0.5, result.best!!.probability, 1e-9)
        assertEquals(0.0, result.margin, 1e-9)
    }

    @Test fun `empty and unrelated text do not create location fixes`() {
        val matcher = RoomNameMatcher(sampleGraph().nodes.values)
        assertFalse(matcher.match("   ").accepted)
        assertTrue(matcher.match("   ").topCandidates.isEmpty())
        assertFalse(matcher.match("XYZQQQVVV").accepted)
        assertFalse(RoomNameMatcher(emptyList()).match("ROOM 201").accepted)
    }

    @Test fun `weighted edits include RN and M and preserve ordinary word spelling`() {
        assertEquals(0.3, weightedLevenshtein("RNO", "MO"), 1e-9)
        assertEquals(0.3, weightedLevenshtein("MO", "RNO"), 1e-9)
        assertEquals(0.3, weightedLevenshtein("O", "0"), 1e-9)
        assertEquals("CSE LAB 201", normaliseText("CSE Lab, 2O1!"))
        assertEquals("LIBRARY", normaliseText("Library"))
    }

    @Test fun `confidence thresholds reject invalid configuration`() {
        fails<IllegalArgumentException> { RecognitionSettings(temperature = 0.0) }
        fails<IllegalArgumentException> { RecognitionSettings(minMargin = Double.NaN) }
    }
}
