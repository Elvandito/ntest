package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetBandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Watch", color = NetBandColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text("${watched.size} host${if (watched.size == 1) "" else "s"} on IP-change watch", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
        }

        StatusLine("Watch catches IP renewal and laptop/DHCP churn by MAC address. Root is not required for this list.", Modifier.padding(top = 8.dp))

        SectionHeader("IP-change watch")
        Panel(Modifier.fillMaxWidth()) {
            Text("Check interval", color = NetBandColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Scan interval used by manual refresh; Android does not run a background daemon.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1_000L to "1s", 5_000L to "5s", 10_000L to "10s", 30_000L to "30s").forEach { (value, label) ->
                    val selected = state.watchIntervalMs == value
                    Column(Modifier.clickable { vm.setWatchSettings(value, state.watchWindowSeconds) }.padding(vertical = 2.dp)) {
                        Text(label, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 12.sp)
                        Box(Modifier.padding(top = 3.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
            HorizontalRule(Modifier.padding(vertical = 12.dp))
            Text("History window", color = NetBandColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Seconds retained for watch events before they rotate out.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = windowText,
                    onValueChange = { windowText = it },
                    modifier = Modifier.width(110.dp),
                    label = { Text("Seconds") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp)
                )
                Spacer(Modifier.width(8.dp))
                MonoButton("Save", {
                    windowText.toLongOrNull()?.let { vm.setWatchSettings(state.watchIntervalMs, it) }
                }, Modifier.width(86.dp))
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
                Text("No watch entries", color = NetBandColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text("Choose hosts on the Hosts tab, then add them to watch to catch an IP change after DHCP renewal.", color = NetBandColors.Muted, fontSize = 13.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Panel(Modifier.fillMaxWidth()) {
                watched.forEachIndexed { index, device ->
                    WatchedRow(device, onRemove = { vm.toggleWatch(device) })
                    if (index < watched.lastIndex) HorizontalRule(Modifier.padding(vertical = 0.dp))
                }
            }
        }

        SectionHeader("IP changes · ${state.watchEvents.size}")
        Panel(Modifier.fillMaxWidth()) {
            if (state.watchEvents.isEmpty()) {
                Text("—", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 14.sp)
                Text("No IP changes observed since app launch.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 4.dp))
            } else {
                state.watchEvents.forEachIndexed { index, event ->
                    Text(event, color = NetBandColors.Text, fontFamily = IbmPlexMono, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (index < state.watchEvents.lastIndex) HorizontalRule(Modifier.padding(vertical = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WatchedRow(device: Device, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.ip, color = NetBandColors.Text, fontFamily = IbmPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("#${device.id.toString().padStart(2, '0')} · ${device.mac}", color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(deviceStatus(device), color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = "Remove ${device.ip} from watch", tint = NetBandColors.Text, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}
