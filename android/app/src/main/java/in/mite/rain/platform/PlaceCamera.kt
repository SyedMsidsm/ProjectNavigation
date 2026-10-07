package `in`.mite.rain.platform

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PlaceCamera(onCapture: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    val latestCapture by rememberUpdatedState(onCapture)
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission=it }
    var camera by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val executor=remember { Executors.newSingleThreadExecutor() }
    val active=remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { active.set(false);executor.shutdown() } }
    if(!permission) Button(onClick={launcher.launch(Manifest.permission.CAMERA)}) { Text("Allow camera for AI capture") }
    else {
        val view=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
        AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(280.dp))
        DisposableEffect(owner,view) {
            val alive=AtomicBoolean(true); var provider:ProcessCameraProvider?=null
            val preview=Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({ if(alive.get()) try {
                provider=future.get();provider?.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture);camera=capture
            } catch(_:Exception) { error="Camera unavailable. Close this view and retry." } },ContextCompat.getMainExecutor(context))
            onDispose { alive.set(false);camera=null;provider?.unbind(preview,capture) }
        }
        Button(enabled=camera!=null && !busy,onClick={
            busy=true;error=null
            camera?.takePicture(executor,object:ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image:ImageProxy) {
                    try {
                        val raw=image.toBitmap()
                        val rotated=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },true)
                        val ratio=minOf(1.0,1280.0/maxOf(rotated.width,rotated.height))
                        val bitmap=Bitmap.createScaledBitmap(rotated,(rotated.width*ratio).toInt(),(rotated.height*ratio).toInt(),true)
                        ContextCompat.getMainExecutor(context).execute { if(active.get()) { busy=false;latestCapture(bitmap) } }
                    } catch(_:Exception) {
                        ContextCompat.getMainExecutor(context).execute { if(active.get()) { busy=false;error="Capture failed. Try again." } }
                    } finally { image.close() }
                }
                override fun onError(exception:ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute { if(active.get()) { busy=false;error="Capture failed. Try again." } }
                }
            })
        }) { Text(if(busy) "Capturing…" else "Capture doorway view") }
    }
    error?.let { Text(it) }
    TextButton(onClick=onCancel,enabled=!busy) { Text("Cancel capture") }
}
