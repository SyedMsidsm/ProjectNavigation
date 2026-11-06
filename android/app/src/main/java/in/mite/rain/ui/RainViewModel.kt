package `in`.mite.rain.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.mite.rain.BuildConfig
import `in`.mite.rain.core.*
import `in`.mite.rain.platform.GraphRepository
import `in`.mite.rain.platform.MotionSource
import `in`.mite.rain.platform.RiskModelLoader
import `in`.mite.rain.platform.SessionLogger
import `in`.mite.rain.platform.UserSettings
import `in`.mite.rain.platform.GuidanceFeedback
import `in`.mite.rain.platform.FloorPackStore
import `in`.mite.rain.platform.FloorPack
import `in`.mite.rain.platform.LoadedFloor
import `in`.mite.rain.platform.TrailLibrary
import `in`.mite.rain.platform.SavedTrail
import `in`.mite.rain.platform.LocalDestinationAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retains the tracker and ordered session writer across screen rotations. */
class RainViewModel(application: Application) : AndroidViewModel(application) {
    private val trailLibrary = TrailLibrary(application)
    private val assistant = LocalDestinationAssistant(application)
    var assistantBusy by mutableStateOf(false); private set
    var assistantStatus by mutableStateOf(if (assistant.available()) "Local model imported" else "No local language model imported"); private set
    var suggestedDestination by mutableStateOf<Node?>(null); private set
    var savedTrails by mutableStateOf<List<SavedTrail>>(emptyList()); private set
    var recording by mutableStateOf(false); private set
    var recordingPaused by mutableStateOf(false); private set
    var trailPreview by mutableStateOf<Graph?>(null); private set
    var trailSteps by mutableStateOf(0); private set
    var trailFloor by mutableStateOf(0); private set
    var trailMessage by mutableStateOf<String?>(null); private set
    var lastSavedTrailId by mutableStateOf<String?>(null); private set
    private var recorder: TrailRecorder? = null
    private var recordingLookAngle = 0.0
    val suggestedPlaceSide get() = when {
        signedTurnDegrees(0.0, recordingLookAngle) in -135.0..-40.0 -> "left"
        signedTurnDegrees(0.0, recordingLookAngle) in 40.0..135.0 -> "right"
        else -> ""
    }
    private var trailTrace: java.io.File? = null
    fun prepareRecording() { if (!recording) { lastSavedTrailId = null; trailMessage = null } }
    fun refreshTrails() { viewModelScope.launch { savedTrails = withContext(Dispatchers.IO) { trailLibrary.list() } } }
    fun startRecording(start: String, floor: Int) {
        if (navigating || mapBusy || assistantBusy || recording) return
        try {
            lastSavedTrailId = null
            recordingLookAngle = 0.0
            recorder = TrailRecorder(stepLength,start,floor); recording = true; recordingPaused = false
            trailMessage = "Hold the phone in your hand. Stop walking while naming a place."
            trailTrace = java.io.File(getApplication<Application>().filesDir,"last-recording-trace.jsonl").apply { writeText("") }
            refreshRecording(); updateSensors()
        } catch (e: Exception) { trailMessage=e.message }
    }
    private fun traceTrail(type: String, angle: Double? = null) {
        runCatching { trailTrace?.appendText(org.json.JSONObject().put("event",type).put("timestamp_ms",android.os.SystemClock.elapsedRealtime()).put("angle_deg",angle).put("input_source","phone_sensor").toString()+"\n") }
    }
    private fun refreshRecording() { recorder?.let { trailPreview=it.preview(); trailSteps=it.steps; trailFloor=it.floor } }
    private fun recordStep() { if(recording && !recordingPaused) { recorder?.step(); traceTrail("step"); refreshRecording() } }
    private fun recordTurn(angle: Double) { if(recording && !recordingPaused) {
        try { recorder?.turn(angle); recordingLookAngle -= angle; traceTrail("turn",angle); refreshRecording() }
        catch(e:Exception) { recordingPaused=true; trailMessage=e.message; updateSensors() }
    } }
    var accelerometerSteps by mutableStateOf(application.getSharedPreferences("rain_motion", android.content.Context.MODE_PRIVATE).getBoolean("accelerometer", false)); private set
    fun toggleStepSource() {
        if (!recording) return
        pauseRecording()
        accelerometerSteps = !accelerometerSteps
        motion.preferAccelerometer = accelerometerSteps
        trailMessage = "Step source changed. Hold the phone steady and tap Continue walking. Motion detection is an estimate and needs a walking check."
    }
    fun pauseRecording() { if(recording) { motion.flushPendingSteps(); recordingPaused=true; updateSensors() } }
    fun resumeRecording() { if(recording && foreground) { recordingLookAngle=0.0; recordingPaused=false; trailMessage="Recording resumed. Keep the same position and heading."; updateSensors() } }
    fun markTrail(name: String, aliases: String, side: String = "", signText: String = "") = editRecording {
        val labels = aliases.split(',').map(String::trim).filter(String::isNotEmpty)
        if(side.isEmpty()) it.mark(name,labels,signText) else it.markBeside(name,labels,side,signText)
    }
    fun joinTrail(id: String) = editRecording { it.join(id) }
    fun changeTrailFloor(floor: Int, method: String) = editRecording { it.changeFloor(floor,method) }
    private fun editRecording(action: (TrailRecorder)->Unit) {
        try { recorder?.let(action); trailMessage="Place updated. Resume only from this position and heading."; refreshRecording() }
        catch(e:Exception) { trailMessage=e.message }
    }
    fun discardRecording() { recorder=null; recording=false; recordingPaused=false; trailPreview=null; updateSensors() }
    val recordedEndpointName get() = recorder?.endpointName.orEmpty()
    fun saveRecording(name:String, backAtStart:Boolean = false, endpoint:String = "") {
        val active=recorder ?: return
        if(mapBusy) return
        val result=try {
            require(active.steps > 0) { "Walk to another place before saving." }
            if (!backAtStart && endpoint.isNotBlank() && endpoint.trim() != active.endpointName) active.mark(endpoint)
            val graph = active.finish()
            if (backAtStart) graph.closeRecordedReturn("T1", active.currentPlaceId) else graph
        } catch(e:Exception) { trailMessage=e.message; return }
        mapBusy=true; recordingPaused=true; updateSensors()
        viewModelScope.launch {
            try {
                lastSavedTrailId = withContext(Dispatchers.IO) { trailLibrary.save(name,result) }
                val saved = withContext(Dispatchers.IO) { trailLibrary.read(checkNotNull(lastSavedTrailId)) }
                floorStore.activate(saved)
                load().join()
                trailMessage="Map saved. Open it to choose your current place and destination."; discardRecording(); refreshTrails()
            } catch(e:Exception) { trailMessage="Could not save: ${e.message}" }
            finally { mapBusy=false }
        }
    }
    fun useTrail(id:String, onReady:()->Unit = {}) {
        if(navigating || recording || mapBusy) return
        mapBusy=true
        viewModelScope.launch {
            try { val pack=withContext(Dispatchers.IO){trailLibrary.read(id)}; floorStore.activate(pack); load().join(); status=loadError?.let { "Could not open map: $it" } ?: "Recorded route selected."
                if(loadError==null) onReady() }
            catch(e:Exception){status="Could not open map: ${e.message}"}
            finally{mapBusy=false}
        }
    }
    fun renameTrail(id:String,name:String) { viewModelScope.launch {
        try{withContext(Dispatchers.IO){trailLibrary.rename(id,name)};refreshTrails()}
        catch(e:Exception){status=e.message}
    } }
    fun deleteTrail(id:String) { viewModelScope.launch {
        try{withContext(Dispatchers.IO){trailLibrary.delete(id)};refreshTrails();status="Saved recording deleted. The active navigation snapshot is unchanged."}
        catch(e:Exception){status=e.message}
    } }
    fun importAssistantModel(uri:android.net.Uri) {
        if(assistantBusy || navigating || recording) return
        assistantBusy=true;assistantStatus="Importing and checking the local model…"
        viewModelScope.launch {
            try{assistant.importModel(uri);assistantStatus="Local model ready. Ask for a destination."}
            catch(e:Exception){assistantStatus="Model could not load: ${e.message?.take(180)}"}
            finally{assistantBusy=false}
        }
    }
    suspend fun describePlace(request:String, places:List<Node>): Node? {
        val catalog = DestinationSearch.semanticCatalog(request,places)
        if (catalog.isEmpty()) return null
        return assistant.suggest(request.trim(),catalog)
    }
    fun askDestination(request:String) {
        if(assistantBusy || navigating || recording) return
        val floor=graph ?: return
        suggestedDestination=null
        val matches=DestinationSearch.matches(request,floor.nodes.values)
        if(matches.size==1){suggestedDestination=matches.single();assistantStatus="Matched a saved name or alias. Confirm the destination.";return}
        if(matches.size>1){assistantStatus="Several places match. Select the correct place and floor from the list.";return}
        assistantBusy=true;assistantStatus="Checking your request on this phone…"
        viewModelScope.launch {
            try {
                val answer=describePlace(request.trim(),floor.nodes.values.filter{it.roomName.isNotBlank()})
                if(graph===floor){suggestedDestination=answer;assistantStatus=if(answer==null) "No valid place suggestion. Choose a saved destination." else "AI suggestion — confirm this place before navigating."}
            } catch(e:Exception){assistantStatus=e.message?.take(200) ?: "Local assistant unavailable. Use name search."}
            finally{assistantBusy=false}
        }
    }
    fun confirmSuggestedDestination(){suggestedDestination?.let{if(graph?.nodes?.get(it.id)==it){selectDestination(it.id);status="Destination set to ${it.displayName}. Open Navigate to choose your starting place."}};suggestedDestination=null}
    fun confirmPosition(id:String) { lastRecognitionSource="manual_place_confirmation";acceptRecognition(id) }
    fun confirmFloorChange(){tracker?.confirmFloorChange()?.let(::updateNavigation);updateSensors()}
    private val preferences = UserSettings(application)
    private val floorStore = FloorPackStore(application)
    var currentFloor by mutableStateOf<LoadedFloor?>(null); private set
    var pendingFloor by mutableStateOf<FloorPack?>(null); private set
    var mapBusy by mutableStateOf(false); private set
    var mapMessage by mutableStateOf<String?>(null); private set
    var graph by mutableStateOf<Graph?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var startId by mutableStateOf(""); private set
    var destinationId by mutableStateOf(""); private set
    var kind by mutableStateOf(PlannerKind.RISK); private set
    var lambda by mutableStateOf(preferences.lambda); private set
    var stepLength by mutableStateOf(preferences.stepLength); private set
    var voiceEnabled by mutableStateOf(preferences.voice); private set
    var hapticsEnabled by mutableStateOf(preferences.haptics); private set
    var promptMode by mutableStateOf(preferences.promptMode); private set
    var feedbackStatus by mutableStateOf("Voice guidance off"); private set
    var calibrationSummary by mutableStateOf(preferences.calibrationSummary); private set
    var calibrationSteps by mutableStateOf(0); private set
    var calibrationRecording by mutableStateOf(false); private set
    var calibrationResult by mutableStateOf<CalibrationResult?>(null); private set
    var calibrationMessage by mutableStateOf("Measure a straight distance first. Hold the phone as you will during navigation."); private set
    var calibrationTurns by mutableStateOf<List<Double>>(emptyList()); private set
    private val calibration = StepCalibration()
    private var calibrationSensor = ""
    var routes by mutableStateOf<Map<PlannerKind, Route?>>(emptyMap()); private set
    var activeRoute by mutableStateOf<Route?>(null); private set
    var navigation by mutableStateOf<NavigationState?>(null); private set
    var navigating by mutableStateOf(false); private set
    var liveMotion by mutableStateOf(false); private set
    var steps by mutableStateOf(0); private set
    var displayHeading by mutableStateOf(0.0); private set
    private var rawHeading = 0.0
    var headingAligned by mutableStateOf(false); private set
    fun toggleGuidanceVoice() {
        voiceEnabled = !voiceEnabled
        preferences.save(lambda, stepLength, voiceEnabled, hapticsEnabled, promptMode)
        feedback.configure(voiceEnabled, hapticsEnabled)
    }
    fun alignHeading() {
        displayHeading = activeRoute?.arcs?.getOrNull(navigation?.currentArcIndex ?: 0)?.bearingDeg ?: 0.0
        rawHeading = displayHeading
        headingAligned = true
    }
    var sensorStatus by mutableStateOf("Sensors idle"); private set
    var status by mutableStateOf<String?>(null); private set
    var recognition by mutableStateOf<RecognitionResult?>(null); private set
    var showScanner by mutableStateOf(false); private set
    var hasSession by mutableStateOf(false); private set
    private var tracker: NavigationTracker? = null
    private var planner: RoutePlanner? = null
    private val motion = MotionSource(application)
    private val logger = SessionLogger(application)
    private val feedback = GuidanceFeedback(application) { feedbackStatus = it }
    private var foreground = false
    private var interactionPaused = false
    private var sessionStartedMs = 0L
    private var walkedBeforeReplan = 0.0
    private var promptStartedMs: Long? = null
    private var lastRecognitionSource = "camera_ocr"
    private var arToken: String? = null

