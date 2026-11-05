package com.elvan.netband.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke

private val CardShape = RoundedCornerShape(10.dp)
private val ControlShape = RoundedCornerShape(6.dp)

@Composable
fun NetBandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val stroke = 1.6.dp.toPx()
        val instrument = NetBandColors.Cool
        val ink = NetBandColors.Ink
        val mid = size.height * 0.50f
        drawLine(ink, androidx.compose.ui.geometry.Offset(2.dp.toPx(), mid), androidx.compose.ui.geometry.Offset(size.width * 0.28f, mid), stroke, StrokeCap.Round)
        drawLine(instrument, androidx.compose.ui.geometry.Offset(size.width * 0.28f, mid), androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(instrument, androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.74f), stroke, StrokeCap.Round)
        drawLine(instrument, androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.74f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, mid), stroke, StrokeCap.Round)
        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * 0.72f, mid), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), mid), stroke, StrokeCap.Round)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(NetBandColors.Raised)
            .border(1.dp, NetBandColors.Rule, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = NetBandColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
        Spacer(Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun Readout(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color = NetBandColors.Cool) {
    Column(modifier) {
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text(value, color = accent, fontSize = 44.sp, fontWeight = FontWeight.Normal, fontFamily = IbmPlexMono, lineHeight = 46.sp)
            if (unit.isNotBlank()) Text(unit, color = NetBandColors.Muted, fontSize = 13.sp, fontFamily = PublicSans, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
        Text(label, color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(value, color = NetBandColors.Ink, fontWeight = FontWeight.Medium, fontSize = 19.sp, fontFamily = IbmPlexMono)
            if (unit.isNotEmpty()) Text(unit, color = NetBandColors.Muted, fontSize = 10.sp, fontFamily = PublicSans, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(18.dp).background(NetBandColors.Rule2))
        Spacer(Modifier.width(10.dp))
        Text(text, color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = PublicSans, maxLines = 2)
    }
}

@Composable
fun MonoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (destructive) NetBandColors.Bad else NetBandColors.Ink,
            disabledContentColor = NetBandColors.Muted
        ),
        border = BorderStroke(1.dp, if (destructive) NetBandColors.Bad else NetBandColors.Rule2)
    ) {
        Text(text, fontFamily = PublicSans, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = NetBandColors.Amber,
            contentColor = NetBandColors.AmberInk,
            disabledContainerColor = NetBandColors.Raised,
            disabledContentColor = NetBandColors.Muted
        )
    ) {
        Text(text, fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun OutlinedIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(ControlShape)
            .border(1.dp, NetBandColors.Rule2, ControlShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

fun formatConnection(iface: String?, localIp: String?, gatewayIp: String?): String =
    "${iface ?: "—"} · local ${localIp ?: "—"} · gateway ${gatewayIp ?: "—"}"

fun shortMac(mac: String): String = if (mac.startsWith("manual:")) "MANUAL" else mac

fun deviceStatus(device: com.elvan.netband.Device): String = when {
    device.blocked -> "Blocked · ${relativeDirection(device.direction)}"
    device.limitRate != null -> "Limited ${device.limitRate} · ${relativeDirection(device.direction)}"
    device.online -> "Online"
    else -> "Offline"
}

fun relativeDirection(direction: com.elvan.netband.Direction): String = when (direction) {
    com.elvan.netband.Direction.UP -> "upload"
    com.elvan.netband.Direction.DOWN -> "download"
    com.elvan.netband.Direction.BOTH -> "upload + download"
}
