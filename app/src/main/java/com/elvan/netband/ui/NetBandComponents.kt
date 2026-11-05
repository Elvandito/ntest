package com.elvan.netband.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.InterfaceStats

private val CardShape = RoundedCornerShape(6.dp)
private val ControlShape = RoundedCornerShape(4.dp)

@Composable
fun NetBandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val stroke = 1.7.dp.toPx()
        val accent = NetBandColors.Accent
        val white = NetBandColors.Text
        val yTop = size.height * 0.32f
        val yMid = size.height * 0.52f
        val yLow = size.height * 0.72f
        drawLine(white, androidx.compose.ui.geometry.Offset(2.dp.toPx(), yMid),
            androidx.compose.ui.geometry.Offset(size.width * 0.30f, yMid), stroke, StrokeCap.Round)
        drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.30f, yMid),
            androidx.compose.ui.geometry.Offset(size.width * 0.43f, yTop), stroke, StrokeCap.Round)
        drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.43f, yTop),
            androidx.compose.ui.geometry.Offset(size.width * 0.57f, yLow), stroke, StrokeCap.Round)
        drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.57f, yLow),
            androidx.compose.ui.geometry.Offset(size.width * 0.70f, yMid), stroke, StrokeCap.Round)
        drawLine(white, androidx.compose.ui.geometry.Offset(size.width * 0.70f, yMid),
            androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), yMid), stroke, StrokeCap.Round)
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
            .border(1.dp, NetBandColors.Border, CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content
    )
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = NetBandColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
        Spacer(Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = NetBandColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = IbmPlexMono)
            if (unit.isNotEmpty()) Text(unit, color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans, modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(18.dp).background(NetBandColors.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, maxLines = 2)
    }
}

@Composable
fun MonoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (destructive) NetBandColors.Negative else NetBandColors.Text,
            disabledContentColor = NetBandColors.Muted
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (destructive) NetBandColors.Negative else NetBandColors.Control)
    ) {
        Text(text, fontFamily = IbmPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = NetBandColors.Text,
            contentColor = NetBandColors.Ground,
            disabledContainerColor = NetBandColors.Raised,
            disabledContentColor = NetBandColors.Muted
        )
    ) {
        Text(text, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
            .size(36.dp)
            .clip(ControlShape)
            .border(1.dp, NetBandColors.Border, ControlShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

fun formatConnection(iface: String?, localIp: String?, gatewayIp: String?): String =
    "${iface ?: "—"} · local ${localIp ?: "—"} · gateway ${gatewayIp ?: "—"}"

fun shortMac(mac: String): String = if (mac.startsWith("manual:")) "MANUAL" else mac

fun deviceStatus(device: com.elvan.netband.Device): String = when {
    device.blocked -> "Blocked ${device.direction}"
    device.limitRate != null -> "Limited ${device.limitRate} ${device.direction}"
    device.online -> "Online"
    else -> "Offline"
}

fun relativeDirection(direction: com.elvan.netband.Direction): String = when (direction) {
    com.elvan.netband.Direction.UP -> "upload"
    com.elvan.netband.Direction.DOWN -> "download"
    com.elvan.netband.Direction.BOTH -> "both"
}