    fun beginAr(): android.content.Intent? {
        val route = activeRoute ?: return null
        val state = navigation ?: return null
        if (!navigating || state.offRouteReason != null || state.arrived || arToken != null) return null
        if(route.arcs.any{it.transition.isNotBlank()}) { status="Use map navigation for this multi-floor route. AR currently supports a single floor."; return null }
        arToken = java.util.UUID.randomUUID().toString()
        feedback.pause(); updateSensors()
        event("ar_started", mapOf("input_source" to "arcore_visual_inertial", "demo_mode" to false,
            "arc" to state.currentArcIndex, "offset_m" to state.offsetM))
        return `in`.mite.rain.ar.ArPreviewActivity.intent(getApplication(), route, state)
            .putExtra("token", arToken).putExtra("floor_name", currentFloor?.name)
            .putExtra("synthetic", currentFloor?.synthetic != false)
            .putExtra("voice", voiceEnabled).putExtra("haptics", hapticsEnabled)
    }

    fun finishAr(data: android.content.Intent?) {
        val token = arToken ?: return
        arToken = null
        val current = navigation ?: return
        val valid = data?.getStringExtra("token") == token
        val index = if (valid) data!!.getIntExtra("arc", current.currentArcIndex) else current.currentArcIndex
        val offset = if (valid) data!!.getDoubleExtra("offset", current.offsetM) else current.offsetM
        val needsFix = !valid || data!!.getBooleanExtra("needs_fix", true)
        val next = runCatching { tracker?.applyArProgress(index, offset, needsFix) }.getOrNull()
            ?: tracker?.applyArProgress(current.currentArcIndex,
                current.offsetM.coerceAtMost(activeRoute!!.arcs[current.currentArcIndex].lengthM), true)
        next?.let(::updateNavigation)
        event("ar_finished", mapOf("input_source" to "arcore_visual_inertial", "demo_mode" to false, "arc" to next?.currentArcIndex,
            "offset_m" to next?.offsetM, "needs_location_fix" to (next?.offRouteReason != null),
            "ar_distance_m" to if (valid) data!!.getDoubleExtra("along", 0.0) else null))
        status = if (next?.offRouteReason != null) "AR needs a location check. Scan a room sign before continuing."
            else "AR progress saved to this journey."
        updateSensors()
    }

