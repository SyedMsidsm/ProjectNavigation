package `in`.mite.rain.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import `in`.mite.rain.R
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import `in`.mite.rain.core.DestinationSearch
import `in`.mite.rain.core.Node
import `in`.mite.rain.platform.OfflineDestinationSpeech

@Composable
internal fun TrailHome(model:RainViewModel, onRecord:()->Unit, onNavigate:()->Unit, onMap:()->Unit,
                       onLibrary:()->Unit, onCampus:()->Unit, onAssistant:()->Unit) {
    var extras by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column { Text("RAIN",style=MaterialTheme.typography.headlineMedium); Eyebrow("Indoor navigation") }
            Tag("Offline", Accent)
        }
        
        HomeAction("↗","Record a trail","Walk, mark places and save a route.",onRecord)
        HomeAction("→","Navigate","Choose your current place and destination.",onNavigate)
        HomeAction("⌖","View floor map",model.currentFloor?.name ?: "Current map",onMap)
        HomeAction("≡","Saved maps","Choose, rename or delete your recordings.",onLibrary)
        HorizontalDivider()
        TextButton(onClick={extras=!extras}) { Text(if(extras) "Close settings" else "Settings") }
        if(extras) {
        HomeAction("+","Campus tools","College plans, route comparison, sign scanning and AI place collection.",onCampus)
        OutlinedButton(onClick=onAssistant,modifier=Modifier.fillMaxWidth()){Text("Local assistant")}
        Text(model.assistantStatus,style=MaterialTheme.typography.bodySmall,color=Muted)
        }


    }
}

