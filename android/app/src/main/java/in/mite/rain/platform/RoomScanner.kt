package `in`.mite.rain.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun RoomScanner(onText: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestOnText by rememberUpdatedState(onText)
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it; requested = true }
    LaunchedEffect(Unit) { if (!permitted) launcher.launch(Manifest.permission.CAMERA) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (permitted) {
            val previewView = remember { PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            } }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(240.dp).clipToBounds())
            DisposableEffect(owner, previewView) {
                val executor = Executors.newSingleThreadExecutor()
                val main = ContextCompat.getMainExecutor(context)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val disposed = AtomicBoolean(false)
                val busy = AtomicBoolean(false)
                val future = ProcessCameraProvider.getInstance(context)
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                var provider: ProcessCameraProvider? = null
                var lastFrame = 0L
                analysis.setAnalyzer(executor) { frame ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    val media = frame.image
                    if (disposed.get() || media == null || now - lastFrame < 450 || !busy.compareAndSet(false, true)) {
                        frame.close()
                    } else {
                        lastFrame = now
                        try {
                            val image = InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees)
                            recognizer.process(image)
                                .addOnSuccessListener(main) { result -> if (!disposed.get()) latestOnText(result.text) }
                                .addOnFailureListener(main) { if (!disposed.get()) error = "Could not read this frame. Hold steady and try closer." }
                                .addOnCompleteListener {
                                    frame.close(); busy.set(false)
                                    if (disposed.get()) recognizer.close()
                                }
                        } catch (_: Exception) {
                            frame.close(); busy.set(false)
                            main.execute { if (!disposed.get()) error = "Camera frame unavailable. Try reopening the scanner." }
                        }
                    }
                }
                future.addListener({
                    if (!disposed.get()) {
                        try {
                            provider = future.get()
                            provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (_: Exception) { error = "Camera unavailable. Close other camera apps, then retry." }
                    }
                }, main)
                onDispose {
                    disposed.set(true)
                    analysis.clearAnalyzer()
                    provider?.unbind(preview, analysis)
                    executor.shutdown()
                    if (!busy.get()) recognizer.close()
                }
            }
            Text("Point at a room sign. Recognition runs on this phone; frames are not saved.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(if (requested) "Camera permission is needed to scan a sign. You can still select your start room manually." else "Allow camera access to read a room sign.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("App settings") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onClose) { Text("Close scanner") }
    }
}