    init { feedback.configure(voiceEnabled, hapticsEnabled); load(); refreshTrails() }

    fun load(): kotlinx.coroutines.Job {
        suggestedDestination = null
        loadError = null
        return viewModelScope.launch {
            try {
                val loaded = floorStore.load()
                val floor = loaded.graph
                val risk = withContext(Dispatchers.IO) { RiskModelLoader.load(getApplication()) }
                planner = RoutePlanner(floor, risk)
                startId = if ("ENT" in floor.nodes) "ENT" else floor.nodes.keys.first()
                destinationId = when {
                    "LIBRARY" in floor.nodes -> "LIBRARY"
                    "N04" in floor.nodes -> "N04"
                    else -> floor.nodes.values.lastOrNull { it.roomName.isNotBlank() }?.id ?: floor.nodes.keys.last()
                }
                currentFloor = loaded
                graph = floor
                compareRoutes()
            } catch (error: Exception) {
                loadError = error.message ?: "The floor map could not be loaded."
            }
        }
    }

    fun selectStart(id: String) { if (!navigating) { startId = id; compareRoutes() } }
    fun selectDestination(id: String) { if (!navigating) { destinationId = id; compareRoutes() } }
    fun selectKind(value: PlannerKind) { if (!navigating) kind = value }
    fun saveSettings(newLambda: Double, newStepLength: Double, voice: Boolean, haptics: Boolean, mode: PromptMode) {
        if (navigating) return
        preferences.save(newLambda, newStepLength, voice, haptics, mode)
        lambda = newLambda; stepLength = newStepLength
        voiceEnabled = voice; hapticsEnabled = haptics; promptMode = mode
        calibrationSummary = preferences.calibrationSummary
        feedback.configure(voice, haptics); compareRoutes()
    }
    private fun settings() = PlannerSettings(lambda = lambda)
    private fun compareRoutes() {
        routes = PlannerKind.entries.associateWith { planner?.plan(startId, destinationId, it, settings()) }
    }

