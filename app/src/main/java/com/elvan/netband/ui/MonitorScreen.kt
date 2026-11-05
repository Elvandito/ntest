package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.max

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
            .background(NetBandColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetBandMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Monitor", color = NetBandColors.Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text("Interface instrument · ${state.netInfo?.iface ?: "no interface"}", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
            OutlinedIconButton(onClick = { vm.refreshMonitor() }, contentDescription = "Refresh interface counters") {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = NetBandColors.Ink, modifier = Modifier.size(20.dp))
            }
        }

        StatusLine("Cumulative since boot from /proc/net/dev. Root is used when Android blocks the unprivileged read.", Modifier.padding(top = 10.dp))

        SectionHeader("Refresh")
        IntervalTabs(intervalMs = intervalMs, onIntervalChange = { intervalMs = it })

        Panel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Readout("Down", state.rxRateBytesPerSecond?.let { InterfaceStats.formatRate(it) } ?: "—", modifier = Modifier.weight(1.35f), accent = NetBandColors.Cool)
                VerticalRule()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InstrumentReading("Up", state.txRateBytesPerSecond?.let { InterfaceStats.formatRate(it) } ?: "—", NetBandColors.Ink)
                    InstrumentReading("RX", state.monitor?.let { InterfaceStats.formatBytes(it.rxBytes) } ?: "—", NetBandColors.Muted)
                    InstrumentReading("TX", state.monitor?.let { InterfaceStats.formatBytes(it.txBytes) } ?: "—", NetBandColors.Muted)
                }
            }
            HorizontalRule(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("drops  ${state.monitor?.rxDrops ?: "—"} rx / ${state.monitor?.txDrops ?: "—"} tx", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = IbmPlexMono)
                Spacer(Modifier.weight(1f))
                Text("since boot", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans)
            }
        }

        SectionHeader("Trace")
        MetricTabs(metric = metric, onMetricChange = { metric = it })
        Panel(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val data = if (metric == "down") downHistory else upHistory
            LineChart(samples = data, accent = NetBandColors.Cool)
        }

        SectionHeader("Analyze host")
        if (state.devices.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("No hosts", color = NetBandColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text("Scan the network first. Analysis samples the interface window and keeps the host state beside it.", color = NetBandColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.devices.forEach { d ->
                    val selected = target?.id == d.id
                    AnalysisTargetButton(text = "#${d.id.toString().padStart(2, '0')} ${d.ip}", selected = selected, onClick = { analysisTargetId = d.id; vm.clearAnalysis() })
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 30, 60).forEach { seconds ->
                    val selected = seconds == analysisSeconds
                    AnalysisTargetButton(text = "${seconds}s", selected = selected, onClick = { analysisSeconds = seconds })
                }
            }
            MonoButton(
                text = if (state.analyzing) "Measuring…" else "Analyze ${target?.ip ?: ""}",
                onClick = { target?.let { vm.analyze(it, analysisSeconds) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                enabled = target != null && !state.analyzing
            )
            if (state.analysisTargetId == target?.id) state.analysisResult?.let { result ->
                Panel(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row {
                        Box(Modifier.width(2.dp).height(18.dp).background(NetBandColors.Amber))
                        Spacer(Modifier.width(8.dp))
                        Text("analysis · ${target?.ip ?: ""}", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans)
                    }
                    Text(result, color = NetBandColors.Ink2, fontFamily = IbmPlexMono, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun IntervalTabs(intervalMs: Long, onIntervalChange: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(250L to "250ms", 500L to "500ms", 1_000L to "1s", 2_000L to "2s").forEach { (value, label) ->
            val selected = value == intervalMs
            SelectChip(label = label, selected = selected, onClick = { onIntervalChange(value) })
        }
    }
}

@Composable
private fun MetricTabs(metric: String, onMetricChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("down" to "Down", "up" to "Up").forEach { (value, label) ->
            val selected = value == metric
            SelectChip(label = label, selected = selected, onClick = { onMetricChange(value) })
        }
    }
}

@Composable
private fun AnalysisTargetButton(text: String, selected: Boolean, onClick: () -> Unit) {
    SelectChip(label = text, selected = selected, onClick = onClick)
}

@Composable
private fun InstrumentReading(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = NetBandColors.Muted, fontSize = 10.sp, fontFamily = PublicSans)
        Text(value, color = tint, fontSize = 15.sp, fontFamily = IbmPlexMono, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun LineChart(samples: List<Pair<Long, Double>>, accent: androidx.compose.ui.graphics.Color) {
    Column {
        if (samples.size < 2) {
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Text("Two counter samples needed", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
            }
            return
        }
        val current = samples.last().second
        val max = samples.maxOf { it.second }.coerceAtLeast(1.0)
        Row {
            Text("peak ${InterfaceStats.formatRate(max)}", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("now ${InterfaceStats.formatRate(current)}", color = NetBandColors.Ink, fontFamily = IbmPlexMono, fontSize = 11.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(148.dp).padding(top = 12.dp)) {
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
            val spine = 0.94f
            drawLine(
                color = NetBandColors.Rule,
                start = Offset(0f, height * spine),
                end = Offset(width, height * spine),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}

@Composable
private fun VerticalRule() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(NetBandColors.Rule2).padding(vertical = 4.dp))
}
