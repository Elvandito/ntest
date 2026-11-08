package com.elvan.netband.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(21.dp)
private val PillShape = RoundedCornerShape(50)

enum class HeroTone { Ready, Warning, Error }
enum class StatusTone { Positive, Notice, Negative, Neutral, Info }

@Composable
fun NetBandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val stroke = 2.dp.toPx()
        val mid = size.height * 0.50f
        drawLine(NetBandColors.Ink, androidx.compose.ui.geometry.Offset(2.dp.toPx(), mid), androidx.compose.ui.geometry.Offset(size.width * 0.28f, mid), stroke, StrokeCap.Round)
        drawLine(NetBandColors.Blue, androidx.compose.ui.geometry.Offset(size.width * 0.28f, mid), androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(NetBandColors.Blue, androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.74f), stroke, StrokeCap.Round)
        drawLine(NetBandColors.Blue, androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.74f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, mid), stroke, StrokeCap.Round)
        drawLine(NetBandColors.Ink, androidx.compose.ui.geometry.Offset(size.width * 0.72f, mid), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), mid), stroke, StrokeCap.Round)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(NetBandColors.Raised)
            .padding(padding),
        content = content
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = NetBandColors.Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.6).sp,
                lineHeight = 36.sp
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = NetBandColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = PublicSans,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        trailing?.let { trailingContent ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = NetBandColors.Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
        Spacer(Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun HeroStatusCard(
    title: String,
    subtitle: String,
    detail: String,
    tone: HeroTone,
    icon: ImageVector
) {
    val background = when (tone) {
        HeroTone.Ready -> NetBandColors.GreenSoft
        HeroTone.Warning -> NetBandColors.AmberSoft
        HeroTone.Error -> NetBandColors.RedSoft
    }
    val accent = when (tone) {
        HeroTone.Ready -> NetBandColors.Green
        HeroTone.Warning -> NetBandColors.Amber
        HeroTone.Error -> NetBandColors.Red
    }
    val secondary = when (tone) {
        HeroTone.Ready -> NetBandColors.GreenInk
        HeroTone.Warning -> Color(0xFFFFE2AC)
        HeroTone.Error -> Color(0xFFFFC9C9)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 116.dp, top = 22.dp, bottom = 20.dp)
        ) {
            Text(title, color = NetBandColors.Ink, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, fontFamily = PublicSans)
            Text(subtitle, color = secondary, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 7.dp))
            Spacer(Modifier.weight(1f))
            Text(detail, color = secondary, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PublicSans, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .border(9.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(76.dp))
        }
    }
}

@Composable
fun StatusPill(text: String, tone: StatusTone) {
    val background = when (tone) {
        StatusTone.Positive -> NetBandColors.GreenSoft
        StatusTone.Notice -> NetBandColors.AmberSoft
        StatusTone.Negative -> NetBandColors.RedSoft
        StatusTone.Neutral -> NetBandColors.Rule
        StatusTone.Info -> NetBandColors.BlueSoft
    }
    val foreground = when (tone) {
        StatusTone.Positive -> NetBandColors.Green
        StatusTone.Notice -> NetBandColors.Amber
        StatusTone.Negative -> NetBandColors.Red
        StatusTone.Neutral -> NetBandColors.Muted
        StatusTone.Info -> NetBandColors.Blue
    }
    Box(
        Modifier
            .clip(PillShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans, maxLines = 1)
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) NetBandColors.BlueSoft else NetBandColors.Rule)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) NetBandColors.Blue else NetBandColors.Ink2,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = PublicSans,
            maxLines = 1
        )
    }
}

@Composable
fun Metric(
    label: String,
    value: String,
    unit: String = "",
    accent: Color = NetBandColors.Ink,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, color = NetBandColors.Muted, fontSize = 13.sp, fontFamily = PublicSans)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text(value, color = accent, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans, lineHeight = 42.sp, maxLines = 1)
            if (unit.isNotBlank()) {
                Text(unit, color = NetBandColors.Muted, fontSize = 14.sp, fontFamily = PublicSans, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = NetBandColors.Muted,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = NetBandColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = valueColor, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PublicSans, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        placeholder = if (placeholder.isBlank()) null else { { Text(placeholder, fontSize = 13.sp) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = PublicSans, fontSize = 14.sp),
        colors = darkFieldColors()
    )
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NetBandColors.Ink,
    unfocusedTextColor = NetBandColors.Ink,
    focusedContainerColor = NetBandColors.Rule,
    unfocusedContainerColor = NetBandColors.Rule,
    focusedBorderColor = NetBandColors.Blue,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = NetBandColors.Blue,
    focusedLabelColor = NetBandColors.Blue,
    unfocusedLabelColor = NetBandColors.Muted,
    focusedPlaceholderColor = NetBandColors.Muted2,
    unfocusedPlaceholderColor = NetBandColors.Muted2
)

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = NetBandColors.Muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontFamily = PublicSans,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NetBandColors.BlueFill,
            contentColor = NetBandColors.BlueInk,
            disabledContainerColor = NetBandColors.Rule,
            disabledContentColor = NetBandColors.Muted
        )
    ) {
        Text(text, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NetBandColors.Rule,
            contentColor = NetBandColors.Ink,
            disabledContainerColor = NetBandColors.Rule,
            disabledContentColor = NetBandColors.Muted
        )
    ) {
        Text(text, fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NetBandColors.RedSoft,
            contentColor = NetBandColors.Red,
            disabledContainerColor = NetBandColors.Rule,
            disabledContentColor = NetBandColors.Muted
        )
    ) {
        Text(text, fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun MonoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    if (destructive) DangerButton(text, onClick, modifier, enabled) else SecondaryButton(text, onClick, modifier, enabled)
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
            .clip(CircleShape)
            .background(NetBandColors.Rule)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}

fun formatConnection(iface: String?, localIp: String?, gatewayIp: String?): String =
    "${iface ?: "—"} · local ${localIp ?: "—"} · gateway ${gatewayIp ?: "—"}"

fun shortMac(mac: String): String = if (mac.startsWith("manual:")) "Manual" else mac

fun deviceStatus(device: com.elvan.netband.Device): String = when {
    device.blocked -> "Blocked · ${relativeDirection(device.direction)}"
    device.limitRate != null -> "Limited ${device.limitRate} · ${relativeDirection(device.direction)}"
    device.online -> "Online"
    else -> "Offline"
}

fun statusLabel(device: com.elvan.netband.Device): String = when {
    device.blocked -> "Blocked"
    device.limitRate != null -> "Limited"
    device.online -> "Online"
    else -> "Offline"
}

fun statusTone(device: com.elvan.netband.Device): StatusTone = when {
    device.blocked -> StatusTone.Negative
    device.limitRate != null -> StatusTone.Notice
    device.online -> StatusTone.Positive
    else -> StatusTone.Neutral
}

fun relativeDirection(direction: com.elvan.netband.Direction): String = when (direction) {
    com.elvan.netband.Direction.UP -> "upload"
    com.elvan.netband.Direction.DOWN -> "download"
    com.elvan.netband.Direction.BOTH -> "upload + download"
}
