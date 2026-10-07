package `in`.mite.rain.core

import kotlin.math.sqrt

data class VisualReference(val nodeId: String, val vector: List<Double>)
data class PlaceEvidence(val nodeId: String, val features: List<Double>) {
    // Ranking only; neither a learned fusion model nor a probability.
    val previewScore get() = when {
        features[2] > 0 && features[3] > 0 -> .6 * features[0] + .4 * features[1]
        features[2] > 0 -> features[0]
        else -> features[1]
    }
}

object VisualMatching {
    val featureNames = listOf("ocr_score", "visual_score", "ocr_present", "visual_present", "agreement")
    fun cosine(a: List<Double>, b: List<Double>): Double {
        require(a.isNotEmpty() && a.size == b.size && a.all { it.isFinite() } && b.all { it.isFinite() })
        val aa = a.sumOf { it*it }; val bb = b.sumOf { it*it }
        require(aa > 0 && bb > 0)
        return (a.indices.sumOf { a[it]*b[it] } / sqrt(aa*bb)).coerceIn(-1.0,1.0)
    }
    fun evidence(nodes: Collection<Node>, text: String, vector: List<Double>, references: List<VisualReference>): List<PlaceEvidence> {
        val named = nodes.filter { it.roomName.isNotBlank() }
        val ocr = RoomNameMatcher(named).match(text, named.size).topCandidates.associate { it.nodeId to it.score }
        val visual = references.filter { r -> named.any { it.id == r.nodeId } }
            .groupBy { it.nodeId }.mapValues { (_, refs) -> refs.maxOf { cosine(vector,it.vector).coerceAtLeast(0.0) } }
        val textBest = ocr.maxByOrNull { it.value }?.key
        val visualBest = visual.maxByOrNull { it.value }?.key
        return named.map { n -> PlaceEvidence(n.id, listOf(ocr[n.id] ?: 0.0,visual[n.id] ?: 0.0,
            if(text.isNotBlank()) 1.0 else 0.0, if(n.id in visual) 1.0 else 0.0,
            if(text.isNotBlank() && n.id == textBest && n.id == visualBest) 1.0 else 0.0)) }
            .sortedWith(compareByDescending<PlaceEvidence> { it.previewScore }.thenBy { it.nodeId })
    }
}
