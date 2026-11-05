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
    // Console · graphite, mapped to Android Color. Warm black is deliberately not used.
    val Paper = Color(0xFF171A21)
    val Paper2 = Color(0xFF1E222A)
    val Paper3 = Color(0xFF11141A)
    val Ink = Color(0xFFECEFF4)
    val Ink2 = Color(0xFFC7CCD5)
    val Muted = Color(0xFFA7ADB8)
    val Rule = Color(0xFF303541)
    val Rule2 = Color(0xFF464D5D)
    val Amber = Color(0xFFE0A72E)
    val AmberInk = Color(0xFF211A08)
    val AmberSoft = Color(0xFF302A18)
    val Cool = Color(0xFF6AA9DC)
    val CoolSoft = Color(0xFF182B38)
    val Ok = Color(0xFF58D09B)
    val OkSoft = Color(0xFF182B24)
    val Bad = Color(0xFFE06D6A)
    val BadSoft = Color(0xFF341F22)

    // Compatibility aliases for the current UI.
    val Ground = Paper
    val Surface = Paper3
    val Raised = Paper2
    val Border = Rule
    val Control = Rule2
    val Text = Ink
    val Accent = Cool
    val Positive = Ok
    val Negative = Bad
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
    primary = NetBandColors.Amber,
    onPrimary = NetBandColors.AmberInk,
    primaryContainer = NetBandColors.AmberSoft,
    onPrimaryContainer = NetBandColors.Amber,
    background = NetBandColors.Paper,
    onBackground = NetBandColors.Ink,
    surface = NetBandColors.Paper3,
    onSurface = NetBandColors.Ink,
    surfaceVariant = NetBandColors.Paper2,
    onSurfaceVariant = NetBandColors.Muted,
    outline = NetBandColors.Rule,
    error = NetBandColors.Bad,
    onError = NetBandColors.AmberInk
)

@Composable
fun NetBandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetBandScheme,
        content = content
    )
}
