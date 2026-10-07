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
                     floorName: String = "Demo floor / Level 01", synthetic: Boolean = true) {
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Eyebrow(floorName)
                    Text("Your indoor route", style = MaterialTheme.typography.titleMedium)
                }
                Tag(if (synthetic) "Synthetic map" else "Imported map")
            }
            Canvas(Modifier.fillMaxWidth().height(265.dp).padding(vertical = 20.dp).semantics {
                contentDescription = (if (synthetic) "Synthetic floor map. " else "Imported floor map. ") + (route?.nodes?.joinToString(" to ") { graph.nodes.getValue(it).displayName } ?: "No route selected.")
            }) {
                val nodes = graph.nodes.values
                val minX = nodes.minOf { it.xM }; val maxX = nodes.maxOf { it.xM }
                val minY = nodes.minOf { it.yM }; val maxY = nodes.maxOf { it.yM }
                val scale = min((size.width - 66.dp.toPx()) / max(maxX - minX, 1.0),
                    (size.height - 40.dp.toPx()) / max(maxY - minY, 1.0)).toFloat()
                val xBase = (size.width - ((maxX - minX) * scale).toFloat()) / 2f
                val yBase = (size.height - ((maxY - minY) * scale).toFloat()) / 2f
                fun point(n: Node) = Offset(xBase + ((n.xM - minX) * scale).toFloat(),
                    yBase + ((maxY - n.yM) * scale).toFloat())
                // A schematic corridor map; coordinates are local metres, not GPS.
                graph.edges.forEach { e ->
                    val a = point(graph.nodes.getValue(e.fromNode)); val b = point(graph.nodes.getValue(e.toNode))
                    drawLine(Color(0xFFEDF0E8), a, b, 23.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xFFDDE4D8), a, b, 1.dp.toPx(), StrokeCap.Round)
                }
                route?.arcs?.forEach { arc ->
                    drawLine(Ink, point(graph.nodes.getValue(arc.fromNode)), point(graph.nodes.getValue(arc.toNode)),
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
                    drawCircle(if (endpoint) Ink else if (onRoute) Ink else Color(0xFFADBDB0),
                        if (endpoint) 7.dp.toPx() else 3.5.dp.toPx(), p)
                    if (endpoint) drawCircle(if (n.id == destinationId) Accent else Color.White, 3.dp.toPx(), p)
                    if (n.roomName.isNotBlank() || endpoint) {
                        val short = when (n.type) {
                            NodeType.JUNCTION -> n.id
                            NodeType.ENTRANCE -> "Entry"
                            NodeType.CORRIDOR_POINT -> "Corner"
                            else -> Regex("\\d+").find(n.roomName)?.value
                                ?: n.aliases.firstOrNull { it.all(Char::isDigit) && it.isNotEmpty() }
                                ?: n.displayName
                        }
                        val label = if (short.length > 16) short.take(14) + "…" else short
                        val topRow = n.yM >= (minY + maxY) / 2
                        drawContext.canvas.nativeCanvas.drawText(label, p.x,
                            p.y + if (topRow) -14.dp.toPx() else 22.dp.toPx(), labelPaint)
                    } else if (n.type == NodeType.JUNCTION) {
                        drawContext.canvas.nativeCanvas.drawText(n.id, p.x, p.y - 12.dp.toPx(), labelPaint)
                    }
                }
                if (navigating && route != null) {
                    var remaining = distanceM
                    var position = point(graph.nodes.getValue(startId))
                    for (arc in route.arcs) {
                        val a = point(graph.nodes.getValue(arc.fromNode)); val b = point(graph.nodes.getValue(arc.toNode))
                        if (remaining <= arc.lengthM) {
                            val fraction = (remaining / arc.lengthM).coerceIn(0.0, 1.0).toFloat()
                            position = a + (b - a) * fraction
                            break
                        }
                        remaining -= arc.lengthM
                        position = b
                    }
                    drawCircle(Accent.copy(alpha = .22f), 17.dp.toPx(), position)
                    drawCircle(Color.White, 9.dp.toPx(), position)
                    drawCircle(Accent, 6.dp.toPx(), position)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("●  Selected route", style = MaterialTheme.typography.bodySmall, color = Ink)
                Text(if (navigating) "●  Estimated position" else "●  Destination", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAD6429))
            }
        }
    }
}
