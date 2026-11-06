package `in`.mite.rain.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun GroundFloorPlanDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var marked by remember { mutableStateOf(true) }
    val image = remember(context, marked) {
        context.assets.open(if (marked) "ground-floor-marked.jpeg" else "ground-floor-plan.png")
            .use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = CanvasColor) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Text(if (marked) "Marked ground-floor plan" else "Original ground-floor plan", style = MaterialTheme.typography.titleLarge)
                Text("Blue: passages · Red: doors · Yellow: stairs. Pinch to zoom and drag to inspect.")
                Text("Room dimensions are in metres. Route distances remain estimates; some openings still need clarification.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { marked = !marked; scale = 1f; offset = Offset.Zero }) {
                    Text(if (marked) "View original drawing" else "View marked drawing")
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = size.width * (scale-1f) / 2f
                            val maxY = size.height * (scale-1f) / 2f
                            offset = Offset((offset.x+pan.x).coerceIn(-maxX,maxX), (offset.y+pan.y).coerceIn(-maxY,maxY))
                        }
                    }) {
                    Image(image, if (marked) "User-marked corridors, doors and stairs" else "Original supplied ground-floor architectural plan", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset zoom") }
                    Button(onClick = onClose) { Text("Close plan") }
                }
            }
        }
    }
}