    fun beginNavigation() {
        if (mapBusy || calibrationRecording || recording || assistantBusy) return
        val route = routes[kind] ?: return
        if (route.arcs.isEmpty()) return
        sessionStartedMs = System.currentTimeMillis()
        walkedBeforeReplan = 0.0; steps = 0; status = null; promptStartedMs = null
        activeRoute = route
        headingAligned = liveMotion
        displayHeading = route.arcs.first().bearingDeg
        rawHeading = displayHeading
        tracker = NavigationTracker(graph!!, route, stepLength, settings(), promptMode)
        navigation = null
        logger.start(metadata() + mapOf(
            "participant_code" to "DEMO", "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "planner" to kind.name.lowercase(), "lambda" to lambda, "start_node" to startId,
            "dest_node" to destinationId, "app_version" to BuildConfig.VERSION_NAME,
            "step_length_m" to stepLength, "risk_model_type" to "hand_weighted_uncalibrated",
            "prompt_mode" to promptMode.name.lowercase(), "floor_digest" to currentFloor?.digest,
        ))
        navigating = true; hasSession = true
        logRoute(route)
        updateNavigation(tracker!!.snapshot())
        updateSensors()
    }

    private fun metadata(): Map<String, Any?> = mapOf("schema_version" to "0.2", "data_source" to (currentFloor?.source ?: "synthetic_floor"), "demo_mode" to !liveMotion)
    private fun event(name: String, fields: Map<String, Any?> = emptyMap()) { logger.event(name, metadata() + fields) }
    private fun logRoute(route: Route) {
        event("route_planned", mapOf("nodes" to route.nodes, "edges" to route.arcs.map { it.edgeId },
            "total_length_m" to route.totalLengthM, "expected_errors" to route.expectedErrors,
            "scheduled_prompts" to route.scheduledPrompts, "risk_model_type" to "hand_weighted_uncalibrated"))
    }

