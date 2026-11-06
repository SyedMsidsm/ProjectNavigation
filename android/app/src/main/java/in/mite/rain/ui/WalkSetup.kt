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
    fun back() { if(destination) destination=false else onBack() }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=::back){Text("← Back")}
            TextButton(onClick=onMaps){Text(model.currentFloor?.name ?: "Saved maps")}
        }
        Text(if(destination)"Where do you want to go?" else "Where are you now?",style=MaterialTheme.typography.headlineMedium)
        if(destination) Text("From ${graph.nodes[from]?.displayName.orEmpty()}",color=Muted)
        key(graph,destination,scanned) {
            PlaceQuestion(if(destination) places.filter { it.id!=from } else places,
                if(destination) "" else scanned, destination,model) { node ->
                if(destination) { model.selectDestination(node.id); onStart() }
                else { from=node.id; model.selectStart(node.id); destination=true }
            }
        }
        if(destination && places.none { it.id!=from }) Text("This map needs a second named place. Choose another saved map.")
        if(!destination) TextButton(onClick={scan=true}){Text("Scan a room sign")}
    }
    if(scan) SignOffer(onUse={scanned=it;scan=false},onClose={scan=false})
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
    val shown=when { expanded -> places; selected.isNotBlank() -> emptyList(); suggestions.isNotEmpty() -> suggestions; else -> matches.take(8) }
    shown.forEach { node -> TextButton(onClick={edit(node.roomName);selected=node.id;expanded=false}){Text("${node.displayName} · floor ${node.floor}")} }
    if(status.isNotBlank()) Text(status,color=Muted)
    val choice=places.find { it.id==selected } ?: matches.singleOrNull()
    Button(enabled=query.isNotBlank() && !busy,modifier=Modifier.fillMaxWidth(),onClick={
        if(choice!=null) onPick(choice)
        else if(matches.size>1){expanded=true;status="Choose the matching place."}
        else if(destination) {
            val token=generation
            busy=true
            scope.launch {
                try { val answer=model.describePlace(query,places)
                    if(token==generation){suggestions=listOfNotNull(answer);status=if(answer==null)"No match. Choose a saved place." else "Confirm the suggested place."}
                } catch(e:CancellationException){throw e}
                catch(e:Exception){if(token==generation)status="Search unavailable. Choose a saved place."}
                finally { if(token==generation)busy=false }
            }
        } else {expanded=true;status="Choose your current place from this map."}
    }){Text(when { busy -> "Finding place…"; choice!=null && destination -> "Navigate to ${choice.displayName}"; choice!=null -> "Continue from ${choice.displayName}"; else -> "Find place" })}
}
