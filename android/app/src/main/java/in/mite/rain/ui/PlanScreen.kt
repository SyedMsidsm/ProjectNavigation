package `in`.mite.rain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.mite.rain.core.Graph
import `in`.mite.rain.core.Node
import `in`.mite.rain.core.PlannerKind
import `in`.mite.rain.core.Route

@Composable
internal fun PlanScreen(
    graph: Graph, startId: String, destinationId: String, kind: PlannerKind,
    routes: Map<PlannerKind, Route?>, onStart: (String) -> Unit, onDestination: (String) -> Unit,
    onKind: (PlannerKind) -> Unit, onSettings: () -> Unit, onScan: () -> Unit,
    onDemoSign: () -> Unit, onNavigate: () -> Unit, onExport: () -> Unit,
    hasSession: Boolean, status: String?, floorName: String, synthetic: Boolean,
    onCalibration: () -> Unit, onMaps: () -> Unit, ready: Boolean, planDerived: Boolean = false, onCollect: () -> Unit = {},
    searchPanel: @Composable () -> Unit = {},
) {
    var showOptions by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp, 20.dp, 22.dp, 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Navigate", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onSettings) { Text("Settings") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (planDerived) Text("PLAN DRAFT · Doorways and route distances are estimated. Review the passages before a walking trial.", color = Muted)
                Text("Where do you want to go?", style = MaterialTheme.typography.headlineMedium)

                Text("Map: $floorName", style = MaterialTheme.typography.titleMedium)
            }
        }
        if(showOptions) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCalibration, enabled = ready, modifier = Modifier.weight(1f)) { Text("Calibrate walking") }
                OutlinedButton(onClick = onMaps, enabled = ready, modifier = Modifier.weight(1f)) { Text("Floor maps") }
            }

        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = PanelColor) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {


                    NodePicker("Current place", graph.nodes.values.toList(), startId, onStart)
                    searchPanel()
                    NodePicker("Or choose a saved place", graph.nodes.values.filter { it.roomName.isNotBlank() }, destinationId, onDestination)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan a sign") }

                    }
                }
            }
        }
        if (showOptions && status != null) item { Notice("Session update", status) }
        item {
            Button(onClick = onNavigate, enabled = ready && routes[kind] != null && startId != destinationId,
                modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (startId == destinationId) "Choose a different destination" else "Start navigation  →")
            }
        }
        item { FloorMap(graph, routes[kind], startId, destinationId, floorName = floorName, synthetic = synthetic) }
        item {
            if(showOptions) Text("Selected route: ${kind.title()}", style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={showOptions=!showOptions}) { Text(if(showOptions) "Hide route options" else "More options") }
        }
        if(showOptions) {
            item { Text("Risk scores are experimental, not measured error probabilities.", color=Muted) }
            for (planner in listOf(PlannerKind.RISK, PlannerKind.SHORTEST, PlannerKind.SIMPLEST)) {
                item { RouteChoice(planner, routes[planner], kind == planner) { onKind(planner) } }
            }
        }
        if (hasSession && showOptions) item { OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export last session · JSONL") } }
        if(showOptions) item {
            Text((if (synthetic) "PRESENTATION DEMO · Synthetic floor data" else "USER-PROVIDED FLOOR · Verify measurements before walking") +
                "\nCamera text recognition is live; route risk is heuristic.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
internal fun NodePicker(label: String, nodes: List<Node>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = nodes.firstOrNull { it.id == selectedId }
    Column {
        Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded; query = "" },
            shape = RoundedCornerShape(14.dp), color = CanvasColor) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(selected?.displayName ?: selectedId, style = MaterialTheme.typography.titleMedium)
                }
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search rooms or aliases") }, shape = RoundedCornerShape(14.dp))
            val matches = (if(query.isBlank()) nodes else `in`.mite.rain.core.DestinationSearch.matches(query,nodes)).take(10)
            if (matches.isEmpty()) Text("No matching rooms", Modifier.padding(12.dp), color = Muted)
            matches.forEach { node ->
                DropdownMenuItem(text = { Text("${node.displayName} · floor ${node.floor}") }, onClick = { onSelect(node.id); expanded = false; query = "" })
            }
        }
    }
}
