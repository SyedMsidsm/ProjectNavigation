package `in`.mite.rain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PanelColor = Color(0xFF10151C)
internal val Ink = Color(0xFFF3F4F6)
internal val Muted = Color(0xFFA2ABB6)
internal val CanvasColor = Color(0xFF080A0D)
internal val PaleGreen = Color(0xFF1A2029)
internal val Accent = Color(0xFF24D8BA)
internal val SoftOrange = Color(0xFF302119)
internal val ErrorInk = Color(0xFFFFA077)

@Composable
fun RainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = CanvasColor, primaryContainer = PaleGreen, onPrimaryContainer = Ink,
            secondary = Accent, secondaryContainer = PaleGreen, onSecondaryContainer = Ink,
            background = CanvasColor, surface = PanelColor,
            onBackground = Ink, onSurface = Ink, onSurfaceVariant = Muted,
            surfaceVariant = PaleGreen, outline = Color(0xFF38414D), error = ErrorInk,
        ),
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        ),
        content = content,
    )
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun Tag(text: String, color: Color = PaleGreen, ink: Color = if (color == Accent) CanvasColor else Ink) {
    Text(text, Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        color = ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
}

@Composable
internal fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
internal fun Notice(title: String, message: String, warning: Boolean = false) {
    Surface(color = if (warning) SoftOrange else PaleGreen, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}
