package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.Device
import com.elvan.netband.NetBandViewModel

@Composable
fun WatchScreen(vm: NetBandViewModel) {
    val state by vm.state.collectAsState()
    var windowText by remember(state.watchWindowSeconds) { mutableStateOf(state.watchWindowSeconds.toString()) }
    val watched = state.devices.filter { it.watched }

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
                Text("Watch", color = NetBandColors.Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text("IP-change trail · by MAC", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
        }

        Readout("On watch", watched.size.toString(), "hosts", Modifier.padding(top = 20.dp), NetBandColors.Amber)
        StatusLine("Catches DHCP renewal and device moves. It does not run a background service; changes appear after a scan.", Modifier.padding(top = 6.dp))

        SectionHeader("Watched hosts")
        Panel(Modifier.fillMaxWidth()) {
            Text("Check interval", color = NetBandColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Manual; Android does not wake on ARP changes.", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1_000L to "1s", 5_000L to "5s", 10_000L to "10s", 30_000L to "30s").forEach { (value, label) ->
                    SelectChip(label = label, selected = state.watchIntervalMs == value, onClick = { vm.setWatchSettings(value, state.watchWindowSeconds) })
                }
            }
            HorizontalRule(Modifier.padding(vertical = 14.dp))
            Text("History window", color = NetBandColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Seconds kept in the in-app trail.", color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = windowText,
                    onValueChange = { windowText = it },
                    modifier = Modifier.width(120.dp),
                    label = { Text("Seconds") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp)
                )
                Spacer(Modifier.width(8.dp))
                MonoButton("Save", {
                    windowText.toLongOrNull()?.let { vm.setWatchSettings(state.watchIntervalMs, it) }
                }, Modifier.width(88.dp))
            }
        }

        SectionHeader("Bulk commands")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MonoButton("watch add all", { vm.watchAll() }, Modifier.weight(1f))
            MonoButton("watch remove all", { vm.unwatchAll() }, Modifier.weight(1f), destructive = true)
        }

        SectionHeader("Watched hosts · ${watched.size}")
        if (watched.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("No watch entries", color = NetBandColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text("Add a host on Hosts. The trail starts the next time you scan and its MAC reappears at a different address.", color = NetBandColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 5.dp))
            }
        } else {
            Panel(Modifier.fillMaxWidth()) {
                watched.forEachIndexed { index, device ->
                    WatchedRow(device, onRemove = { vm.toggleWatch(device) })
                    if (index < watched.lastIndex) HorizontalRule()
                }
            }
        }

        SectionHeader("IP-change trail")
        Panel(Modifier.fillMaxWidth()) {
            if (state.watchEvents.isEmpty()) {
                Text("—", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 16.sp)
                Text("No address changes observed since launch.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 5.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.watchEvents.forEach { event -> TrailRow(event) }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WatchedRow(device: Device, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.ip, color = NetBandColors.Ink, fontFamily = IbmPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("#${device.id.toString().padStart(2, '0')}  ·  ${device.mac}", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Text(deviceStatus(device), color = if (device.limitRate != null || device.blocked) NetBandColors.Amber else NetBandColors.Cool, fontFamily = PublicSans, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = "Remove ${device.ip} from watch", tint = NetBandColors.Ink2, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun TrailRow(event: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(1.dp).height(22.dp).background(NetBandColors.Rule2))
        Spacer(Modifier.width(10.dp))
        Text(event, color = NetBandColors.Ink2, fontFamily = IbmPlexMono, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}
