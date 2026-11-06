package `in`.mite.rain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.mite.rain.core.Graph
import `in`.mite.rain.core.RecognitionResult
import `in`.mite.rain.core.PromptMode
import java.util.Locale

@Composable
internal fun SettingsDialog(lambda: Double, stepLength: Double, voice: Boolean, haptics: Boolean, promptMode: PromptMode,
                            onSave: (Double, Double, Boolean, Boolean, PromptMode) -> Unit, onClose: () -> Unit) {
    var localLambda by remember { mutableStateOf(lambda.toFloat()) }
    var localStep by remember { mutableStateOf(stepLength.toFloat()) }
    var localVoice by remember { mutableStateOf(voice) }
    var localHaptics by remember { mutableStateOf(haptics) }
    var localMode by remember { mutableStateOf(promptMode) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Tune your walkthrough") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Risk trade-off · λ = ${localLambda.toInt()} m", style = MaterialTheme.typography.titleMedium)
                Text("Extra walking the planner can trade for lower heuristic navigation risk. Higher values may choose clearer corridors.")
                Slider(localLambda, { localLambda = it }, valueRange = 0f..160f, steps = 15)
                Text("Step length · ${String.format(Locale.US, "%.2f", localStep)} m", style = MaterialTheme.typography.titleMedium)
                Text("Calibrate by walking a measured distance and dividing metres by your step count. Keep the phone orientation steady during a walk.")
                Slider(localStep, { localStep = it }, valueRange = .3f..1.2f)
                Row { Text("Spoken guidance", Modifier.weight(1f)); Switch(localVoice, { localVoice = it }) }
                Text("Uses an installed offline English voice. New instructions and warnings are spoken; you can also repeat the current instruction.", style = MaterialTheme.typography.bodySmall)
                Row { Text("Vibration cues", Modifier.weight(1f)); Switch(localHaptics, { localHaptics = it }) }
                Text("One pulse for a new instruction; two pulses for a position check. These are supporting cues, not obstacle detection.", style = MaterialTheme.typography.bodySmall)
                Text("Scan prompt policy", style = MaterialTheme.typography.titleMedium)
                PromptMode.entries.forEach { mode ->
                    TextButton(onClick = { localMode = mode }) { Text((if (mode == localMode) "✓ " else "") + mode.displayName()) }
                }
                Text("Policies share a 15 m repeat limit and require a readable nearby landmark. Wrong-turn recovery remains active in every mode.", style = MaterialTheme.typography.bodySmall)
                Text("v0 risk model: hand-set logistic weights. Scan benefit is an assumed demo parameter. No learned or fused-recognition claims.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { Button(onClick = { onSave(localLambda.toDouble(), localStep.toDouble(), localVoice, localHaptics, localMode) }) { Text("Apply settings") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

internal fun PromptMode.displayName() = when (this) {
    PromptMode.COST_SENSITIVE -> "Risk-sensitive checks"
    PromptMode.ALWAYS -> "Check eligible landmarks"
    PromptMode.FIXED_INTERVAL -> "Check after each 20 m interval"
    PromptMode.NEVER -> "No routine checks"
}

@Composable
internal fun CalibrationDialog(model: RainViewModel, onClose: () -> Unit) {
    var distance by remember { mutableStateOf("10") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Calibrate your walking") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mark a measured straight distance of 5–100 m. Stand at its start, tap Start counting, walk to the endpoint and stop. Keep this screen open and keep the phone pose steady.")
            OutlinedTextField(distance, { distance = it }, label = { Text("Measured distance in metres") }, singleLine = true,
                enabled = !model.calibrationRecording, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            Text("${model.calibrationSteps} detected steps", style = MaterialTheme.typography.headlineMedium)
            Text(model.calibrationMessage)
            if (model.calibrationTurns.isNotEmpty()) Text("Recent relative turns: " + model.calibrationTurns.joinToString { "${it.toInt()}°" } +
                ". If you turned while walking, repeat on a straight course.")
            model.calibrationResult?.let { result -> Text(String.format(Locale.US, "%.1f m / %d steps = %.3f m per step", result.distanceM, result.steps, result.stepLengthM), style = MaterialTheme.typography.titleMedium) }
            Text("Saved: ${model.calibrationSummary}", style = MaterialTheme.typography.bodySmall)
            if (model.calibrationRecording) Button(onClick = model::stopCalibration) { Text("Stop counting") }
            else Button(onClick = { model.beginCalibration(distance.toDoubleOrNull() ?: Double.NaN) }) { Text("Start counting") }
            if (model.calibrationResult != null) Button(onClick = model::saveCalibration) { Text("Save measured step length") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close calibration") } })
}

@Composable
internal fun FloorDialog(model: RainViewModel, onImport: () -> Unit, onClose: () -> Unit) {
    var confirmRestore by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    if (showPlan) GroundFloorPlanDialog { showPlan = false }
    AlertDialog(onDismissRequest = { if (!model.mapBusy) onClose() }, title = { Text("Floor maps") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Active: ${model.currentFloor?.name ?: "Unavailable"}")
            Text("Import a RAIN floor JSON file containing a name, source, rooms CSV and edges CSV. The entire graph is checked before activation. Field-check user-provided measurements before navigating.")
            model.mapMessage?.let { Text(it) }
            if (model.mapBusy) Text("Checking or saving the map…")
            model.pendingFloor?.floor?.let { floor ->
                Text("Review: ${floor.name}", style = MaterialTheme.typography.titleMedium)
                Text("${floor.graph.nodes.size} locations · ${floor.graph.edges.size} segments\n" + when (floor.source) {
                    "synthetic_floor" -> "Synthetic data"
                    "plan_derived_floor" -> "Drawing-derived draft. Check the marked plan for doorway evidence and pending openings. Route distances and physical access need field checks. Sign conditions are unknown; automatic scan prompts are disabled for these unverified signs."
                    else -> "User-provided measurements · field verification required"
                })
                Button(onClick = model::activateFloor, enabled = !model.mapBusy) { Text("Activate reviewed map") }
                TextButton(onClick = model::discardFloorPreview, enabled = !model.mapBusy) { Text("Discard preview") }
            }
            Button(onClick = model::previewGroundFloor, enabled = !model.mapBusy) { Text("College ground floor") }
            TextButton(onClick = { showPlan = true }) { Text("View supplied plan") }
            Button(onClick = onImport, enabled = !model.mapBusy) { Text("Choose floor file") }
            TextButton(onClick = { confirmRestore = true }, enabled = !model.mapBusy) { Text("Restore sample map") }
            if (confirmRestore) {
                Text("This switches the active map to the bundled sample. Your original imported file remains in its source location.")
                Button(onClick = { confirmRestore = false; model.restoreDemoFloor() }, enabled = !model.mapBusy) { Text("Confirm sample map") }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose, enabled = !model.mapBusy) { Text("Close maps") } })
}

@Composable
internal fun SampleTextDialog(graph: Graph, suggestedNode: String, onMatch: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(graph.nodes[suggestedNode]?.let { it.signText.ifBlank { it.displayName } } ?: "Room 204") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Try sign recognition") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Tag("DEMO TEXT INPUT", SoftOrange)
                Text("This sends typed text through the same room matcher as camera OCR. It does not simulate a camera result.")
                OutlinedTextField(text, { text = it }, label = { Text("Text from a sign") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Try: " + graph.nodes.values.filter { it.isRecognizable }.take(3).joinToString(" · ") { it.displayName }, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { Button(onClick = { onMatch(text) }, enabled = text.isNotBlank()) { Text("Match text") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
internal fun RecognitionDialog(result: RecognitionResult, navigating: Boolean, onAccept: (String) -> Unit, onClose: () -> Unit) {
    val top = result.topCandidates.firstOrNull()
    AlertDialog(onDismissRequest = onClose, title = { Text(if (result.accepted) "Sign matched" else "Check the room match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(result.reason, style = MaterialTheme.typography.bodyMedium)
                result.topCandidates.take(3).forEachIndexed { index, candidate ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(candidate.roomName, style = MaterialTheme.typography.titleMedium)
                        Text("Text match score: ${decimal(candidate.score)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        if (!result.accepted || index > 0) TextButton(onClick = { onAccept(candidate.nodeId) }) { Text("Confirm this location manually") }
                    }
                }
                if (top == null) Text("Try a clearer sign or select a starting point manually.")
                Text("A text match is a location suggestion, not independent ground truth.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = {
            if (result.accepted && top != null) Button(onClick = { onAccept(top.nodeId) }) {
                Text(if (navigating) "Anchor & replan" else "Use as starting point")
            } else TextButton(onClick = onClose) { Text("Try again") }
        },
        dismissButton = { if (result.accepted) TextButton(onClick = onClose) { Text("Cancel") } })
}
