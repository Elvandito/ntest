package com.elvan.netband.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.InterfaceStats
import com.elvan.netband.NetBandViewModel
import kotlinx.coroutines.delay

@Composable
fun MonitorScreen(vm: NetBandViewModel) {
    val state by vm.state.collectAsState()
    var intervalMs by remember { mutableStateOf(1_000L) }
    var metric by remember { mutableStateOf("down") }
    var downHistory by remember { mutableStateOf(emptyList<Pair<Long, Double>>()) }
    var upHistory by remember { mutableStateOf(emptyList<Pair<Long, Double>>()) }
    var analysisTargetId by remember { mutableStateOf<Int?>(null) }
    var analysisSeconds by remember { mutableStateOf(5) }
    val target = state.devices.firstOrNull { it.id == analysisTargetId } ?: state.devices.firstOrNull()

    LaunchedEffect(intervalMs) {
        while (true) {
            vm.refreshMonitor()
            delay(intervalMs)
        }
    }

    LaunchedEffect(state.monitor?.sampledAtMs) {
        val ts = state.monitor?.sampledAtMs ?: return@LaunchedEffect
        downHistory = (downHistory + (ts to (state.rxRateBytesPerSecond ?: 0.0))).takeLast(240)
        upHistory = (upHistory + (ts to (state.txRateBytesPerSecond ?: 0.0))).takeLast(240)
    }

    val monitor = state.monitor
    val (downValue, downUnit) = rateParts(state.rxRateBytesPerSecond)
    val (upValue, upUnit) = rateParts(state.txRateBytesPerSecond)

    Column(
        Modifier
            .fillMaxSize()
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        ScreenHeader(
            title = "Monitor",
            subtitle = "Interface counters · ${state.netInfo?.iface ?: "no interface"}"
        ) {
            OutlinedIconButton(onClick = { vm.refreshMonitor() }, contentDescription = "Refresh interface counters") {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = NetBandColors.Ink, modifier = Modifier.size(20.dp))
            }
        }

        val heroTone = when {
            state.netInfo == null -> HeroTone.Error
            monitor != null -> HeroTone.Ready
            else -> HeroTone.Warning
        }
        val heroTitle = when {
            state.netInfo == null -> "No interface"
            monitor != null -> "Interface live"
            else -> "Waiting for counters"
        }
        val heroSubtitle = when {
            state.netInfo == null -> "Connect to Wi-Fi or Ethernet to read interface counters."
            monitor != null -> "Cumulative counters are readable. Refresh is ${intervalLabel(intervalMs)}."
            else -> "The interface exists, but /proc/net/dev could not be read."
        }
        val heroDetail = when {
            monitor != null -> "${monitor.iface} · ${InterfaceStats.formatBytes(monitor.rxBytes)} received · ${InterfaceStats.formatBytes(monitor.txBytes)} sent"
            state.netInfo != null -> "${state.netInfo?.iface} · waiting for a readable sample"
            else -> "No active wired or Wi-Fi network"
        }
        HeroStatusCard(
            title = heroTitle,
            subtitle = heroSubtitle,
            detail = heroDetail,
            tone = heroTone,
            icon = Icons.Filled.Speed
        )

        if (state.message.isNotBlank()) StatusLine(state.message, Modifier.padding(top = 8.dp))
        StatusLine("Cumulative since boot from /proc/net/dev. Root is used only when Android blocks the unprivileged read.")

        SectionHeader("Now")
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Metric(
                    label = "Download",
                    value = downValue,
                    unit = downUnit,
                    accent = NetBandColors.Blue,
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(54.dp).background(NetBandColors.Rule))
                Metric(
                    label = "Upload",
                    value = upValue,
                    unit = upUnit,
                    accent = NetBandColors.Green,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalRule(Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Received", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                    Text(
                        monitor?.let { InterfaceStats.formatBytes(it.rxBytes) } ?: "—",
                        color = NetBandColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Transmitted", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                    Text(
                        monitor?.let { InterfaceStats.formatBytes(it.txBytes) } ?: "—",
                        color = NetBandColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "drops ${monitor?.rxDrops ?: "—"} rx / ${monitor?.txDrops ?: "—"} tx",
                    color = NetBandColors.Muted,
                    fontSize = 11.sp,
                    fontFamily = IbmPlexMono
                )
                Spacer(Modifier.weight(1f))
                Text("since boot", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans)
            }
        }

        SectionHeader("Refresh")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(250L to "250ms", 500L to "500ms", 1_000L to "1s", 2_000L to "2s").forEach { (value, label) ->
                FilterPill(label, value == intervalMs, { intervalMs = value })
            }
        }

        SectionHeader("Trace")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("down" to "Download", "up" to "Upload").forEach { (value, label) ->
                FilterPill(label, value == metric, { metric = value })
            }
        }
        Panel(Modifier.fillMaxWidth().padding(top = 6.dp), padding = PaddingValues(18.dp)) {
            val data = if (metric == "down") downHistory else upHistory
            LineChart(samples = data, accent = if (metric == "down") NetBandColors.Blue else NetBandColors.Green)
        }

        SectionHeader("Analyze host")
        if (state.devices.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("No hosts to analyze", color = NetBandColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text(
                    "Open Hosts and scan your network first. Analysis measures the interface window and keeps the host state beside it.",
                    color = NetBandColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = PublicSans,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.devices.forEach { device ->
                    val selected = target?.id == device.id
                    FilterPill(
                        "#${device.id.toString().padStart(2, '0')} ${device.ip}",
                        selected,
                        { analysisTargetId = device.id; vm.clearAnalysis() }
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 5, 30, 60).forEach { seconds ->
                    FilterPill("${seconds}s", seconds == analysisSeconds, { analysisSeconds = seconds })
                }
            }
            PrimaryButton(
                text = if (state.analyzing) "Measuring…" else "Analyze ${target?.ip ?: ""}",
                onClick = { target?.let { vm.analyze(it, analysisSeconds) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                enabled = target != null && !state.analyzing,
                contentDescription = "Analyze host"
            )
            if (state.analysisTargetId == target?.id) {
                state.analysisResult?.let { result ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(NetBandColors.Paper3)
                            .padding(14.dp)
                    ) {
                        Text(
                            result,
                            color = NetBandColors.Ink2,
                            fontFamily = IbmPlexMono,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun intervalLabel(intervalMs: Long): String = when (intervalMs) {
    250L -> "250 ms"
    500L -> "500 ms"
    1_000L -> "1 s"
    2_000L -> "2 s"
    else -> "$intervalMs ms"
}

private fun rateParts(rate: Double?): Pair<String, String> {
    val formatted = rate?.let { InterfaceStats.formatRate(it) } ?: "—"
    val parts = formatted.split(" ", limit = 2)
    return parts[0] to (parts.getOrNull(1) ?: "")
}

@Composable
private fun LineChart(samples: List<Pair<Long, Double>>, accent: Color) {
    Column {
        if (samples.size < 2) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("Two counter samples needed", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
            }
            return
        }
        val current = samples.last().second
        val peak = samples.maxOf { it.second }.coerceAtLeast(1.0)
        Row {
            Text("peak ${InterfaceStats.formatRate(peak)}", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("now ${InterfaceStats.formatRate(current)}", color = NetBandColors.Ink, fontFamily = IbmPlexMono, fontSize = 11.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(156.dp).padding(top = 12.dp)) {
            val width = size.width
            val height = size.height
            val step = width / (samples.size - 1).coerceAtLeast(1)
            val path = Path()
            val area = Path()
            area.moveTo(0f, height)
            samples.forEachIndexed { index, sample ->
                val rate = sample.second
                val x = index * step
                val y = height - (rate / peak).toFloat() * (height * 0.82f) - height * 0.06f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                area.lineTo(x, y)
            }
            area.lineTo(width, height)
            area.close()

            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                drawLine(
                    color = NetBandColors.Rule,
                    start = Offset(0f, height * fraction),
                    end = Offset(width, height * fraction),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent)
                )
            )
            drawPath(path = path, color = accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            val lastY = height - (samples.last().second / peak).toFloat() * (height * 0.82f) - height * 0.06f
            drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(width, lastY))
        }
    }
}
