package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.NetBandViewModel

private data class CommandEntry(val command: String, val destination: String)

private val commandMap = listOf(
    CommandEntry("scan [--range IP-IP]", "Hosts · Range field, then Scan"),
    CommandEntry("hosts", "Hosts · all discovered devices"),
    CommandEntry("limit IDs RATE", "Host · Limit · rate and direction"),
    CommandEntry("block IDs", "Host · Block · direction, then confirm"),
    CommandEntry("free IDs | all", "Host · Free, or Hosts · Free all"),
    CommandEntry("add IP [--mac MAC]", "Hosts · Add host"),
    CommandEntry("monitor [--interval MS]", "Monitor · refresh chips"),
    CommandEntry("analyze IDs [--duration S]", "Host or Monitor · 1s, 5s, 30s or 60s"),
    CommandEntry("status", "Hosts · state pill on every row"),
    CommandEntry("watch add / remove", "Host · Watch, or Watch · bulk commands"),
    CommandEntry("watch set range | interval", "Watch · History window and check interval"),
    CommandEntry("clear", "Settings · Clear local host list"),
    CommandEntry("help", "Settings · This command map"),
    CommandEntry("quit", "Close NetBand")
)

@Composable
fun SettingsScreen(vm: NetBandViewModel, onQuit: () -> Unit) {
    val state by vm.state.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Authorized local controls"
        )

        StatusLine("Use only on a network and devices you own or are authorized to manage.")

        SectionHeader("Target")
        Panel(Modifier.fillMaxWidth()) {
            InfoRow("Interface", state.netInfo?.iface ?: "—")
            InfoRow("Local IP", state.netInfo?.localIp ?: "—")
            InfoRow("Gateway", state.netInfo?.gatewayIp ?: "—")
            InfoRow("Gateway MAC", state.gatewayMac.ifBlank { "Not discovered" })
            InfoRow(
                "Root shell",
                if (state.hasRoot) "Available" else "Unavailable",
                valueColor = if (state.hasRoot) NetBandColors.Green else NetBandColors.Amber
            )
        }

        SectionHeader("Command map")
        Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            commandMap.forEachIndexed { index, entry ->
                CommandRow(entry)
                if (index < commandMap.lastIndex) HorizontalRule()
            }
        }

        SectionHeader("Local data")
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "Stored on this phone",
                color = NetBandColors.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PublicSans
            )
            Text(
                "IDs, addresses, hostnames, and limit/block/watch state. Clearing removes the saved list; it does not undo rules already applied through tc or iptables.",
                color = NetBandColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = PublicSans,
                modifier = Modifier.padding(top = 5.dp)
            )
            DangerButton(
                "Clear local data",
                { showClearConfirm = true },
                Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }

        SectionHeader("App")
        Panel(Modifier.fillMaxWidth()) {
            Text("NetBand", color = NetBandColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
            Text(
                "Version 1.0 · local network administration from an Android device.",
                color = NetBandColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = PublicSans,
                modifier = Modifier.padding(top = 4.dp)
            )
            PrimaryButton("Quit", onQuit, Modifier.fillMaxWidth().padding(top = 14.dp))
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = NetBandColors.Raised,
            title = { Text("Clear local data?", color = NetBandColors.Ink, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "This deletes the saved host list and watch entries. Existing root rules are not removed.",
                    color = NetBandColors.Muted,
                    fontFamily = PublicSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clear(); showClearConfirm = false }, modifier = Modifier.height(44.dp)) {
                    Text("Clear data", color = NetBandColors.Red, fontFamily = PublicSans, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }, modifier = Modifier.height(44.dp)) {
                    Text("Keep data", color = NetBandColors.Muted, fontFamily = PublicSans)
                }
            }
        )
    }
}

@Composable
private fun CommandRow(entry: CommandEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            entry.command,
            color = NetBandColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = IbmPlexMono
        )
        Text(
            entry.destination,
            color = NetBandColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = PublicSans,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
