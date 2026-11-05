package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.NetBandViewModel

@Composable
fun SettingsScreen(vm: NetBandViewModel, onQuit: () -> Unit) {
    val state by vm.state.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

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
            Column {
                Text("Settings", color = NetBandColors.Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text("Authorized local controls", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
        }

        StatusLine("Use only on a network and devices you own or are authorized to manage.", Modifier.padding(top = 10.dp))

        SectionHeader("Target")
        Panel(Modifier.fillMaxWidth()) {
            DetailLine("Interface", state.netInfo?.iface ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Local IP", state.netInfo?.localIp ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Gateway", state.netInfo?.gatewayIp ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Gateway MAC", state.gatewayMac.ifBlank { "Not discovered" })
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Root shell", if (state.hasRoot) "ready" else "unavailable", if (state.hasRoot) NetBandColors.Amber else NetBandColors.Muted)
        }

        SectionHeader("Command map")
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandRow("scan [--range IP-IP]", "Hosts · Range field, then Scan")
                CommandRow("hosts", "Hosts · all discovered devices")
                CommandRow("limit IDs RATE", "Host · Limit · rate and direction")
                CommandRow("block IDs", "Host · Block · direction, then confirm")
                CommandRow("free IDs | all", "Host · Free, or Hosts · Free all")
                CommandRow("add IP [--mac MAC]", "Hosts · Add host")
                CommandRow("monitor [--interval MS]", "Monitor · interval chips")
                CommandRow("analyze IDs [--duration S]", "Host or Monitor · 1s, 5s, 30s or 60s")
                CommandRow("status", "Hosts · state column on every row")
                CommandRow("watch add / remove", "Host · Watch, or Watch · bulk commands")
                CommandRow("watch set range | interval", "Watch · History window and check interval")
                CommandRow("clear", "Settings · Clear local host list")
                CommandRow("help", "Settings · This command map")
                CommandRow("quit", "Close NetBand")
            }
        }

        SectionHeader("Local data")
        Panel(Modifier.fillMaxWidth()) {
            Text("Stored on this phone", color = NetBandColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("IDs, addresses, hostnames, and limit/block/watch state. Clearing removes the saved list; it does not undo rules already applied through tc or iptables.", color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 5.dp))
            MonoButton("Clear local data", { showClearConfirm = true }, Modifier.padding(top = 14.dp).fillMaxWidth(), destructive = true)
        }

        SectionHeader("App")
        Panel(Modifier.fillMaxWidth()) {
            Text("NetBand", color = NetBandColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Local network administration from an Android device.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 4.dp))
            PrimaryButton("Quit", onQuit, Modifier.padding(top = 14.dp).fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = NetBandColors.Raised,
            title = { Text("Clear local data?", color = NetBandColors.Ink, fontFamily = PublicSans) },
            text = { Text("This deletes the saved host list and watch entries. Existing root rules are not removed.", color = NetBandColors.Muted, fontFamily = PublicSans, lineHeight = 18.sp) },
            confirmButton = { TextButton(onClick = { vm.clear(); showClearConfirm = false }, modifier = Modifier.height(44.dp)) { Text("Clear data", color = NetBandColors.Bad, fontFamily = PublicSans) } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }, modifier = Modifier.height(44.dp)) { Text("Keep data", color = NetBandColors.Ink2, fontFamily = PublicSans) } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, tint: androidx.compose.ui.graphics.Color = NetBandColors.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.width(112.dp))
        Text(value, color = tint, fontSize = 12.sp, fontFamily = IbmPlexMono, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CommandRow(command: String, destination: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(1.dp).height(26.dp).background(NetBandColors.Rule2))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(command, color = NetBandColors.Ink, fontSize = 11.sp, fontFamily = IbmPlexMono)
            Text(destination, color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = PublicSans)
        }
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}
