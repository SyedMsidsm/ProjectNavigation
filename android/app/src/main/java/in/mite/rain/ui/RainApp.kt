package `in`.mite.rain.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.mite.rain.platform.RoomScanner

@Composable
fun RainApp(model: RainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSample by rememberSaveable { mutableStateOf(false) }
    var showCalibration by rememberSaveable { mutableStateOf(false) }
    var showMaps by rememberSaveable { mutableStateOf(false) }
    var showCollection by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("home") }
    var mapFloor by rememberSaveable { mutableStateOf(0) }
    val floorPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::previewFloor) }
    val calibrationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.changeMotionMode(true) }
    val arLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.finishAr(it.data) }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.changeForegroundState(true)
            if (event == Lifecycle.Event.ON_PAUSE) model.changeForegroundState(false)
        }
        lifecycle.addObserver(observer)
        model.changeForegroundState(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); model.changeForegroundState(false) }
    }
    DisposableEffect(showSample, showSettings, showCalibration, showMaps, showCollection, model) {
        model.pauseForInteraction(showSample || showSettings || showCalibration || showMaps || showCollection)
        onDispose { model.pauseForInteraction(false) }
    }
    BackHandler(enabled = model.navigating && !model.showScanner && model.recognition == null) { model.finishNavigation(false) }
    BackHandler(enabled = !model.navigating && !model.recording && page != "home") { page="home" }
    val graph = model.graph
    Surface(Modifier.fillMaxSize(), color = CanvasColor) {
        if (graph == null) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("RAIN", style = MaterialTheme.typography.headlineLarge)
                    if (model.loadError == null) { CircularProgressIndicator(); Text("Preparing your offline floor map…") }
                    else {
                        Text(model.loadError!!); Button(onClick = model::load) { Text("Retry loading map") }
                        Button(onClick = model::restoreDemoFloor, enabled = !model.mapBusy) { Text("Restore sample map") }
                    }
                }
            }
        } else {
            val route = model.activeRoute
            val state = model.navigation
            if (model.navigating && route != null && state != null) {
                NavigateScreen(graph, route, model.kind, state, model.steps, model.liveMotion, model.sensorStatus,
                    floorName = model.currentFloor?.name.orEmpty(), synthetic = model.currentFloor?.synthetic != false,
                    feedbackStatus = model.feedbackStatus, promptPolicy = model.promptMode.displayName(),
                    planDerived = model.currentFloor?.source == "plan_derived_floor",
                    onRepeat = model::repeatGuidance, onObserver = model::observerTag, observerStatus = model.status,
                    onConfirmPosition = model::confirmPosition, onConfirmFloor = model::confirmFloorChange,
                    stepLengthM = model.stepLength,
                    voiceOn = model.voiceEnabled, onVoice = model::toggleGuidanceVoice,
                    headingDegrees = model.displayHeading, headingAligned = model.headingAligned, onAlignHeading = model::alignHeading,
                    onLiveMotion = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        else model.changeMotionMode(enabled)
                    },
                    onDemoSteps = { repeat(5) { model.step(simulated = true) } },
                    onDemoTurn = { model.turn(it, simulated = true) }, onScan = model::openScanner,
                    onSampleSign = { showSample = true }, onFinish = { model.finishNavigation(true) },
                    onExit = { model.finishNavigation(false) }, onExport = model::export,
                    onAr = {
                        model.beginAr()?.let { arLauncher.launch(it) }
                    })
            } else if(page=="home") {
                TrailHome(model,onRecord={model.prepareRecording();page="record"},onNavigate={page="walk"},onMap={mapFloor=graph.nodes[model.startId]?.floor ?: 0;page="map"},
                    onLibrary={page="library"},onCampus={page="plan"},onAssistant={page="assistant"})
            } else if(page=="record") {
                TrailRecordScreen(model,onNavigate={page="walk"}){page="home"}
            } else if(page=="library") {
                TrailLibraryScreen(model,onUse={page="walk"}){page="home"}
            } else if(page=="walk") {
                key(model.currentFloor?.digest) {
                    WalkSetup(model,onMaps={page="library"},onBack={page="home"},onStart={
                        model.changeMotionMode(true)
                        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
                            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        model.beginNavigation()
                    })
                }
            } else if(page=="assistant") {
                AssistantScreen(model){page="home"}
            } else if(page=="map") {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    androidx.compose.material3.TextButton(onClick={page="home"}){Text("← Home")}
                    Text("Floor map",style=MaterialTheme.typography.headlineLarge)
                    androidx.compose.foundation.layout.Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        graph.nodes.values.map{it.floor}.distinct().sorted().forEach{f->
                            androidx.compose.material3.TextButton(onClick={mapFloor=f}){Text("Floor $f")}
                        }
                    }
                    FloorMap(graph,model.routes[model.kind],model.startId,model.destinationId,floorName=model.currentFloor?.name.orEmpty(),synthetic=model.currentFloor?.synthetic!=false,selectedFloor=mapFloor)
                    Button(onClick={page="walk"}){Text("Choose a destination")}
                }
            } else {
                Column {
                androidx.compose.material3.TextButton(onClick={page="home"}){Text("← Home")}
                PlanScreen(graph, model.startId, model.destinationId, model.kind, model.routes,
                    model::selectStart, model::selectDestination, model::selectKind,
                    onSettings = { showSettings = true }, onScan = model::openScanner,
                    onDemoSign = { showSample = true }, onNavigate = {
                        model.changeMotionMode(true)
                        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        model.beginNavigation()
                    },
                    onExport = model::export, hasSession = model.hasSession, status = model.status,
                    floorName = model.currentFloor?.name.orEmpty(), synthetic = model.currentFloor?.synthetic != false,
                    ready = !model.mapBusy && !model.assistantBusy,
                    searchPanel = { DestinationSearchPanel(model) },
                    planDerived = model.currentFloor?.source == "plan_derived_floor",
                    onMaps = { showMaps = true },
                    onCollect = { showCollection = true },
                    onCalibration = {
                        showCalibration = true
                        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                            calibrationPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    })
                }
            }
            if (showCollection) model.currentFloor?.let { PlaceCollectionDialog(it, model::selectStart) { showCollection = false } }
            if (showSettings) SettingsDialog(model.lambda, model.stepLength, model.voiceEnabled, model.hapticsEnabled, model.promptMode,
                onSave = { lambda, step, voice, haptics, mode -> model.saveSettings(lambda, step, voice, haptics, mode); showSettings = false }, onClose = { showSettings = false })
            if (showCalibration) CalibrationDialog(model) { model.closeCalibration(); showCalibration = false }
            if (showMaps) FloorDialog(model, onImport = { floorPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onClose = { model.discardFloorPreview(); showMaps = false })
            if (showSample) SampleTextDialog(graph, model.navigation?.currentNodeId ?: model.startId,
                onMatch = { text -> showSample = false; model.recognize(text, "typed_demo_text") }, onClose = { showSample = false })
            model.recognition?.let { RecognitionDialog(it, model.navigating, model::acceptRecognition, model::dismissRecognition) }
            if (model.showScanner) Dialog(onDismissRequest = model::closeScanner,
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
                Surface(Modifier.fillMaxSize()) {
                    var detectedText by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Eyebrow("ON-DEVICE OCR")
                        Text("Read a room sign", style = MaterialTheme.typography.headlineMedium)
                        RoomScanner(onText = { detectedText = it }, onClose = model::closeScanner)
                        Text(if (detectedText.isBlank()) "Waiting for readable text…" else detectedText,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                        Button(onClick = { model.recognize(detectedText, "camera_ocr") }, enabled = detectedText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()) { Text("Match detected text") }
                        Text("Only a hash and room match scores enter the session log. Raw text and camera frames are not saved.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
    }
}
