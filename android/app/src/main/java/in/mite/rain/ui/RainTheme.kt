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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF173F39)
internal val Muted = Color(0xFF677770)
internal val CanvasColor = Color(0xFFF5F5EF)
internal val PaleGreen = Color(0xFFE7EFE7)
internal val Accent = Color(0xFFF1AB69)
internal val SoftOrange = Color(0xFFFFEBD6)
internal val ErrorInk = Color(0xFF9E3D2F)

@Composable
fun RainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White, primaryContainer = PaleGreen, onPrimaryContainer = Ink,
            secondary = Color(0xFFAD6429), secondaryContainer = SoftOrange, onSecondaryContainer = Ink,
            background = CanvasColor, surface = Color.White,
            onBackground = Ink, onSurface = Ink, onSurfaceVariant = Muted,
            surfaceVariant = PaleGreen, outline = Color(0xFFC8D2C8), error = ErrorInk,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
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
internal fun Tag(text: String, color: Color = PaleGreen, ink: Color = Ink) {
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