    fun step(simulated: Boolean = false) {
        if (!navigating || arToken != null || interactionPaused || showScanner || recognition != null || simulated == liveMotion) return
        val previous = navigation
        if(previous?.floorChangePending == true) return
        if (previous?.offRouteReason != null && previous.returnSteps == null) return
        val next = tracker?.step() ?: return
        steps++
        updateNavigation(next)
        event("step", mapOf("step_count" to steps, "pdr_edge_id" to activeRoute?.arcs?.getOrNull(next.currentArcIndex)?.edgeId,
            "pdr_offset_m" to next.offsetM, "heading_rel_deg" to null,
            "input_source" to if (simulated) "simulated_steps" else "phone_sensor"))
    }

    fun beginReturn() { tracker?.beginReturn()?.let(::updateNavigation) }
    fun turn(angle: Double, simulated: Boolean = false) {
        if (!navigating || arToken != null || interactionPaused || showScanner || recognition != null || simulated == liveMotion) return
        event("turn_detected", mapOf("angle_deg" to angle, "at_node_estimate" to navigation?.currentNodeId,
            "input_source" to if (simulated) "simulated_turn" else "phone_sensor"))
        tracker?.turn(angle)?.let(::updateNavigation)
    }

    private fun updateNavigation(next: NavigationState) {
        val previous = navigation
        navigation = next
        val route = activeRoute
        val endIndex = route?.guidanceEndIndex(next.currentArcIndex)
        val turn = endIndex?.let { route?.transitions?.getOrNull(it + 1) }
        val approaching = route != null && previous != null && turn != null && kotlin.math.abs(turn.angleDeg) >= 45 &&
            route.distanceToGuidanceEnd(next) <= 3 && route.guidanceEndIndex(previous.currentArcIndex) == endIndex &&
            route.distanceToGuidanceEnd(previous) > 3 && next.offRouteReason == null && !next.arrived
        val turnCue = if (approaching) when {
            kotlin.math.abs(turn!!.angleDeg) > 135 -> "Just ahead, turn back."
            turn.angleDeg > 0 -> "Just ahead, turn right."
            else -> "Just ahead, turn left."
        } else null
        val cueChanged = approaching || previous == null || route?.guidanceEndIndex(previous.currentArcIndex) != endIndex ||
            previous.arrived != next.arrived || previous.offRouteReason != next.offRouteReason || previous.promptReason != next.promptReason || previous.returning != next.returning
        if (cueChanged && foreground && !interactionPaused && !showScanner && recognition == null)
            feedback.announce(turnCue ?: if (next.promptReason != null && next.offRouteReason == null) "Check a nearby room sign. ${next.instruction}" else next.instruction,
                next.offRouteReason != null || next.promptReason != null)
        if (next.offRouteReason != null && next.offRouteReason != previous?.offRouteReason)
            event("off_route", mapOf("reason" to next.offRouteReason, "confidence" to null))
        if (next.promptReason != null && next.promptReason != previous?.promptReason) {
            promptStartedMs = System.currentTimeMillis()
            event("prompt_shown", mapOf("reason" to next.promptReason, "node_estimate" to next.currentNodeId, "p_wrong" to null))
        }
    }

