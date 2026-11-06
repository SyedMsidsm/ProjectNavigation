package `in`.mite.rain.core

import kotlin.math.*

/** A longer return from a dead end passes through the previous junction.
 * Only reuse an exact same-floor retrace along a bidirectional recorded segment. */
fun Graph.reuseRetracedCorridors(): Graph {
    val links = edges.toMutableList()
    var changed = false
    for (i in links.indices) {
        var leg = links[i]
        if (!leg.bidirectional || leg.transition.isNotEmpty()) continue
        while (leg.lengthM > 0.05) {
            val start = nodes.getValue(leg.fromNode)
            val end = nodes.getValue(leg.toNode)
            val back = links.take(i).singleOrNull { prior ->
                if (!prior.bidirectional || prior.transition.isNotEmpty() || prior.lengthM <= 0.05 ||
                    abs(prior.lengthM-leg.lengthM) <= 0.05 || prior.toNode != start.id) false
                else {
                    val hall = nodes.getValue(prior.fromNode)
                    val axis = Math.toRadians(leg.bearingDeg)
                    abs(signedTurnDegrees(prior.bearingDeg,leg.bearingDeg)).let { abs(it-180.0) < 0.01 } &&
                        hypot(start.xM + sin(axis)*prior.lengthM - hall.xM,
                            start.yM + cos(axis)*prior.lengthM - hall.yM) < 0.05 &&
                        hypot(start.xM + sin(axis)*leg.lengthM - end.xM,
                            start.yM + cos(axis)*leg.lengthM - end.yM) < 0.05
                }
            } ?: break
            if (back.lengthM > leg.lengthM) {
                // The return stopped inside the original corridor, then branched.
                // Only split at an unlabeled structural junction on this exact line.
                if(end.type == NodeType.JUNCTION && end.roomName.isBlank()) {
                    links[links.indexOf(back)] = back.copy(toNode=end.id,lengthM=back.lengthM-leg.lengthM)
                    changed=true
                }
                break
            }
            leg = leg.copy(fromNode=back.fromNode,lengthM=leg.lengthM-back.lengthM)
            links[i] = leg
            changed = true
        }
    }
    return if (changed) Graph(nodes.values,links) else this
}

/** Close a user-confirmed return. Follow the existing straight corridor outward
 * from the start to find the junction where the final return leg joined it. */
fun Graph.closeRecordedReturn(startId: String, endId: String): Graph {
    if (startId == endId) return this
    val start = nodes.getValue(startId)
    val end = nodes.getValue(endId)
    require(start.floor == end.floor && degree(endId) == 1) { "Choose a single-floor walk ending at the start." }
    val back = outgoing(endId).single()
    val corner = nodes.getValue(back.toNode)
    require(corner.id != startId) { "Walk out and back before closing the route." }
    val kept = edges.filter { it.id != back.edgeId }.toMutableList()
    val places = nodes.toMutableMap().apply { remove(endId) }
    val radians = Math.toRadians(back.bearingDeg)
    val dx = sin(radians)
    val dy = cos(radians)
    val along = (corner.xM-start.xM)*dx + (corner.yM-start.yM)*dy
    val side = abs((corner.xM-start.xM)*dy - (corner.yM-start.yM)*dx)
    var at = start
    var travelled = 0.0
    var matched = false
    val seen = mutableSetOf(start.id)
    while (side <= 1.8 && along > 0 && abs(along-back.lengthM) <= 1.8) {
        val arc = outgoing(at.id).singleOrNull { it.edgeId != back.edgeId && it.toNode !in seen &&
            it.transition.isBlank() && abs(signedTurnDegrees(it.bearingDeg,back.bearingDeg)) < 5 } ?: break
        val next = nodes.getValue(arc.toNode)
        seen.add(next.id)
        if (next.id == corner.id) { matched = true; break }
        val offset = along-travelled
        if (offset > .15 && offset < arc.lengthM-.15 && corner.roomName.isBlank() && degree(corner.id)==2) {
            val snapped = corner.copy(xM=at.xM+offset*dx,yM=at.yM+offset*dy)
            val spur = kept.single { it.fromNode==corner.id || it.toNode==corner.id }
            val other = nodes.getValue(if(spur.fromNode==corner.id) spur.toNode else spur.fromNode)
            val len = hypot(snapped.xM-other.xM,snapped.yM-other.yM)
            if (len < .1 || spur.id == arc.edgeId) break
            places[corner.id] = snapped
            kept[kept.indexOf(spur)] = spur.copy(lengthM=len)
            val original = kept.single { it.id==arc.edgeId }
            kept.remove(original)
            kept += original.copy(fromNode=at.id,toNode=corner.id,lengthM=offset,bearingDeg=arc.bearingDeg)
            kept += original.copy(id=back.edgeId,fromNode=corner.id,toNode=next.id,lengthM=arc.lengthM-offset,bearingDeg=arc.bearingDeg)
            matched = true
            break
        }
        travelled += arc.lengthM
        if (travelled > along+.15) break
        at = next
    }
    if (!matched && kept.none { setOf(it.toNode,it.fromNode)==setOf(startId,corner.id) }) {
        val x = start.xM-corner.xM
        val y = start.yM-corner.yM
        require(hypot(x,y) > .1) { "Cannot distinguish the final corner from the start." }
        kept += edges.single { it.id==back.edgeId }.copy(fromNode=corner.id,toNode=startId,
            lengthM=hypot(x,y),bearingDeg=normaliseBearing(Math.toDegrees(atan2(x,y))))
    }
    return Graph(places.values,kept)
}
