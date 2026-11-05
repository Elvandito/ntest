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
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetBandMark()
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Settings", color = NetBandColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
                Text("Local controls and command map", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
        }

        StatusLine("Use only on a network and devices you own or are authorized to manage.", Modifier.padding(top = 8.dp))

        SectionHeader("Network target")
        Panel(Modifier.fillMaxWidth()) {
            DetailLine("Interface", state.netInfo?.iface ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Local IP", state.netInfo?.localIp ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Gateway", state.netInfo?.gatewayIp ?: "—")
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Gateway MAC", state.gatewayMac.ifBlank { "Not discovered yet" })
            HorizontalRule(Modifier.padding(vertical = 8.dp))
            DetailLine("Root shell", if (state.hasRoot) "Ready (tc/iptables)" else "Unavailable")
        }

        SectionHeader("Command map")
        Panel(Modifier.fillMaxWidth()) {
            CommandRow("scan [--range IP-IP]", "Hosts · optional Range field → Scan")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("hosts", "Hosts · all discovered devices")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("limit IDs RATE", "Host → Limit → rate and direction → Apply")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("block IDs", "Host → Block → direction → confirm")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("free IDs|all", "Host → Free or Hosts → Free all")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("add IP [--mac MAC]", "Hosts → Add host")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("monitor [--interval MS]", "Monitor · interval tabs and interface counters")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("analyze IDs [--duration S]", "Host or Monitor → Analyze for 1/5/30/60s")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("status", "Hosts list · Limited/Blocked state per host")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("watch add / remove", "Host → Watch or Watch tab → Bulk commands")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("watch set range|interval VAL", "Watch · History window and interval")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("clear", "Settings · Clear local host list")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("help", "This command map")
            HorizontalRule(Modifier.padding(vertical = 7.dp))
            CommandRow("quit", "Close NetBand")
        }

        SectionHeader("Local data")
        Panel(Modifier.fillMaxWidth()) {
            Text("Stored on this device", color = NetBandColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            Text("Device IDs, IPs, MAC addresses, hostnames, limit/block/watch state are kept in app-private storage. Clearing removes the list and watch entries, not any root iptables or tc rules that were already applied.", color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 5.dp))
            MonoButton("Clear local data", { showClearConfirm = true }, Modifier.padding(top = 12.dp), destructive = true)
        }

        SectionHeader("App")
        Panel(Modifier.fillMaxWidth()) {
            Text("NetBand", color = NetBandColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
            Text("Authorized network control panel for Android. Built from the command-line feature set.", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 4.dp))
            PrimaryButton("Quit", onQuit, Modifier.padding(top = 14.dp).fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = NetBandColors.Raised,
            title = { Text("Clear local data?", color = NetBandColors.Text, fontFamily = PublicSans) },
            text = { Text("This deletes the saved host list and watch entries. Active root rules are not removed automatically.", color = NetBandColors.Muted, fontFamily = PublicSans) },
            confirmButton = { TextButton(onClick = { vm.clear(); showClearConfirm = false }) { Text("Clear", color = NetBandColors.Negative, fontFamily = PublicSans) } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Keep", fontFamily = PublicSans) } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.width(112.dp))
        Text(value, color = NetBandColors.Text, fontSize = 12.sp, fontFamily = IbmPlexMono, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CommandRow(command: String, destination: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(command, color = NetBandColors.Text, fontSize = 11.sp, fontFamily = IbmPlexMono, modifier = Modifier.width(150.dp))
        Text(destination, color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = PublicSans, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}