    fun changeMotionMode(value: Boolean) {
        liveMotion = value
        headingAligned = false
        if (navigating) event("input_mode_changed", mapOf("input_source" to if (value) "phone_sensor" else "demo_controls"))
        updateSensors()
    }
    fun changeForegroundState(value: Boolean) {
        foreground = value
        if (!value) {
            headingAligned = false
            feedback.pause()
            if(navigating && navigation?.offRouteReason == null && liveMotion && arToken == null && !showScanner && !interactionPaused)
                tracker?.requirePositionCheck("navigation_interrupted")?.let(::updateNavigation)
            if(recording){motion.flushPendingSteps();recordingPaused=true;trailMessage="Recording paused while the app was away. Return to the same place and heading before resuming."}
            if (calibrationRecording) {
                calibration.interrupt(); calibrationRecording = false; calibrationResult = null
                calibrationMessage = "Walk interrupted while the app was paused. Start a new trial so no missed steps enter calibration."
            }
        }
        updateSensors()
    }
    fun pauseForInteraction(value: Boolean) { interactionPaused = value; if (value) { headingAligned = false; feedback.pause() }; updateSensors() }
    private fun updateSensors() {
        if(recording) {
            if(foreground && !recordingPaused) motion.start(::recordStep, ::recordTurn,
                onHeadingDelta = { recordingLookAngle += it }, recordingTrail = true) else motion.stop()
            sensorStatus=motion.status;return
        }
        if (foreground && calibrationRecording) return
        if (foreground && navigating && liveMotion && navigation?.floorChangePending != true && arToken == null && !interactionPaused && !showScanner && recognition == null) {
            motion.start(navigating = true, onStep = { step() }, onTurn = { turn(it) },
                onHeadingDelta = { delta ->
                    if (headingAligned) {
                        rawHeading += delta
                        val difference = rawHeading - displayHeading
                        if (kotlin.math.abs(difference) > .45) displayHeading += difference * .35
                    }
                })
        } else motion.stop()
        sensorStatus = motion.status
    }

