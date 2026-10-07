package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class VisualMatchingTest {
    @Test fun cosineNormalizesAndRejectsInvalidVectors() {
        assertEquals(1.0,VisualMatching.cosine(listOf(1.0,0.0),listOf(3.0,0.0)),1e-8)
        assertEquals(0.0,VisualMatching.cosine(listOf(1.0,0.0),listOf(0.0,2.0)),1e-8)
        fails<IllegalArgumentException> { VisualMatching.cosine(listOf(0.0),listOf(0.0)) }
        fails<IllegalArgumentException> { VisualMatching.cosine(listOf(Double.NaN),listOf(1.0)) }
    }
    @Test fun onlyRegisteredReferencesContributeAndMissingEvidenceIsExplicit() {
        val nodes=listOf(Node("A",NodeType.DOOR,"Room A"),Node("B",NodeType.DOOR,"Room B"))
        val results=VisualMatching.evidence(nodes,"",listOf(1.0,0.0),listOf(VisualReference("B",listOf(1.0,0.0)),VisualReference("OTHER",listOf(1.0,0.0))))
        assertEquals("B",results.first().nodeId)
        assertEquals(listOf(0.0,1.0,0.0,1.0,0.0),results.first().features)
        assertEquals(2,results.size)
    }
    @Test fun agreeingTextAndAppearanceAreVisibleToTheLearner() {
        val nodes=listOf(Node("A",NodeType.DOOR,"Room A"),Node("B",NodeType.DOOR,"Room B"))
        val evidence=VisualMatching.evidence(nodes,"Room A",listOf(1.0,0.0),listOf(VisualReference("A",listOf(1.0,0.0))))
        assertEquals("A",evidence.first().nodeId)
        assertEquals(1.0,evidence.first().features.last(),0.0)
    }
}
