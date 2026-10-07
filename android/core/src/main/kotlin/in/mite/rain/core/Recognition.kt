package `in`.mite.rain.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class RecognitionSettings(val minScore: Double = 0.5, val minProbability: Double = 0.6,
                               val minMargin: Double = 0.2, val temperature: Double = 0.1) {
    init {
        require(minScore in 0.0..1.0 && minProbability in 0.0..1.0 && minMargin in 0.0..1.0)
        require(temperature.isFinite() && temperature > 0)
    }
}
data class RecognitionCandidate(val nodeId: String, val roomName: String, val score: Double, val probability: Double)
data class RecognitionResult(val topCandidates: List<RecognitionCandidate>, val accepted: Boolean, val reason: String) {
    val best: RecognitionCandidate? get() = topCandidates.firstOrNull()
    val margin: Double get() = (topCandidates.firstOrNull()?.probability ?: 0.0) - (topCandidates.getOrNull(1)?.probability ?: 0.0)
}

/** Text-only OCR matching. Softmax confidence is a relative match score, not calibrated accuracy. */
class RoomNameMatcher(nodes: Collection<Node>, private val settings: RecognitionSettings = RecognitionSettings()) {
    private val candidates = nodes.filter { it.roomName.isNotBlank() || it.signText.isNotBlank() || it.aliases.isNotEmpty() }
        .map { node -> node to (listOf(node.signText, node.roomName) + node.aliases).map(::normaliseText).filter(String::isNotBlank).distinct() }

    fun match(text: String, limit: Int = 3): RecognitionResult {
        val all = normaliseText(text)
        if (all.isBlank()) return RecognitionResult(emptyList(), false, "No text read. Move closer to a room sign.")
        if (candidates.isEmpty()) return RecognitionResult(emptyList(), false, "No room labels are registered.")
        val observations = (listOf(all) + text.lines().map(::normaliseText)).filter(String::isNotBlank).distinct()
        val observedNumbers = numbers(all)
        val scores = candidates.map { (node, labels) ->
            val registeredNumbers = labels.flatMap { numbers(it) }.toSet()
            val raw = labels.maxOfOrNull { label -> observations.maxOf { observed -> score(observed, label) } } ?: 0.0
            // Apply this at room level too: a generic alias or separate OCR line must not
            // override an explicit number that conflicts with every registered room number.
            val conflict = observedNumbers.isNotEmpty() && registeredNumbers.isNotEmpty() &&
                observedNumbers.intersect(registeredNumbers).isEmpty()
            node to if (conflict) min(0.45, raw) else raw
        }.sortedWith(compareByDescending<Pair<Node, Double>> { it.second }.thenBy { it.first.id })
        val largest = scores.first().second
        val masses = scores.map { exp((it.second - largest) / settings.temperature) }
        val denominator = masses.sum()
        val ranked = scores.mapIndexed { index, (node, value) -> RecognitionCandidate(node.id, node.displayName, value, masses[index] / denominator) }
        val first = ranked.first()
        val margin = first.probability - (ranked.getOrNull(1)?.probability ?: 0.0)
        val accepted = first.score >= settings.minScore && first.probability >= settings.minProbability && margin >= settings.minMargin
        val reason = when {
            first.score < settings.minScore -> "Text does not match a registered room. Rescan the complete sign."
            !accepted -> "Several rooms look similar. Move closer or include the room number."
            else -> "Room sign matched. Confirm your location."
        }
        return RecognitionResult(ranked.take(limit.coerceAtLeast(1)), accepted, reason)
    }

    private fun score(observed: String, expected: String): Double {
        val a = observed.replace(" ", "")
        val b = expected.replace(" ", "")
        val similarity = (1.0 - weightedLevenshtein(a, b) / max(a.length, b.length)).coerceIn(0.0, 1.0)
        val seenNumbers = numbers(observed)
        val labelNumbers = numbers(expected)
        // A confidently read conflicting room number must not be accepted via a long common prefix.
        return if (seenNumbers.isNotEmpty() && labelNumbers.isNotEmpty() && seenNumbers.intersect(labelNumbers).isEmpty()) min(0.45, similarity) else similarity
    }

    private fun numbers(text: String): Set<String> = Regex("\\d+").findAll(text)
        .map { it.value.trimStart('0').ifBlank { "0" } }.toSet()
}

fun normaliseText(input: String): String {
    val clean = Normalizer.normalize(input, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return clean.split(Regex("\\s+")).joinToString(" ") { token ->
        if (token.any(Char::isDigit) && token.all { it.isDigit() || it in "OILSBZ" }) {
            token.map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; 'S' -> '5'; 'B' -> '8'; 'Z' -> '2'; else -> it } }.joinToString("")
        } else token
    }
}

/** Weighted edit distance including common single-character confusions and RN <-> M. */
fun weightedLevenshtein(a: String, b: String): Double {
    val d = Array(a.length + 1) { DoubleArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i.toDouble()
    for (j in 0..b.length) d[0][j] = j.toDouble()
    val confusions = listOf("O0", "I1L", "S5", "B8", "Z2")
    for (i in 1..a.length) for (j in 1..b.length) {
        val ca = a[i - 1]; val cb = b[j - 1]
        val substitution = when { ca == cb -> 0.0; confusions.any { ca in it && cb in it } -> 0.3; else -> 1.0 }
        var best = min(min(d[i - 1][j] + 1.0, d[i][j - 1] + 1.0), d[i - 1][j - 1] + substitution)
        if (i >= 2 && a.substring(i - 2, i) == "RN" && cb == 'M') best = min(best, d[i - 2][j - 1] + 0.3)
        if (j >= 2 && b.substring(j - 2, j) == "RN" && ca == 'M') best = min(best, d[i - 1][j - 2] + 0.3)
        d[i][j] = best
    }
    return d[a.length][b.length]
}
