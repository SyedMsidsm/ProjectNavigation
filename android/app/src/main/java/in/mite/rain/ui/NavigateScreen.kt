package `in`.mite.rain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import `in`.mite.rain.core.Graph
import `in`.mite.rain.core.NavigationState
import `in`.mite.rain.core.PlannerKind
import `in`.mite.rain.core.Route

@Composable
internal fun NavigateScreen(
    graph: Graph, route: Route, kind: PlannerKind, state: NavigationState, steps: Int,
    liveMotion: Boolean, sensorStatus: String, onLiveMotion: (Boolean) -> Unit,
    onDemoSteps: () -> Unit, onDemoTurn: (Double) -> Unit, onScan: () -> Unit,
    onSampleSign: () -> Unit, onFinish: () -> Unit, onExit: () -> Unit, onExport: () -> Unit,
    onAr: () -> Unit,
    floorName: String, synthetic: Boolean, feedbackStatus: String, promptPolicy: String,
    onConfirmPosition: (String) -> Unit = {}, onConfirmFloor: () -> Unit = {}, stepLengthM: Double = .7,
    voiceOn: Boolean, onVoice: () -> Unit, headingDegrees: Double, headingAligned: Boolean, onAlignHeading: () -> Unit,
    onRepeat: () -> Unit, onObserver: (String) -> Unit, observerStatus: String?, planDerived: Boolean = false,
) {
    var showTools by remember { mutableStateOf(false) }
    var showObserver by remember { mutableStateOf(false) }
    var showPosition by remember { mutableStateOf(false) }
    var positionId by remember { mutableStateOf(state.currentNodeId) }
    var confirmArrival by remember { mutableStateOf(false) }
    val destinationId = route.nodes.last()
    val routeProgress = route.totalLengthM - state.remainingDistanceM
    val progress = if (route.totalLengthM <= 0) 1f else (routeProgress / route.totalLengthM).toFloat().coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
        if (planDerived) item { Text("PLAN DRAFT · Estimated distances and doorway connections. This route needs an on-site check.", color = Muted) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit) { Text("← End walk") }
                if (!liveMotion) Tag("Demo motion", SoftOrange)
                TextButton(onClick=onVoice) { Text(if(voiceOn) "Sound on" else "Sound off") }
            }
        }
        item {
            Surface(color = PaleGreen, contentColor = Ink, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("To ${graph.nodes.getValue(destinationId).displayName}", style = MaterialTheme.typography.labelLarge, color = Muted)
                    val currentArc = route.arcs.getOrNull(state.currentArcIndex)
                    val nextTurn = route.transitions.getOrNull(state.currentArcIndex + 1)
                    val arrow = when {
                        state.offRouteReason != null -> "?"
                        state.floorChangePending -> "↕"
                        state.arrived -> "✓"
                        currentArc != null && currentArc.lengthM-state.offsetM <= 3 && nextTurn != null && kotlin.math.abs(nextTurn.angleDeg)>=60 -> if(kotlin.math.abs(nextTurn.angleDeg)>135)"↶" else if(nextTurn.angleDeg>0)"→" else "←"
                        else -> "↑"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(arrow,fontSize=42.sp,color=Accent)
                        Text(if (state.arrived) "Route distance reached" else state.instruction,
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                    Text(if (state.arrived) "Read the room sign to confirm where you are. Distance alone cannot verify arrival."
                        else "Estimated near ${graph.nodes[state.currentNodeId]?.displayName ?: state.currentNodeId}",
                        color = Color(0xFFDCE9DF), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Color(0xFF3C5C52))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${metres(state.remainingDistanceM)} remaining", style = MaterialTheme.typography.bodySmall)
                        Text("$steps steps", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { FloorMap(graph, route, route.nodes.first(), destinationId, routeProgress, navigating = true, floorName = floorName, synthetic = synthetic,selectedFloor=graph.nodes.getValue(state.currentNodeId).floor, headingDegrees=if(liveMotion && headingAligned) headingDegrees else route.arcs.getOrNull(state.currentArcIndex)?.bearingDeg) }
        if (liveMotion && !state.arrived) item {
            if (!headingAligned) Text("Face along the highlighted corridor ahead, then align the arrow. This is relative phone direction, not a compass.", style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = onAlignHeading) { Text(if (headingAligned) "Realign direction" else "I'm facing the route · Align arrow") }
        }
        if (state.offRouteReason != null) item {
            Notice("Let's check your position", "${state.offRouteReason}. Scan a known sign to anchor your location and calculate a fresh route.", warning = true)
        } else if (state.promptReason != null) item {
            Notice("A good moment to read a sign", "${state.promptReason}. A known sign can reset the motion estimate.", warning = true)
        }
        if(showTools || state.offRouteReason != null || state.promptReason != null || state.floorChangePending) item {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp)) {
                Text("Scan sign & check position")
            }
            TextButton(onClick = onRepeat) { Text("Repeat guidance") }
            Text(feedbackStatus, style = MaterialTheme.typography.bodySmall, color = Muted)

            if(state.confidence < .65 && !state.arrived) Text("Are you at ${graph.nodes.getValue(state.currentNodeId).displayName}? Confirm only if you recognise it.")
            OutlinedButton(onClick={positionId=state.currentNodeId;showPosition=true},modifier=Modifier.fillMaxWidth()){Text("Confirm where I am")}
            if(state.floorChangePending)Button(onClick=onConfirmFloor,modifier=Modifier.fillMaxWidth()){Text("Change floor — I have reached the new floor")}
        }
        item { TextButton(onClick={showTools=!showTools}) { Text(if(showTools) "Hide extra tools" else "More options") } }
        if(showTools) {
        item {
            OutlinedButton(onClick = onAr, enabled = !state.arrived && state.offRouteReason == null && route.arcs.none { it.transition.isNotBlank() },
                modifier = Modifier.fillMaxWidth()) { Text("AR walking navigation") }
            Text(if(route.arcs.any{it.transition.isNotBlank()}) "Use map guidance for multi-floor routes. AR currently supports single-floor routes." else "Confirm your start and align once. Arrows and journey progress update as you walk.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        item {
            Surface(color = PanelColor, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use phone motion", style = MaterialTheme.typography.titleMedium)
                            Text(if (liveMotion) sensorStatus else "Presentation controls are active", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Switch(checked = liveMotion, onCheckedChange = onLiveMotion)
                    }
                    if (!liveMotion) {
                        Text("DEMO INPUTS · These simulate movement", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAD6429))
                        Button(onClick = onDemoSteps, enabled = state.offRouteReason == null && route.arcs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Advance 5 simulated steps") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onDemoTurn(-90.0) }, modifier = Modifier.weight(1f), enabled = !state.arrived && state.offRouteReason == null) { Text("Turn left") }
                            OutlinedButton(onClick = { onDemoTurn(90.0) }, modifier = Modifier.weight(1f), enabled = !state.arrived && state.offRouteReason == null) { Text("Turn right") }
                        }
                        TextButton(onClick = onSampleSign, modifier = Modifier.fillMaxWidth()) { Text("Try a sample sign anchor") }
                    }
                    Text("Motion is an approximate route estimate. Sign scans provide location anchors. Drift estimate: ${metres(state.driftM)}.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        item {
            Surface(color = PanelColor, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("YOUR PATH")
                    route.nodes.forEachIndexed { index, id ->
                        val isCurrent = id == state.currentNodeId
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (isCurrent) "●" else "○", color = if (isCurrent) Ink else Muted)
                            Text(graph.nodes.getValue(id).displayName, style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) Ink else Muted, modifier = Modifier.weight(1f))
                            if (id in route.scheduledPrompts) Text("SIGN CHECK", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAD6429))
                        }
                    }
                }
            }
        }
        }
        if (state.arrived) item {
            Button(onClick = {confirmArrival=true}, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("I'm here — confirm arrival") }
        }
        if(showTools) {
        item { OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export session · JSONL") } }
        item {
            Text("Prompt policy: $promptPolicy", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showObserver = !showObserver }) { Text("Observer tools") }
            if (showObserver) {
                Text("A separate observer can mark what actually happened. Location IDs remain app estimates. These tags do not automatically verify arrival.", style = MaterialTheme.typography.bodySmall)
                listOf("observed_wrong_turn" to "Mark observed wrong turn", "checkpoint" to "Mark checkpoint",
                    "observed_correct_arrival" to "Mark observed correct arrival", "observed_incorrect_arrival" to "Mark observed incorrect arrival").forEach { (tag, label) ->
                    TextButton(onClick = { onObserver(tag) }) { Text(label) }
                }
                observerStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { Text("${if (synthetic) "Synthetic floor" else "User-provided floor"} · Uncalibrated risk weights\n${if (liveMotion) "Live motion events" else "Simulated motion events"} are labelled in the export. Arrival is not a measured accuracy result.",
            style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
    }
    if(showPosition)AlertDialog(onDismissRequest={showPosition=false},title={Text("Confirm your current place")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Choose only a place you recognise. This resets the position estimate and replans from there.")
            NodePicker("I am at",graph.nodes.values.filter{it.roomName.isNotBlank()},positionId,{positionId=it})
        }
    },confirmButton={TextButton(onClick={onConfirmPosition(positionId);showPosition=false},enabled=graph.nodes[positionId]?.roomName?.isNotBlank()==true){Text("Confirm this location")}},dismissButton={TextButton(onClick={showPosition=false}){Text("Cancel")}})
    if(confirmArrival)AlertDialog(onDismissRequest={confirmArrival=false},title={Text("Are you at ${graph.nodes.getValue(destinationId).displayName}?")},text={Text("Check the place or sign. The distance estimate alone does not establish arrival.")},confirmButton={TextButton(onClick={confirmArrival=false;onFinish()}){Text("Yes, I'm here")}},dismissButton={TextButton(onClick={confirmArrival=false;showPosition=true}){Text("No, check position")}})
}