    fun openScanner() { headingAligned = false; feedback.pause(); showScanner = true; updateSensors() }
    fun closeScanner() { showScanner = false; updateSensors() }
    fun recognize(text: String, source: String) {
        val floor = graph ?: return
        showScanner = false
        lastRecognitionSource = source
        val result = RoomNameMatcher(floor.nodes.values).match(text)
        recognition = result
        event("recognition", mapOf("method" to "text", "top1_node" to result.best?.nodeId,
            "confidence" to (result.best?.probability ?: 0.0), "margin" to result.margin,
            "top3" to result.topCandidates.map { mapOf("node_id" to it.nodeId, "score" to it.score, "probability" to it.probability) },
            "ocr_raw_text_hash" to logger.hashText(text), "accepted" to result.accepted, "input_source" to source))
        promptStartedMs?.let { start ->
            event("prompt_result", mapOf("scanned" to (source == "camera_ocr"), "recognition_top1" to result.best?.nodeId,
                "seconds_to_respond" to (System.currentTimeMillis() - start) / 1000.0, "input_source" to source))
            promptStartedMs = null
        }
        updateSensors()
    }

    fun dismissRecognition() { recognition = null; updateSensors() }
    fun acceptRecognition(nodeId: String) {
        val floor = graph ?: return
        if (nodeId !in floor.nodes) return
        event("location_confirmed", mapOf("node_id" to nodeId, "confirmation" to "manual", "input_source" to lastRecognitionSource))
        recognition = null
        if (!navigating) {
            startId = nodeId; compareRoutes()
            status = "Starting point set to ${floor.nodes.getValue(nodeId).displayName}."
        } else {
            val route = planner?.plan(nodeId, destinationId, kind, settings())
            if (route == null) status = "No walkable route from this location. End this walk and choose another destination."
            else {
                walkedBeforeReplan += navigation?.distanceWalkedM ?: 0.0
                activeRoute = route
                headingAligned = false
                tracker = NavigationTracker(floor, route, stepLength, settings(), promptMode)
                navigation = null
                promptStartedMs = null
                event("replan", mapOf("from_node" to nodeId, "new_route_nodes" to route.nodes))
                logRoute(route)
                updateNavigation(tracker!!.snapshot())
            }
        }
        updateSensors()
    }

    fun finishNavigation(completed: Boolean) {
        if (!navigating) return
        if (completed && navigation?.arrived != true) return
        if (completed) event("arrival", mapOf("node_id" to destinationId, "correct" to null,
            "ground_truth_verified" to false, "total_time_s" to (System.currentTimeMillis() - sessionStartedMs) / 1000.0,
            "distance_walked_m" to (walkedBeforeReplan + (navigation?.distanceWalkedM ?: 0.0))))
        logger.finish(completed)
        feedback.pause()
        navigating = false; updateSensors()
        status = if (completed) "Walkthrough complete. Export the labelled session for review." else "Walk ended. Your session log is ready to export."
    }

    fun export() {
        if (!logger.share()) status = "Start a walkthrough to create a session log."
        logger.lastError?.let { status = it }
    }

