package `in`.mite.rain.core

import kotlin.math.cos
import kotlin.math.sin

/** Local AR coordinates: +x right, -z ahead. No localisation or progress inference. */
data class ArArrow(val x: Float, val z: Float, val clockwiseDegrees: Float)

fun arPreviewPath(lengths: List<Double>, bearings: List<Double>, offsetM: Double = 0.0,
                  maxDistanceM: Double = 6.0): List<ArArrow> {
    require(lengths.size == bearings.size)
    require(lengths.all { it.isFinite() && it > 0 } && bearings.all { it.isFinite() })
    require(offsetM.isFinite() && offsetM >= 0 && maxDistanceM.isFinite() && maxDistanceM in 0.5..6.0)
    if (lengths.isEmpty()) return emptyList()
    require(offsetM <= lengths.first())
    val result = mutableListOf<ArArrow>()
    var x = 0.0; var z = 0.0; var walked = 0.0; var sample = 0.75
    for (i in lengths.indices) {
        val length = lengths[i] - if (i == 0) offsetM else 0.0
        val angle = signedTurnDegrees(bearings.first(), bearings[i])
        val radians = Math.toRadians(angle)
        while (sample <= walked + length && sample <= maxDistanceM) {
            val t = sample - walked
            result += ArArrow((x + sin(radians) * t).toFloat(), (z - cos(radians) * t).toFloat(), angle.toFloat())
            sample += 0.75
        }
        x += sin(radians) * length; z -= cos(radians) * length; walked += length
        if (walked >= maxDistanceM) break
    }
    return result
}
