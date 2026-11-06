package `in`.mite.rain.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import `in`.mite.rain.core.DestinationSearch
import `in`.mite.rain.core.Node
import `in`.mite.rain.core.PlannerKind
import kotlin.math.roundToInt
import `in`.mite.rain.platform.OfflineDestinationSpeech
import `in`.mite.rain.platform.RoomScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** OCR is an editable suggestion. The camera opens only after an explicit tap. */
@Composable
internal fun SignOffer(onUse: (String)->Unit, onClose: ()->Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text(if(scanning) "Read the room sign" else "Is there a sign nearby?")},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            if(scanning) {
                RoomScanner(onText={ if(it.isNotBlank()) label=it.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString(" ").take(80) },onClose=onClose)
                Text(label.ifBlank { "Point the camera at a room name or label." })
            } else Text("Use a room sign to fill in your current place.")
        }},confirmButton={TextButton(onClick={if(scanning) onUse(label) else scanning=true},enabled=!scanning || label.isNotBlank()){Text(if(scanning)"Use this text" else "Scan sign")}},
        dismissButton={TextButton(onClick=onClose){Text("Skip")}})
}

@Composable
internal fun WalkSetup(model:RainViewModel, onMaps:()->Unit, onStart:()->Unit, onBack:()->Unit) {
    val graph=model.graph ?: return
    val places=graph.nodes.values.filter { it.roomName.isNotBlank() }
    var destination by rememberSaveable { mutableStateOf(false) }
    var scan by rememberSaveable { mutableStateOf(true) }
    var scanned by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var confirmId by rememberSaveable { mutableStateOf("") }
    fun back() { if(destination) destination=false else onBack() }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=::back){Text("← Back")}
            TextButton(onClick=onMaps){Text(model.currentFloor?.name ?: "Saved maps")}
        }
        Text(if(destination)"Where do you want to go?" else "Where are you now?",style=MaterialTheme.typography.headlineMedium)
        if(destination) Text("From ${graph.nodes[from]?.displayName.orEmpty()}",color=Muted)
        if(destination) Text("Face down the first corridor before starting guidance.",style=MaterialTheme.typography.bodySmall,color=Muted)
        key(graph,destination,scanned) {
            PlaceQuestion(if(destination) places.filter { it.id!=from } else places,
                if(destination) "" else scanned, destination,model) { node ->
                if(destination) { model.selectDestination(node.id); model.selectKind(PlannerKind.SHORTEST); confirmId=node.id }
                else { from=node.id; model.selectStart(node.id); destination=true }
            }
        }
        if(destination && places.none { it.id!=from }) Text("This map needs a second named place. Choose another saved map.")
        if(!destination) TextButton(onClick={scan=true}){Text("Scan a room sign")}
    }
    if(scan) SignOffer(onUse={scanned=it;scan=false},onClose={scan=false})
    graph.nodes[confirmId]?.let { place ->
        val shortest=model.routes[PlannerKind.SHORTEST]
        val risk=model.routes[PlannerKind.RISK]
        AlertDialog(onDismissRequest={confirmId=""},title={Text("Navigate to ${place.displayName}?")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("From ${graph.nodes[from]?.displayName.orEmpty()}")
                for ((kind,label) in listOf(PlannerKind.SHORTEST to "Shortest",PlannerKind.RISK to "Risk-aware")) {
                    model.routes[kind]?.let { route ->
                        FilterChip(selected=model.kind==kind,onClick={model.selectKind(kind)},label={
                            Text("$label · ${route.totalLengthM.roundToInt()} m · ${route.transitions.count { kotlin.math.abs(it.angleDeg)>=45 }} turns")
                        })
                    }
                }
                Text(if(shortest==null) "No connected route was found." else if(shortest.nodes==risk?.nodes)
                    "Both options follow the same path." else "Risk-aware weighs distance, junctions, turns and available signs.")
            }},confirmButton={TextButton(enabled=model.routes[model.kind]!=null,onClick={confirmId="";onStart()}){Text("Start navigation")}},
            dismissButton={TextButton(onClick={confirmId=""}){Text("Change destination")}})
    }
}

@Composable
private fun PlaceQuestion(places:List<Node>, initial:String, destination:Boolean, model:RainViewModel, onPick:(Node)->Unit) {
    var query by rememberSaveable { mutableStateOf(initial) }
    var selected by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf(emptyList<Node>()) }
    val scope=rememberCoroutineScope()
    fun edit(value:String) { query=value.take(500); generation++; selected=""; suggestions=emptyList();status="";busy=false }
    val context=LocalContext.current
    val speech=remember { OfflineDestinationSpeech(context) }
    DisposableEffect(Unit){ onDispose { speech.close() } }
    fun listen(){speech.listen({ edit(it) },{status=it})}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ if(it)listen() else status="Type a place or choose one below." }
    val matches=if(query.isBlank()) emptyList() else DestinationSearch.matches(query,places)
    OutlinedTextField(query,::edit,singleLine=true,label={Text(if(destination)"Place name or request" else "Current place")},modifier=Modifier.fillMaxWidth(),
        supportingText=if(destination) ({Text("For example, “I want to wash my face”")}) else null)
    Row {
        TextButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)listen() else permission.launch(Manifest.permission.RECORD_AUDIO)}){Text("Speak")}
        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide saved places" else "Choose a saved place ▾")}
    }
    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text(if(destination)"Choose destination" else "Choose current place")},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            suggestions.ifEmpty { places }.forEach { node -> TextButton(onClick={edit(node.roomName);selected=node.id;expanded=false;onPick(node)}){Text("${node.displayName} · floor ${node.floor}")} }
        }},confirmButton={TextButton(onClick={expanded=false}){Text("Close")}})
    val shown=if(selected.isBlank() && !expanded) matches.take(8) else emptyList()
    shown.forEach { node -> TextButton(onClick={edit(node.roomName);selected=node.id;expanded=false}){Text("${node.displayName} · floor ${node.floor}")} }
    if(status.isNotBlank()) Text(status,color=Muted)
    val choice=places.find { it.id==selected } ?: matches.singleOrNull()
    Button(enabled=query.isNotBlank() && !busy,modifier=Modifier.fillMaxWidth(),onClick={
        if(choice!=null) onPick(choice)
        else if(matches.size>1){suggestions=matches;expanded=true}
        else if(destination) {
            val token=generation
            busy=true
            scope.launch {
                try { val catalog=DestinationSearch.semanticCatalog(query,places)
                    val answers=if(catalog !== places) catalog else listOfNotNull(model.describePlace(query,places))
                    if(token==generation){if(answers.size==1) onPick(answers.single()) else {
                        suggestions=answers;expanded=true;status=if(answers.isEmpty()) "No match. Choose a saved place." else ""
                    }}
                } catch(e:CancellationException){throw e}
                catch(e:Exception){if(token==generation)status="Search unavailable. Choose a saved place."}
                finally { if(token==generation)busy=false }
            }
        } else {expanded=true;status="Choose your current place from this map."}
    }){Text(when { busy -> "Finding place…"; choice!=null && destination -> "Navigate to ${choice.displayName}"; choice!=null -> "Continue from ${choice.displayName}"; else -> "Find place" })}
}
