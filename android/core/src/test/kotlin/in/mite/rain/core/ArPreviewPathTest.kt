package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class ArPreviewPathTest {
    @Test fun straightIsAheadAndLimitedToSixMetres() {
        val arrows = arPreviewPath(listOf(40.0), listOf(270.0))
        assertEquals(8, arrows.size)
        assertTrue(arrows.all { it.x == 0f && it.z < 0f })
        assertEquals(-6f, arrows.last().z, .001f)
    }
    @Test fun clockwiseTurnGoesRightAndPreservesOffset() {
        val arrows = arPreviewPath(listOf(2.0, 4.0), listOf(350.0, 80.0), 0.5)
        assertEquals(0f, arrows[1].x, .001f)
        assertEquals(-1.5f, arrows[1].z, .001f)
        assertEquals(.75f, arrows[2].x, .001f)
        assertEquals(-1.5f, arrows[2].z, .001f)
    }
    @Test fun counterclockwiseTurnGoesLeft() {
        val arrows = arPreviewPath(listOf(.75, 2.0), listOf(0.0, 270.0))
        assertTrue(arrows[1].x < 0)
    }
    @Test fun shortRemainingRouteNeverDrawsBeyondDestination() {
        assertTrue(arPreviewPath(listOf(1.0), listOf(0.0), .5).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidOffsetIsRejected() {
        arPreviewPath(listOf(1.0), listOf(0.0), 2.0)
    }
}
