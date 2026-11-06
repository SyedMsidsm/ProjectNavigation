package `in`.mite.rain.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.mite.rain.core.Graph
import `in`.mite.rain.core.Node
import `in`.mite.rain.core.NodeType
import `in`.mite.rain.core.Route
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun FloorMap(graph: Graph, route: Route?, startId: String, destinationId: String,
                     distanceM: Double = 0.0, navigating: Boolean = false,
                     floorName: String = "Demo floor / Level 01", synthetic: Boolean = true,
                     selectedFloor: Int = graph.nodes[startId]?.floor ?: 0, headingDegrees: Double? = null) {
    Surface(color = PanelColor, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Eyebrow(floorName)
                    Text(if(route == null && !synthetic) "Floor map" else "Your indoor route", style = MaterialTheme.typography.titleMedium)
                }
                Tag(if (synthetic) "Sample" else "Local map")
            }
            Canvas(Modifier.fillMaxWidth().height(if(navigating) 340.dp else 265.dp).padding(vertical = 20.dp).semantics {
                contentDescription = (if (synthetic) "Synthetic floor map. " else "Imported floor map. ") + (route?.nodes?.joinToString(" to ") { graph.nodes.getValue(it).displayName } ?: "No route selected.")
            }) {
                val nodes = graph.nodes.values.filter { it.floor == selectedFloor }.ifEmpty { graph.nodes.values.toList() }
                val minX = nodes.minOf { it.xM }; val maxX = nodes.maxOf { it.xM }
                val minY = nodes.minOf { it.yM }; val maxY = nodes.maxOf { it.yM }
                val scale = min((size.width - 66.dp.toPx()) / max(maxX - minX, 1.0),
                    (size.height - 40.dp.toPx()) / max(maxY - minY, 1.0)).toFloat()
                val xBase = (size.width - ((maxX - minX) * scale).toFloat()) / 2f
                val yBase = (size.height - ((maxY - minY) * scale).toFloat()) / 2f
                fun point(n: Node): Offset {
                    val px = xBase + ((n.xM - minX) * scale).toFloat()
                    val py = yBase + ((maxY - n.yM) * scale).toFloat()
                    if (!navigating) return Offset(px,py)
                    val depth = (py / size.height).coerceIn(0f,1f)
                    return Offset(center.x + (px-center.x)*(.72f+.28f*depth),
                        center.y + (py-center.y)*.86f)
                }
                // A schematic corridor map; coordinates are local metres, not GPS.
                graph.edges.filter { e -> graph.nodes.getValue(e.fromNode).floor == selectedFloor && graph.nodes.getValue(e.toNode).floor == selectedFloor }.forEach { e ->
                    val a = point(graph.nodes.getValue(e.fromNode)); val b = point(graph.nodes.getValue(e.toNode))
                    if(navigating) drawLine(Color(0xFF070B12),a+Offset(0f,4.dp.toPx()),b+Offset(0f,4.dp.toPx()),25.dp.toPx(),StrokeCap.Round)
                    drawLine(Color(0xFF222C36), a, b, 23.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xFF455363), a, b, 1.dp.toPx(), StrokeCap.Round)
                }
                route?.arcs?.filter { graph.nodes.getValue(it.fromNode).floor == selectedFloor && graph.nodes.getValue(it.toNode).floor == selectedFloor }?.forEach { arc ->
                    drawLine(Accent, point(graph.nodes.getValue(arc.fromNode)), point(graph.nodes.getValue(arc.toNode)),
                        4.dp.toPx(), StrokeCap.Round)
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Muted.toArgb(); textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                }
                nodes.forEach { n ->
                    val p = point(n)
                    val endpoint = n.id == startId || n.id == destinationId
                    val onRoute = route?.nodes?.contains(n.id) == true
                    drawCircle(if (n.type == NodeType.STAIRS) Color(0xFFC89900) else if (endpoint) Ink else if (onRoute) Ink else Color(0xFFADBDB0),
                        if (endpoint) 7.dp.toPx() else 3.5.dp.toPx(), p)
                    if (endpoint) drawCircle(if (n.id == destinationId) Accent else PanelColor, 3.dp.toPx(), p)
                    if (n.roomName.isNotBlank() || endpoint) {
                        val short = if(n.roomName.isNotBlank()) n.displayName else when (n.type) {
                            NodeType.JUNCTION -> n.id
                            NodeType.ENTRANCE -> "Entry"
                            NodeType.CORRIDOR_POINT -> "Corner"
                            NodeType.STAIRS -> "Stairs"
                            else -> Regex("\\d+").find(n.roomName)?.value
                                ?: n.aliases.firstOrNull { it.all(Char::isDigit) && it.isNotEmpty() }
                                ?: n.displayName
                        }
                        val label = if (short.length > 16) short.take(14) + "…" else short
                        val topRow = n.yM >= (minY + maxY) / 2
                        drawContext.canvas.nativeCanvas.drawText(label, p.x,
                            p.y + if (topRow) -14.dp.toPx() else 22.dp.toPx(), labelPaint)
                    } else if (n.type == NodeType.JUNCTION && route == null && synthetic) {
                        drawContext.canvas.nativeCanvas.drawText(n.id, p.x, p.y - 12.dp.toPx(), labelPaint)
                    }
                }
                if (navigating && route != null) {
                    var remaining = distanceM
                    var position = point(graph.nodes.getValue(startId))
                    for (arc in route.arcs) {
                        val a = point(graph.nodes.getValue(arc.fromNode)); val b = point(graph.nodes.getValue(arc.toNode))
                        if (remaining <= arc.lengthM) {
                            val fraction = if(arc.lengthM == 0.0) 0f else (remaining / arc.lengthM).coerceIn(0.0, 1.0).toFloat()
                            position = a + (b - a) * fraction
                            break
                        }
                        remaining -= arc.lengthM
                        position = b
                    }
                    val blue = Color(0xFF70ACFF)
                    if (headingDegrees != null) {
                        rotate((headingDegrees % 360).toFloat(), position) {
                            val radius = 78.dp.toPx()
                            drawArc(brush = Brush.radialGradient(listOf(blue.copy(alpha=.38f), blue.copy(alpha=0f)), position, radius),
                                startAngle = -124f, sweepAngle = 68f, useCenter = true,
                                topLeft = position - Offset(radius,radius), size = androidx.compose.ui.geometry.Size(radius*2,radius*2))
                            fun facet(vararg points: Pair<Int,Int>) = Path().apply {
                                points.forEachIndexed { index, (x,y) ->
                                    val px = position.x+x.dp.toPx()
                                    val py = position.y+y.dp.toPx()
                                    if(index==0) moveTo(px,py) else lineTo(px,py)
                                }
                                close()
                            }
                            drawPath(facet(0 to -23, 19 to 17, 0 to 10,-19 to 17), Color(0xFF142B4C))
                            drawPath(facet(0 to -26,-18 to 12,0 to 5), Color(0xFFBCDEFF))
                            drawPath(facet(0 to -26,0 to 5,18 to 12), Color(0xFF448EFF))
                            drawPath(facet(-18 to 12,0 to 5,18 to 12,0 to 10), Color(0xFF2663B9))
                        }
                    } else {
                        drawCircle(blue.copy(alpha=.2f), 18.dp.toPx(), position)
                        drawCircle(Color.White, 10.dp.toPx(), position)
                        drawCircle(blue, 7.dp.toPx(), position)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(route == null) "Recorded trail" else "●  Selected route", style = MaterialTheme.typography.bodySmall, color = Accent)
                Text(if (navigating) "●  Estimated position" else if(route == null) "" else "●  Destination", style = MaterialTheme.typography.bodySmall,
                    color = if(navigating) Color(0xFF70ACFF) else Accent)
            }
            if (nodesHaveStairs(graph)) Text("Yellow points: stairs access. Routes stop before the steps.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private fun nodesHaveStairs(graph: Graph) = graph.nodes.values.any { it.type == NodeType.STAIRS }
