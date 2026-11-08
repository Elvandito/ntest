package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.Device
import com.elvan.netband.Direction
import com.elvan.netband.NetBandViewModel
import com.elvan.netband.UiState

private enum class HostFilter(val label: String) {
    ALL("All"), ONLINE("Online"), LIMITED("Limited"), BLOCKED("Blocked"), WATCHED("Watched")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(vm: NetBandViewModel) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HostFilter.ALL) }
    var selected by remember { mutableStateOf<Device?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showLimit by remember { mutableStateOf<Device?>(null) }
    var showBlock by remember { mutableStateOf<Device?>(null) }

    val filtered = state.devices.filter { device ->
        val matchesQuery = query.isBlank() ||
            device.ip.contains(query, ignoreCase = true) ||
            device.mac.contains(query, ignoreCase = true) ||
            device.hostname.contains(query, ignoreCase = true) ||
            device.id.toString() == query.trim()
        val matchesFilter = when (filter) {
            HostFilter.ALL -> true
            HostFilter.ONLINE -> device.online
            HostFilter.LIMITED -> device.limitRate != null
            HostFilter.BLOCKED -> device.blocked
            HostFilter.WATCHED -> device.watched
        }
        matchesQuery && matchesFilter
    }
    val selectedDevice = selected?.let { current -> state.devices.firstOrNull { it.id == current.id } ?: current }
    val limitDevice = showLimit?.let { current -> state.devices.firstOrNull { it.id == current.id } ?: current }
    val blockDevice = showBlock?.let { current -> state.devices.firstOrNull { it.id == current.id } ?: current }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        ScreenHeader(
            title = "NetBand",
            subtitle = "${state.devices.size} saved hosts Â· ${if (state.hasRoot) "root ready" else "scan-only"}"
        ) {
            OutlinedIconButton(onClick = { showAdd = true }, contentDescription = "Add host") {
                Icon(Icons.Filled.Add, contentDescription = null, tint = NetBandColors.Ink, modifier = Modifier.size(21.dp))
            }
            PrimaryButton(
                text = if (state.scanning) "Scanning" else "Scan",
                onClick = { vm.scan() },
                modifier = Modifier.width(84.dp),
                enabled = !state.scanning
            )
        }

        val network = state.netInfo
        val heroTone = when {
            network == null -> HeroTone.Error
            state.hasRoot -> HeroTone.Ready
            else -> HeroTone.Warning
        }
        val heroTitle = when {
            network == null -> "No network"
            state.hasRoot -> "Network ready"
            else -> "Scan-only mode"
        }
        val heroSubtitle = when {
            network == null -> "Connect to Wi-Fi or Ethernet to scan your own network."
            state.hasRoot -> "Root shell is available. Limits, blocks, and interface counters are ready."
            else -> "Root shell is unavailable. Discovery and manual add still work."
        }
        val heroDetail = if (network != null) "${network.iface} Â· ${network.localIp}" else "No active interface"
        HeroStatusCard(
            title = heroTitle,
            subtitle = heroSubtitle,
            detail = heroDetail,
            tone = heroTone,
            icon = if (heroTone == HeroTone.Ready) Icons.Filled.Check else Icons.Filled.Info
        )

        if (state.message.isNotBlank()) StatusLine(state.message, Modifier.padding(top = 8.dp))
        StatusLine("Use only on a network and devices you own or are authorized to manage.", Modifier.padding(top = 2.dp))

        SectionHeader("This network")
        Panel(Modifier.fillMaxWidth()) {
            InfoRow("Gateway", network?.gatewayIp ?: "â€”")
            InfoRow("Gateway MAC", state.gatewayMac.ifBlank { "Not discovered" })
            InfoRow("Subnet prefix", network?.let { "/${it.prefixLength}" } ?: "â€”")
            InfoRow("Watched hosts", state.devices.count { it.watched }.toString())
        }

        SectionHeader("Scan")
        Panel(Modifier.fillMaxWidth()) {
            Text("Range", color = NetBandColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
            OutlinedTextField(
                value = state.scanRange,
                onValueChange = vm::setScanRange,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                placeholder = { Text("192.168.1.10-192.168.1.80", color = NetBandColors.Muted2, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = darkFieldColors()
            )
            Text("Optional. Leave blank to scan the local subnet.", color = NetBandColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 8.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            placeholder = { Text("Search hosts", color = NetBandColors.Muted2, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NetBandColors.Muted, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = darkFieldColors()
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HostFilter.entries.forEach { option ->
                FilterPill(option.label, option == filter, { filter = option })
            }
        }

        SectionHeader("Hosts Â· ${filtered.size}") {
            TextButton(onClick = { vm.freeAll() }) {
                Text("Free all", color = NetBandColors.Blue, fontSize = 14.sp, fontFamily = PublicSans)
            }
        }

        if (filtered.isEmpty()) {
            EmptyHosts(query = query, onClear = { query = ""; filter = HostFilter.ALL }, onAdd = { showAdd = true }, onScan = { vm.scan() })
        } else {
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                filtered.forEachIndexed { index, device ->
                    HostRow(device = device, onOpen = { selected = device })
                    if (index < filtered.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) {
        AddHostDialog(
            onDismiss = { showAdd = false },
            onAdd = { ip, mac -> vm.addHost(ip, mac); showAdd = false }
        )
    }
    selectedDevice?.let { device ->
        DeviceDetailDialog(
            device = device,
            state = state,
            onDismiss = { selected = null },
            onLimit = { showLimit = device },
            onBlock = { showBlock = device },
            onFree = { vm.free(device) },
            onWatch = { vm.toggleWatch(device) },
            onAnalyze = { seconds -> vm.analyze(device, seconds) }
        )
    }
    limitDevice?.let { device ->
        LimitDialog(
            device = device,
            onDismiss = { showLimit = null },
            onApply = { rate, direction -> vm.limit(device, rate, direction); showLimit = null }
        )
    }
    blockDevice?.let { device ->
        BlockDialog(
            device = device,
            onDismiss = { showBlock = null },
            onBlock = { direction -> vm.block(device, direction); showBlock = null }
        )
    }
}

@Composable
private fun HostRow(device: Device, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.ip, color = NetBandColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                if (device.watched) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Visibility, contentDescription = "Watched", tint = NetBandColors.Blue, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                listOf(device.hostname.ifBlank { "No hostname" }, shortMac(device.mac)).joinToString(" Â· "),
                color = NetBandColors.Muted,
                fontSize = 12.sp,
                fontFamily = PublicSans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (device.limitRate != null) {
                Text(
                    "${device.limitRate} Â· ${relativeDirection(device.direction)}",
                    color = NetBandColors.Amber,
                    fontSize = 12.sp,
                    fontFamily = PublicSans,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        StatusPill(statusLabel(device), statusTone(device))
        IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Open ${device.ip}", tint = NetBandColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyHosts(query: String, onClear: () -> Unit, onAdd: () -> Unit, onScan: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Text(
            if (query.isBlank()) "No hosts yet" else "No match for \"$query\"",
            color = NetBandColors.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PublicSans
        )
        Text(
            if (query.isBlank()) "Scan this network to discover devices, or add an address you already know."
            else "Clear the search to see the full host list.",
            color = NetBandColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = PublicSans,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isBlank()) {
                SecondaryButton("Scan network", onScan, Modifier.weight(1f))
                SecondaryButton("Add host", onAdd, Modifier.weight(1f))
            } else {
                SecondaryButton("Clear search", onClear, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AddHostDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = NetBandColors.Raised,
        title = { Text("Add host", color = NetBandColors.Ink, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DarkField(value = ip, onValueChange = { ip = it }, label = "IP address")
                DarkField(value = mac, onValueChange = { mac = it }, label = "MAC address (optional)", placeholder = "aa:bb:cc:dd:ee:ff")
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(ip, mac) }, modifier = Modifier.height(44.dp)) {
                Text("Add host", color = NetBandColors.Blue, fontFamily = PublicSans, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) {
                Text("Cancel", color = NetBandColors.Muted, fontFamily = PublicSans)
            }
        }
    )
}

@Composable
private fun DeviceDetailDialog(
    device: Device,
    state: UiState,
    onDismiss: () -> Unit,
    onLimit: () -> Unit,
    onBlock: () -> Unit,
    onFree: () -> Unit,
    onWatch: () -> Unit,
    onAnalyze: (Int) -> Unit
) {
    var analysisSeconds by remember { mutableStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = NetBandColors.Raised,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.ip, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, color = NetBandColors.Ink, fontSize = 22.sp)
                    Spacer(Modifier.height(5.dp))
                    StatusPill(statusLabel(device), statusTone(device))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetBandColors.Muted)
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InfoRow("Host", "#" + device.id.toString().padStart(2, '0'))
                InfoRow("MAC", shortMac(device.mac))
                InfoRow("Hostname", device.hostname.ifBlank { "Not resolved" })
                InfoRow("Watch", if (device.watched) "On" else "Off")
                if (device.limitRate != null) InfoRow("Rate", "${device.limitRate} Â· ${relativeDirection(device.direction)}")
                Spacer(Modifier.height(10.dp))
                Text("Controls", color = NetBandColors.Muted, fontSize = 13.sp, fontFamily = PublicSans)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Limit", onLimit, Modifier.weight(1f))
                    DangerButton("Block", onBlock, Modifier.weight(1f))
                    SecondaryButton("Free", onFree, Modifier.weight(1f))
                }
                SecondaryButton(if (device.watched) "Stop watching" else "Watch host", onWatch, Modifier.fillMaxWidth().padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                Text("Analyze over", color = NetBandColors.Muted, fontSize = 13.sp, fontFamily = PublicSans)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 30, 60).forEach { seconds ->
                        FilterPill("${seconds}s", seconds == analysisSeconds, { analysisSeconds = seconds })
                    }
                }
                PrimaryButton(
                    if (state.analyzing) "Measuringâ€¦" else "Analyze $analysisSeconds seconds",
                    { onAnalyze(analysisSeconds) },
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    enabled = !state.analyzing
                )
                if (state.analysisTargetId == device.id) {
                    state.analysisResult?.let { result ->
                        Text(result, color = NetBandColors.Ink2, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = IbmPlexMono, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun LimitDialog(device: Device, onDismiss: () -> Unit, onApply: (String, Direction) -> Unit) {
    var rate by remember { mutableStateOf(device.limitRate ?: "512kbit") }
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = NetBandColors.Raised,
        title = { Text("Limit ${device.ip}", color = NetBandColors.Ink, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DarkField(value = rate, onValueChange = { rate = it }, label = "Rate", placeholder = "512kbit Â· 2mbit")
                DirectionSelector(direction, { direction = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(rate.trim(), direction) }, modifier = Modifier.height(44.dp)) {
                Text("Apply limit", color = NetBandColors.Blue, fontFamily = PublicSans, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) {
                Text("Cancel", color = NetBandColors.Muted, fontFamily = PublicSans)
            }
        }
    )
}

@Composable
private fun BlockDialog(device: Device, onDismiss: () -> Unit, onBlock: (Direction) -> Unit) {
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = NetBandColors.Raised,
        title = { Text("Block ${device.ip}", color = NetBandColors.Ink, fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Drop traffic for this host.", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 14.sp)
                DirectionSelector(direction, { direction = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onBlock(direction) }, modifier = Modifier.height(44.dp)) {
                Text("Block host", color = NetBandColors.Red, fontFamily = PublicSans, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) {
                Text("Cancel", color = NetBandColors.Muted, fontFamily = PublicSans)
            }
        }
    )
}

@Composable
private fun DirectionSelector(direction: Direction, onDirectionChange: (Direction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Direction.entries.forEach { option ->
            FilterPill(
                relativeDirection(option).replaceFirstChar { it.uppercase() },
                option == direction,
                { onDirectionChange(option) },
                Modifier.weight(1f)
            )
        }
    }
}