    fun repeatGuidance() {
        if (foreground && navigating && !showScanner && recognition == null) {
            feedback.announce(navigation?.instruction ?: return, navigation?.offRouteReason != null)
        }
    }
    fun beginCalibration(distance: Double) {
        if (navigating || !foreground || mapBusy) return
        calibrationResult = null
        try { calibration.start(distance) } catch (error: IllegalArgumentException) { calibrationMessage = error.message!!; return }
        calibrationSteps = 0; calibrationTurns = emptyList(); calibrationResult = null; calibrationRecording = true
        motion.start(onStep = { if (calibrationRecording) { calibration.step(); calibrationSteps = calibration.steps } },
            onTurn = { if (calibrationRecording) calibrationTurns = (calibrationTurns + it).takeLast(8) })
        calibrationSensor = motion.status
        calibrationMessage = "Walk the measured distance, then stop counting. $calibrationSensor"
    }
    fun stopCalibration() {
        if (!calibrationRecording) return
        motion.stop(); calibrationRecording = false
        try {
            calibrationResult = calibration.stop()
            calibrationMessage = "Review the result before saving. Repeat if you changed the phone pose or missed the marked endpoints."
        } catch (error: IllegalArgumentException) { calibrationResult = null; calibrationMessage = error.message!! }
    }
    fun saveCalibration() {
        val result = calibrationResult ?: return
        if (navigating || calibrationRecording) return
        preferences.saveCalibration(result, calibrationSensor); stepLength = result.stepLengthM
        calibrationSummary = preferences.calibrationSummary; calibrationResult = null
        calibrationMessage = "Calibration saved. Verify it on a second measured walk."
    }
    fun closeCalibration() {
        calibration.interrupt(); calibrationRecording = false; calibrationResult = null
        motion.stop(); updateSensors()
    }
    fun previewFloor(uri: android.net.Uri) {
        if (navigating || mapBusy || calibrationRecording) return
        mapBusy = true; mapMessage = null; pendingFloor = null
        viewModelScope.launch {
            try {
                pendingFloor = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { FloorPackStore.parse(FloorPackStore.readLimited(it)) }
                        ?: error("The selected file could not be opened.")
                }
            } catch (error: Exception) { mapMessage = "Map not loaded: ${error.message?.take(180) ?: "invalid floor pack"}" }
            finally { mapBusy = false }
        }
    }
    fun previewGroundFloor() {
        if (navigating || mapBusy || calibrationRecording) return
        mapBusy = true; pendingFloor = null; mapMessage = null
        viewModelScope.launch {
            try {
                pendingFloor = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open("ground-floor.json").use { FloorPackStore.parse(FloorPackStore.readLimited(it)) }
                }
            } catch (error: Exception) { mapMessage = "Ground floor could not be loaded: ${error.message?.take(160)}" }
            finally { mapBusy = false }
        }
    }
    fun activateFloor() {
        val pack = pendingFloor ?: return
        if (navigating || mapBusy) return
        mapBusy = true
        viewModelScope.launch {
            try { floorStore.activate(pack); pendingFloor = null; mapMessage = "Map activated. Measurements still need a field check."; graph = null; load() }
            catch (_: Exception) { mapMessage = "Map could not be saved. The previous floor remains active." }
            finally { mapBusy = false }
        }
    }
    fun restoreDemoFloor() {
        if (navigating || mapBusy) return
        mapBusy = true
        viewModelScope.launch {
            try { floorStore.restoreDemo(); pendingFloor = null; mapMessage = "Sample map restored."; graph = null; load() }
            catch (_: Exception) { mapMessage = "Could not restore the sample map." }
            finally { mapBusy = false }
        }
    }
    fun discardFloorPreview() { if (!mapBusy) pendingFloor = null }
    fun observerTag(tag: String) {
        if (navigating && tag in setOf("observed_wrong_turn", "observed_correct_arrival", "observed_incorrect_arrival", "checkpoint")) {
            event("observer_tag", mapOf("tag" to tag, "node_id" to navigation?.currentNodeId))
            status = "Observer tag saved: ${tag.replace('_', ' ')}. Node is the app estimate, not independently verified."
        }
    }
    override fun onCleared() { motion.stop(); feedback.close(); logger.close(); super.onCleared() }
}
