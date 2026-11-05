package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.InterfaceStats
import com.elvan.netband.NetBandViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(
        Modifier
            .fillMaxSize()
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetBandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Monitor", color = NetBandColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text("Interface counters, ${state.netInfo?.iface ?: "no interface"}", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
            OutlinedIconButton(onClick = { vm.refreshMonitor() }, contentDescription = "Refresh interface counters") {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = NetBandColors.Text)
            }
        }

        StatusLine("Counts are read from /proc/net/dev; no per-host bytes are invented or inferred.", Modifier.padding(top = 8.dp))

        SectionHeader("Refresh interval")
        IntervalTabs(intervalMs = intervalMs, onIntervalChange = { intervalMs = it })

        Panel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    StatCell("Down", state.rxRateBytesPerSecond?.let { InterfaceStats.formatRate(it) } ?: "—", "", Modifier.weight(1f))
                    VerticalRule()
                    StatCell("Up", state.txRateBytesPerSecond?.let { InterfaceStats.formatRate(it) } ?: "—", "", Modifier.weight(1f))
                }
                HorizontalRule()
                Row(Modifier.fillMaxWidth()) {
                    StatCell("RX total", state.monitor?.let { InterfaceStats.formatBytes(it.rxBytes) } ?: "—", "", Modifier.weight(1f))
                    VerticalRule()
                    StatCell("TX total", state.monitor?.let { InterfaceStats.formatBytes(it.txBytes) } ?: "—", "", Modifier.weight(1f))
                }
                HorizontalRule()
                Row(Modifier.fillMaxWidth()) {
                    StatCell("Drops RX", state.monitor?.rxDrops?.toString() ?: "—", "pkts", Modifier.weight(1f))
                    VerticalRule()
                    StatCell("Drops TX", state.monitor?.txDrops?.toString() ?: "—", "pkts", Modifier.weight(1f))
                }
            }
        }

        SectionHeader("Throughput")
        MetricTabs(metric = metric, onMetricChange = { metric = it })
        Panel(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val data = if (metric == "down") downHistory else upHistory
            LineChart(samples = data, accent = NetBandColors.Accent)
        }

        SectionHeader("Analyze host")
        if (state.devices.isEmpty()) {
            StatusLine("No hosts yet — scan the network first.")
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.devices.forEach { d ->
                    val selected = target?.id == d.id
                    TextButton(
                        onClick = { analysisTargetId = d.id; vm.clearAnalysis() },
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("#${d.id.toString().padStart(2, '0')} ${d.ip}", color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 5, 30, 60).forEach { seconds ->
                    val selected = seconds == analysisSeconds
                    TextButton(onClick = { analysisSeconds = seconds }, modifier = Modifier.height(32.dp)) {
                        Text("${seconds}s", color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 12.sp)
                    }
                    if (selected) Box(Modifier.width(1.dp).height(18.dp).background(NetBandColors.Accent).align(Alignment.CenterVertically))
                }
            }
            MonoButton(
                text = if (state.analyzing) "Measuring…" else "Analyze ${target?.ip ?: ""}",
                onClick = { target?.let { vm.analyze(it, analysisSeconds) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = target != null && !state.analyzing
            )
            if (state.analysisTargetId == target?.id) state.analysisResult?.let { result ->
                Panel(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(result, color = NetBandColors.Text, fontFamily = IbmPlexMono, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun IntervalTabs(intervalMs: Long, onIntervalChange: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(250L to "250ms", 500L to "500ms", 1_000L to "1s", 2_000L to "2s").forEach { (value, label) ->
            val selected = value == intervalMs
            Column(Modifier.clickable { onIntervalChange(value) }.padding(vertical = 3.dp)) {
                Text(label, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 12.sp)
                Box(Modifier.padding(top = 3.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
    }
}

@Composable
private fun MetricTabs(metric: String, onMetricChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("down" to "Down", "up" to "Up").forEach { (value, label) ->
            val selected = value == metric
            Column(Modifier.clickable { onMetricChange(value) }.padding(vertical = 3.dp)) {
                Text(label, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
                Box(Modifier.padding(top = 3.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
    }
}

@Composable
private fun LineChart(samples: List<Pair<Long, Double>>, accent: androidx.compose.ui.graphics.Color) {
    Column {
        if (samples.size < 2) {
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Text("Waiting for two counter samples", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
            }
            return
        }
        val current = samples.last().second
        val max = samples.maxOf { it.second }.coerceAtLeast(1.0)
        Row {
            Text("peak ${InterfaceStats.formatRate(max)}", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("now ${InterfaceStats.formatRate(current)}", color = NetBandColors.Text, fontFamily = IbmPlexMono, fontSize = 11.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(148.dp).padding(top = 10.dp)) {
            val width = size.width
            val height = size.height
            val step = width / (samples.size - 1).coerceAtLeast(1)
            val path = Path()
            samples.forEachIndexed { index, (_, rate) ->
                val x = index * step
                val y = height - (rate / max).toFloat() * (height * 0.82f) - height * 0.06f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            drawLine(
                color = NetBandColors.Border,
                start = Offset(0f, height * 0.94f),
                end = Offset(width, height * 0.94f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun HorizontalRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}

@Composable
private fun VerticalRule() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(NetBandColors.Rule))
}
