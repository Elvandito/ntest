package com.elvan.netband.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elvan.netband.R

object NetBandColors {
    // Reference direction: true black, soft graphite cards, blue selection, green status.
    val Black = Color(0xFF000000)
    val Paper = Color(0xFF000000)
    val Paper2 = Color(0xFF1C1C1E)
    val Paper3 = Color(0xFF0B0B0C)
    val Ink = Color(0xFFFFFFFF)
    val Ink2 = Color(0xFFD1D1D6)
    val Muted = Color(0xFFA1A1A6)
    val Muted2 = Color(0xFF8E8E93)
    val Rule = Color(0xFF2C2C2E)
    val Rule2 = Color(0xFF3A3A3C)

    val Blue = Color(0xFF5AA9FF)
    val BlueFill = Color(0xFF0A84FF)
    val BlueSoft = Color(0xFF173A5C)
    val BlueInk = Color(0xFFFFFFFF)

    val Green = Color(0xFF34C759)
    val GreenSoft = Color(0xFF0E3A24)
    val GreenInk = Color(0xFFBDF5D0)

    val Amber = Color(0xFFFFB020)
    val AmberSoft = Color(0xFF3A2A10)
    val AmberInk = Color(0xFF1C1405)

    val Red = Color(0xFFFF6B6B)
    val RedSoft = Color(0xFF3A1B1E)

    // Compatibility aliases for the current UI.
    val Ground = Paper
    val Surface = Paper2
    val Raised = Paper2
    val Border = Rule
    val Control = Rule2
    val Text = Ink
    val Accent = Blue
    val Cool = Blue
    val CoolSoft = BlueSoft
    val Ok = Green
    val OkSoft = GreenSoft
    val Bad = Red
    val BadSoft = RedSoft
    val Positive = Green
    val Negative = Red
    val Warning = Amber
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
    primary = NetBandColors.Blue,
    onPrimary = NetBandColors.BlueInk,
    primaryContainer = NetBandColors.BlueSoft,
    onPrimaryContainer = NetBandColors.Blue,
    secondary = NetBandColors.Green,
    onSecondary = NetBandColors.AmberInk,
    background = NetBandColors.Paper,
    onBackground = NetBandColors.Ink,
    surface = NetBandColors.Paper2,
    onSurface = NetBandColors.Ink,
    surfaceVariant = NetBandColors.Rule,
    onSurfaceVariant = NetBandColors.Muted,
    outline = NetBandColors.Rule2,
    error = NetBandColors.Red,
    onError = NetBandColors.Ink
)

private val NetBandShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun NetBandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetBandScheme,
        shapes = NetBandShapes,
        content = content
    )
}