@Composable private fun HomeAction(number:String,title:String,description:String,action:()->Unit){
    Surface(onClick=action,shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Box(Modifier.size(42.dp).background(Accent,RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                Text(number,color=CanvasColor,style=MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(title,style=MaterialTheme.typography.titleLarge);Text(description,color=Muted,style=MaterialTheme.typography.bodyMedium)
            }
            Text("›",fontSize=26.sp)
        }
    }
}

@Composable internal fun TrailRecordScreen(model:RainViewModel,onNavigate:()->Unit,onBack:()->Unit){
    var start by rememberSaveable{mutableStateOf("")}
    var offerSign by rememberSaveable { mutableStateOf(!model.recording) }
    if(offerSign && !model.recording) SignOffer(onUse={start=it;offerSign=false},onClose={offerSign=false})
    var startFloor by rememberSaveable{mutableStateOf("0")}
    var name by rememberSaveable{mutableStateOf("")}
    var aliases by rememberSaveable{mutableStateOf("")}
    var targetFloor by rememberSaveable{mutableStateOf("1")}
    var method by rememberSaveable{mutableStateOf("stairs")}
    var backAtStart by rememberSaveable { mutableStateOf(false) }
    var mapName by rememberSaveable{mutableStateOf("")}
    var mode by rememberSaveable{mutableStateOf("")}
    var discard by rememberSaveable{mutableStateOf(false)}
    var advanced by rememberSaveable{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ model.startRecording(start,startFloor.toIntOrNull() ?: 0) }
    val context=LocalContext.current
    fun startWalk(){
        if(android.os.Build.VERSION.SDK_INT>=29 && ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        else model.startRecording(start,startFloor.toIntOrNull() ?: 0)
    }
    fun back(){if(model.recording)discard=true else onBack()}
    BackHandler{back()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick={back()}){Text("← Home")}
        Text("Record a trail",style=MaterialTheme.typography.headlineLarge)
        if(!model.recording && model.lastSavedTrailId != null) {
            Notice("Map saved", "Your places and trail are saved on this phone.")
            Button(onClick={model.lastSavedTrailId?.let { model.useTrail(it,onNavigate) }},enabled=!model.mapBusy,modifier=Modifier.fillMaxWidth()) { Text("Navigate on this map") }
            OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()) { Text("Back to home") }
        } else if(!model.recording){
            Text("Where does your trail start?",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(start,{start=it},label={Text("Starting place")},singleLine=true,modifier=Modifier.fillMaxWidth())

            TextButton(onClick={offerSign=true}) { Text("Scan nearby sign") }
            if(advanced) OutlinedTextField(startFloor,{startFloor=it},label={Text("Floor number (ground = 0)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(onClick={startWalk()},enabled=start.isNotBlank() && startFloor.toIntOrNull() in -20..100,modifier=Modifier.fillMaxWidth()){Text("Start recording")}
        }else{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Tag(if(model.recordingPaused)"Paused" else "Recording",SoftOrange);Tag("${model.trailSteps} steps");}
            if(advanced) Text(model.sensorStatus,style=MaterialTheme.typography.bodySmall,color=Muted)
            if(advanced && (model.trailSteps == 0 || model.recordingPaused)) TextButton(onClick=model::toggleStepSource) { Text(if(model.accelerometerSteps) "Use phone step detector" else "Steps not updating? Try motion detection") }
            model.trailPreview?.let{g->FloorMap(g,null,g.nodes.keys.first(),g.nodes.keys.last(),floorName="Recorded trail · floor ${model.trailFloor}",synthetic=false,selectedFloor=model.trailFloor)}
            if(mode.isEmpty() && model.recordingPaused) Button(onClick={mode="";model.resumeRecording()},enabled=!model.mapBusy,modifier=Modifier.fillMaxWidth()){Text("Continue walking")}
            else if(mode.isEmpty()) OutlinedButton(onClick=model::pauseRecording,modifier=Modifier.fillMaxWidth()){Text("Pause recording")}
            if(mode.isEmpty()) Button(onClick={model.pauseRecording();name="";aliases="";mode="mark"},modifier=Modifier.fillMaxWidth()){Text("Mark this place")}
            if(mode.isEmpty()) TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Hide more options" else "More recording options") }
            if(advanced && mode.isEmpty()) {
                OutlinedButton(onClick={model.pauseRecording();mode="join"},modifier=Modifier.fillMaxWidth()){Text("Connect to an existing place")}
                OutlinedButton(onClick={model.pauseRecording();targetFloor=(model.trailFloor+1).toString();mode="floor"},modifier=Modifier.fillMaxWidth()){Text("Change floor")}
            }
            if(mode.isEmpty()) OutlinedButton(onClick={model.pauseRecording();mode="save";backAtStart=false},modifier=Modifier.fillMaxWidth()){Text("Stop and save")}
            if(mode.isNotEmpty()) TextButton(onClick={mode=""}) {Text("Back to recording")}
            when(mode){
                "mark"->{
                    Text("Stay still while naming this place. After saving, tap Continue walking without changing your position or heading.")
                    OutlinedTextField(name,{name=it},label={Text("Place name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    if(advanced) OutlinedTextField(aliases,{aliases=it},label={Text("Aliases, separated by commas")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.markTrail(name,aliases);mode=""},enabled=name.isNotBlank()){Text("Save place")}
                }
                "join"->{
                    Text("Only join a place when you are physically back there. Distances remain the recorded walking estimates.")
                    model.trailPreview?.nodes?.values?.filter{it.roomName.isNotBlank() && it.floor==model.trailFloor}?.forEach{n->
                        OutlinedButton(onClick={model.joinTrail(n.id)}){Text(if(n.id=="T1")"Finish at start · ${n.displayName}" else "Join ${n.displayName}")}
                    }
                }
                "floor"->{
                    Text("Pause at the stairs, lift or escalator. After reaching the new floor, enter it and confirm. Keep your heading consistent.")
                    OutlinedTextField(targetFloor,{targetFloor=it},label={Text("New floor number")},singleLine=true)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("stairs","lift","escalator").forEach{m->FilterChip(selected=method==m,onClick={method=m},label={Text(m)})}}
                    Button(onClick={targetFloor.toIntOrNull()?.let{model.changeTrailFloor(it,method)}},enabled=targetFloor.toIntOrNull()!=null){Text("Confirm new floor")}
                }
                "save"->{
                    OutlinedTextField(mapName,{mapName=it},label={Text("Map name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=backAtStart,onCheckedChange={backAtStart=it});Text("Back at the starting place") }
                    Button(onClick={model.saveRecording(mapName,backAtStart)},enabled=mapName.isNotBlank() && !model.mapBusy){Text(if(model.mapBusy)"Saving…" else "Save recorded map")}
                }
            }
        }
        model.trailMessage?.let{Notice("Recording",it)}
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Discard this recording?")},text={Text("This unsaved trail will be lost. Saved maps are unaffected.")},
        confirmButton={TextButton(onClick={model.discardRecording();discard=false;onBack()}){Text("Discard")}},dismissButton={TextButton(onClick={discard=false}){Text("Keep recording")}})
}

@Composable internal fun TrailLibraryScreen(model:RainViewModel,onUse:()->Unit,onBack:()->Unit){
    var renameId by rememberSaveable{mutableStateOf<String?>(null)};var name by rememberSaveable{mutableStateOf("")}
    var deleteId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){model.refreshTrails()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=onBack){Text("← Home")};Text("Saved maps",style=MaterialTheme.typography.headlineLarge)
        Text("Active: ${model.currentFloor?.name}",color=Muted)
        if(model.savedTrails.isEmpty())Notice("No recordings yet","Choose Record a trail to create your first map. Campus maps are under Campus tools.")
        model.savedTrails.forEach{entry->
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)){
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(entry.name,style=MaterialTheme.typography.titleLarge);Text("${entry.places} named places · recorded route",color=Muted)
                    Button(onClick={model.useTrail(entry.id,onUse)},enabled=!model.mapBusy){Text("Use this map")}
                    Row{TextButton(onClick={renameId=entry.id;name=entry.name}){Text("Rename")};TextButton(onClick={deleteId=entry.id}){Text("Delete")}}
                }
            }
        }
        model.status?.let{Text(it)}
    }
    if(renameId!=null)AlertDialog(onDismissRequest={renameId=null},title={Text("Rename map")},text={OutlinedTextField(name,{name=it},singleLine=true)},confirmButton={TextButton(onClick={model.renameTrail(renameId!!,name);renameId=null},enabled=name.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick={renameId=null}){Text("Cancel")}})
    if(deleteId!=null)AlertDialog(onDismissRequest={deleteId=null},title={Text("Delete saved recording?")},text={Text("This removes the saved recording from the library. An already active map remains available until you switch maps.")},confirmButton={TextButton(onClick={model.deleteTrail(deleteId!!);deleteId=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleteId=null}){Text("Cancel")}})
}

@Composable internal fun DestinationSearchPanel(model:RainViewModel){
    var query by rememberSaveable{mutableStateOf("")};var speechStatus by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current;val speech=remember{OfflineDestinationSpeech(context)}
    fun listen(){speech.listen({query=it;speechStatus="Recognised: $it"},{speechStatus=it})}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)listen() else speechStatus="Microphone permission declined. Typed search remains available."}
    DisposableEffect(Unit){onDispose{speech.close()}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(query,{query=it.take(500)},label={Text("Type a place or describe what you need")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)listen() else permission.launch(Manifest.permission.RECORD_AUDIO)}){Text("Speak destination")}
            Button(onClick={model.askDestination(query)},enabled=query.isNotBlank() && !model.assistantBusy){Text(if(model.assistantBusy)"Thinking…" else "Find place")}
        }
        speechStatus?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        if(query.isNotBlank())DestinationSearch.matches(query,model.graph!!.nodes.values).take(8).forEach{n->
            TextButton(onClick={model.selectDestination(n.id)}){Text("${n.displayName} · floor ${n.floor}")}
        }
        if(query.isNotBlank()) Text(model.assistantStatus,style=MaterialTheme.typography.bodySmall,color=Muted)
        model.suggestedDestination?.let{n->
            Notice("Suggested destination","${n.displayName} · floor ${n.floor}")
            Button(onClick=model::confirmSuggestedDestination){Text("Use this destination")}
        }
    }
}

@Composable internal fun AssistantScreen(model:RainViewModel,onBack:()->Unit){
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(model::importAssistantModel)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Text("← Home")};Text("Local assistant",style=MaterialTheme.typography.headlineLarge)
        Text("Describe a destination, then confirm its name. The assistant only suggests places already on your active map.")
        Text("A compatible .litertlm language-model file is required for AI requests. Import it once from this phone. Name and alias search works without a model.",color=Muted)
        OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!model.assistantBusy){Text("Import local model")}
        DestinationSearchPanel(model)
        Text("Model loading runs on the phone and may take time. No audio, request or map is sent to a cloud model.",style=MaterialTheme.typography.bodySmall,color=Muted)
    }
}
