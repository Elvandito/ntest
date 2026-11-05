package com.elvan.netband.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.elvan.netband.R

object NetBandColors {
    val Ground = Color(0xFF171719)
    val Surface = Color(0xFF131217)
    val Raised = Color(0xFF212023)
    val Border = Color(0xFF222224)
    val Rule = Color(0xFF1B1A1F)
    val Control = Color(0xFF3A3A3E)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8A8C92)
    val Accent = Color(0xFF4A8AD6)
    val Positive = Color(0xFF44A282)
    val Negative = Color(0xFFE5566F)
    val Warning = Color(0xFFD9A441)
}

val PublicSans = FontFamily(
    Font(R.font.public_sans, FontWeight.Normal),
    Font(R.font.public_sans, FontWeight.Medium),
    Font(R.font.public_sans, FontWeight.SemiBold),
    Font(R.font.public_sans, FontWeight.Bold)
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
)

private val NetBandScheme = darkColorScheme(
    primary = NetBandColors.Accent,
    onPrimary = Color.White,
    primaryContainer = NetBandColors.Raised,
    onPrimaryContainer = NetBandColors.Text,
    background = NetBandColors.Ground,
    onBackground = NetBandColors.Text,
    surface = NetBandColors.Surface,
    onSurface = NetBandColors.Text,
    surfaceVariant = NetBandColors.Raised,
    onSurfaceVariant = NetBandColors.Muted,
    outline = NetBandColors.Border,
    error = NetBandColors.Negative,
    onError = Color.White
)

@Composable
fun NetBandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetBandScheme,
        content = content
    )
}
