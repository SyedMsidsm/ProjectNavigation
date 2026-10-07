package `in`.mite.rain.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.mite.rain.core.*
import `in`.mite.rain.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private data class PlaceObservation(val bitmap:Bitmap,val vector:List<Double>,val evidence:List<PlaceEvidence>,val at:Long)

@Composable
internal fun PlaceCollectionDialog(floor:LoadedFloor,onUseLocation:(String)->Unit,onClose:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var study by remember { mutableStateOf("pilot") }
    var studyInput by remember { mutableStateOf("pilot") }
    val store=remember(floor.digest,study) { PlaceDataset(context,floor,study) }
    var summary by remember { mutableStateOf("Loading dataset…") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(false) }
    var observation by remember { mutableStateOf<PlaceObservation?>(null) }
    var split by remember { mutableStateOf("reference") }
    var session by remember { mutableStateOf("S"+System.currentTimeMillis()) }
    var selected by remember { mutableStateOf("") }
    var unknown by remember { mutableStateOf(false) }
    val nodes=remember(floor.digest) { floor.graph.nodes.values.filter { it.roomName.isNotBlank() }.sortedBy { it.displayName } }
    LaunchedEffect(store) {
        try { summary=withContext(Dispatchers.IO) { store.summary() } }
        catch(_:Exception) { message="Dataset could not be read. Do not overwrite it; export/recover the stored files." }
        finally { busy=false }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri,"wt")?.use(store::export) ?: error("Cannot open export file")
            };message="Dataset exported. It contains labelled photos; review it before sharing." }
            catch(error:Exception) { message="Export failed: ${error.message?.take(140)}" }
            finally { busy=false }
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=CanvasColor) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("AI place collection",style=MaterialTheme.typography.headlineMedium)
                Text(floor.name)
                Text("Pretrained MobileNetV3 + OCR. Campus fusion is not trained yet. Preview rankings always require your confirmation.")
                Text(summary)
                OutlinedTextField(studyInput,{studyInput=it},label={Text("Study code (active: $study)")},enabled=!busy && !camera,singleLine=true)
                TextButton(enabled=!busy && !camera,onClick={
                    if(studyInput.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { if(study!=studyInput) { busy=true;study=studyInput;observation=null };message="Study opened. Other study files are preserved." }
                    else message="Use 1–40 letters, digits, - or _ for the study code."
                }) { Text("Open or create study") }
                Text("Select the actual room yourself. Capture only its doorway/sign and surroundings; avoid people and personal information. Photos are saved only when you tap Save labelled capture.")
                if(!camera) {
                    NodePicker("Actual location (ground truth)",nodes,selected) { if(!busy) { selected=it;unknown=false } }
                    Row { Checkbox(unknown,{unknown=it},enabled=!busy);Text("Unknown / outside registered locations") }
                    Text("Capture purpose: $split")
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("reference","train").forEach { value -> OutlinedButton(onClick={split=value},enabled=!busy) { Text(value) } }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("validation","test").forEach { value -> OutlinedButton(onClick={split=value},enabled=!busy) { Text(value) } }
                    }
                    OutlinedTextField(session,{session=it},label={Text("Capture session code")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                    Text("Finish all reference photos first. Use different real capture sessions for training, validation and test; renaming a session does not make adjacent photos independent.")
                    Button(onClick={camera=true;observation=null;message=null},enabled=!busy) { Text("Take a new view") }
                } else PlaceCamera(onCapture={ bitmap ->
                    camera=false;busy=true
                    scope.launch {
                        try { observation=withContext(Dispatchers.IO) {
                            val at=System.currentTimeMillis()
                            val vector=PlaceEncoder(context).use { it.encode(bitmap) }
                            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val text=try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS).text } finally { recognizer.close() }
                            PlaceObservation(bitmap,vector,VisualMatching.evidence(floor.graph.nodes.values,text,vector,store.references()),at)
                        };message="Review the photo and actual room before saving. Raw OCR text is not stored." }
                        catch(error:Exception) { message="Analysis failed: ${error.message?.take(140)}" }
                        finally { busy=false }
                    }
                },onCancel={camera=false})
                observation?.let { obs ->
                    Image(obs.bitmap.asImageBitmap(),"Captured doorway review",modifier=Modifier.fillMaxWidth().height(210.dp))
                    Text("Preview suggestions — untrained combined ranking")
                    obs.evidence.take(3).forEach { e ->
                        Text("${floor.graph.nodes.getValue(e.nodeId).displayName}: text %.2f · visual %.2f".format(java.util.Locale.US,e.features[0],e.features[1]))
                    }
                    Text("Save label: ${if(unknown) "Unknown location" else floor.graph.nodes[selected]?.displayName ?: "Choose the actual room"}\nPurpose: $split · Session: $session")
                    Button(enabled=!busy && (unknown || selected.isNotBlank()),onClick={ scope.launch {
                        busy=true
                        try { message=withContext(Dispatchers.IO) { store.save(obs.bitmap,obs.vector,if(unknown)null else selected,split,session,obs.evidence,obs.at) }
                            observation=null;summary=withContext(Dispatchers.IO) { store.summary() }
                        } catch(error:Exception) { message=error.message ?: "Capture could not be saved." }
                        finally { busy=false }
                    } }) { Text("Save labelled capture (photo + features)") }
                    if(!unknown && selected.isNotBlank()) TextButton(enabled=!busy,onClick={onUseLocation(selected);onClose()}) { Text("Use my selected room as start") }
                    TextButton(enabled=!busy,onClick={observation=null}) { Text("Discard unsaved capture") }
                }
                if(busy) CircularProgressIndicator()
                message?.let { Text(it) }
                Button(onClick={export.launch("rain-place-dataset.zip")},enabled=!busy && !camera) { Text("Export dataset including photos") }
                TextButton(onClick=onClose,enabled=!busy) { Text("Close AI collection") }
            }
        }
    }
}
