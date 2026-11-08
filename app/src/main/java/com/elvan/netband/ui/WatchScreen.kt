package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        ScreenHeader(
            title = "Watch",
            subtitle = "Address trail · by MAC"
        )

        val tone = if (watched.isEmpty()) HeroTone.Warning else HeroTone.Ready
        HeroStatusCard(
            title = if (watched.isEmpty()) "No hosts on watch" else "${watched.size} host${if (watched.size == 1) "" else "s"} on watch",
            subtitle = if (watched.isEmpty()) {
                "Add hosts from Hosts to catch DHCP renewals and address moves."
            } else {
                "Address changes are matched by MAC after each scan."
            },
            detail = "Interval ${watchIntervalLabel(state.watchIntervalMs)} · window ${state.watchWindowSeconds}s",
            tone = tone,
            icon = Icons.Filled.Visibility
        )

        if (state.message.isNotBlank()) StatusLine(state.message, Modifier.padding(top = 8.dp))
        StatusLine("Watch runs on demand. Android does not wake the app when an ARP entry changes.")

        SectionHeader("Watch settings")
        Panel(Modifier.fillMaxWidth()) {
            Text("Check interval", color = NetBandColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text(
                "How often a scan may refresh the trail. Manual by design.",
                color = NetBandColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = PublicSans,
                modifier = Modifier.padding(top = 3.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1_000L to "1s", 5_000L to "5s", 10_000L to "10s", 30_000L to "30s").forEach { (value, label) ->
                    FilterPill(label, state.watchIntervalMs == value, {
                        vm.setWatchSettings(value, state.watchWindowSeconds)
                    })
                }
            }
            HorizontalRule(Modifier.padding(vertical = 14.dp))
            Text("History window", color = NetBandColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text(
                "Seconds kept in the in-app address trail.",
                color = NetBandColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = PublicSans,
                modifier = Modifier.padding(top = 3.dp)
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DarkField(
                    value = windowText,
                    onValueChange = { windowText = it },
                    label = "Seconds",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                SecondaryButton(
                    "Save",
                    {
                        windowText.toLongOrNull()?.let { seconds ->
                            vm.setWatchSettings(state.watchIntervalMs, seconds)
                        }
                    },
                    Modifier.width(92.dp)
                )
            }
        }

        SectionHeader("Bulk commands")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Watch all", { vm.watchAll() }, Modifier.weight(1f))
            DangerButton("Remove all", { vm.unwatchAll() }, Modifier.weight(1f))
        }

        SectionHeader("Watched hosts · ${watched.size}")
        if (watched.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("No watch entries", color = NetBandColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text(
                    "Open a host on Hosts and choose Watch host. The trail starts when its MAC appears at a different address.",
                    color = NetBandColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = PublicSans,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                watched.forEachIndexed { index, device ->
                    WatchedRow(device, onRemove = { vm.toggleWatch(device) })
                    if (index < watched.lastIndex) HorizontalRule()
                }
            }
        }

        SectionHeader("IP-change trail")
        Panel(Modifier.fillMaxWidth()) {
            if (state.watchEvents.isEmpty()) {
                Text("No address changes", color = NetBandColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text(
                    "Scan again after a DHCP renewal. Only watched MACs are reported here.",
                    color = NetBandColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = PublicSans,
                    modifier = Modifier.padding(top = 5.dp)
                )
            } else {
                state.watchEvents.forEachIndexed { index, event ->
                    TrailRow(event)
                    if (index < state.watchEvents.lastIndex) HorizontalRule(Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun watchIntervalLabel(intervalMs: Long): String = when (intervalMs) {
    1_000L -> "1 s"
    5_000L -> "5 s"
    10_000L -> "10 s"
    30_000L -> "30 s"
    else -> "$intervalMs ms"
}

@Composable
private fun WatchedRow(device: Device, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.ip, color = NetBandColors.Ink, fontFamily = IbmPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "${device.hostname.ifBlank { "No hostname" }} · ${shortMac(device.mac)}",
                color = NetBandColors.Muted,
                fontFamily = PublicSans,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (device.limitRate != null) {
                Text(
                    "${device.limitRate} · ${relativeDirection(device.direction)}",
                    color = NetBandColors.Amber,
                    fontFamily = PublicSans,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        StatusPill(statusLabel(device), statusTone(device))
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = "Remove ${device.ip} from watch", tint = NetBandColors.Muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun TrailRow(event: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(NetBandColors.Blue))
        Spacer(Modifier.width(10.dp))
        Text(
            event,
            color = NetBandColors.Ink2,
            fontFamily = IbmPlexMono,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
