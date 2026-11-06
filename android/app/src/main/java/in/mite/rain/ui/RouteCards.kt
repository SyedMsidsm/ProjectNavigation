package `in`.mite.rain.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.mite.rain.core.PlannerKind
import `in`.mite.rain.core.Route
import `in`.mite.rain.core.TurnClass
import java.util.Locale

internal fun PlannerKind.title(): String = when (this) {
    PlannerKind.SHORTEST -> "Shortest path"
    PlannerKind.SIMPLEST -> "Simplest path"
    PlannerKind.RISK -> "Risk-aware path"
}

internal fun metres(value: Double) = String.format(Locale.US, "%.0f m", value)
internal fun decimal(value: Double) = String.format(Locale.US, "%.2f", value)

@Composable
internal fun RouteChoice(kind: PlannerKind, route: Route?, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = route != null, onClick = onSelect),
        shape = RoundedCornerShape(20.dp), color = if (selected) PaleGreen else PanelColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Ink else Color(0xFFE1E6DC)),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)) {
            RadioButton(selected = selected, onClick = onSelect, enabled = route != null)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(kind.title(), style = MaterialTheme.typography.titleMedium)
                    Text(route?.let { metres(it.totalLengthM) } ?: "No route", style = MaterialTheme.typography.titleMedium)
                }
                if (route != null) {
                    val turns = route.transitions.count { it.turnClass == TurnClass.TURN || it.turnClass == TurnClass.U_TURN }
                    Text("$turns ${if (turns == 1) "turn" else "turns"}  ·  ${route.scheduledPrompts.size} checks  ·  ${decimal(route.expectedErrors)} risk score",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (kind == PlannerKind.RISK && selected)
                        Text("Balances walking distance and navigation uncertainty", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
