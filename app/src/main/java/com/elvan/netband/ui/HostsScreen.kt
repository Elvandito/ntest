package com.elvan.netband.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.Device
import com.elvan.netband.Direction
import com.elvan.netband.NetBandViewModel
import com.elvan.netband.UiState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag

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

    Column(
        Modifier
            .fillMaxSize()
            .background(NetBandColors.Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        HeaderRow(
            state = state,
            onScan = { vm.scan() },
            onAdd = { showAdd = true },
            scanning = state.scanning
        )

        StatusLine(formatConnection(state.netInfo?.iface, state.netInfo?.localIp, state.netInfo?.gatewayIp), Modifier.padding(top = 6.dp))
        if (!state.hasRoot) StatusLine("Root unavailable — scan and add-host work; limit/block need root.", Modifier.padding(top = 2.dp))
        if (state.message.isNotBlank()) StatusLine(state.message, Modifier.padding(top = 2.dp))

        ScanRangeBar(state = state, vm = vm, onScan = { vm.scan() })

        KpiGrid(state)

        FilterTabs(query = query, onQueryChange = { query = it }, filter = filter, onFilterChange = { filter = it })

        SectionHeader(title = "Hosts · ${filtered.size}/${state.devices.size}") {
            TextButton(onClick = { vm.freeAll() }) { Text("Free all", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = IbmPlexMono) }
        }

        if (filtered.isEmpty()) {
            EmptyHosts(query = query, onClear = { query = ""; filter = HostFilter.ALL })
        } else {
            Panel(Modifier.fillMaxWidth()) {
                filtered.forEachIndexed { index, device ->
                    DeviceRow(
                        device = device,
                        onOpen = { selected = device },
                        onLimit = { showLimit = device },
                        onBlock = { showBlock = device },
                        onFree = { vm.free(device) },
                        onWatch = { vm.toggleWatch(device) }
                    )
                    if (index < filtered.lastIndex) HorizontalRule()
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) AddHostDialog(onDismiss = { showAdd = false }, onAdd = { ip, mac -> vm.addHost(ip, mac); showAdd = false })
    selected?.let { device ->
        DeviceDetailDialog(
            device = device,
            state = state,
            onDismiss = { selected = null; vm.refreshMonitor() },
            onLimit = { showLimit = device },
            onBlock = { showBlock = device },
            onFree = { vm.free(device) },
            onWatch = { vm.toggleWatch(device) },
            onAnalyze = { seconds -> vm.analyze(device, seconds) }
        )
    }
    showLimit?.let { device -> LimitDialog(device = device, onDismiss = { showLimit = null }, onApply = { rate, dir -> vm.limit(device, rate, dir); showLimit = null }) }
    showBlock?.let { device -> BlockDialog(device = device, onDismiss = { showBlock = null }, onBlock = { dir -> vm.block(device, dir); showBlock = null }) }
}

@Composable
private fun HeaderRow(state: UiState, onScan: () -> Unit, onAdd: () -> Unit, scanning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NetBandMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("NetBand", color = NetBandColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
            Text(if (state.hasRoot) "Root shell ready" else "Scan-only mode", color = if (state.hasRoot) NetBandColors.Positive else NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
        }
        OutlinedIconButton(onClick = onAdd, contentDescription = "Add host") { Icon(Icons.Filled.Add, contentDescription = null, tint = NetBandColors.Text) }
        Spacer(Modifier.width(8.dp))
        PrimaryButton(if (scanning) "Scanning…" else "Scan", onScan, enabled = !scanning)
    }
}

@Composable
private fun ScanRangeBar(state: UiState, vm: NetBandViewModel, onScan: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.scanRange,
            onValueChange = vm::setScanRange,
            modifier = Modifier.weight(1f),
            label = { Text("Range (optional)") },
            placeholder = { Text("192.168.1.10-192.168.1.80") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp),
            shape = MaterialTheme.shapes.extraSmall
        )
        Spacer(Modifier.width(8.dp))
        OutlinedIconButton(onClick = onScan, contentDescription = "Scan range", enabled = !state.scanning) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = NetBandColors.Text)
        }
    }
}

@Composable
private fun KpiGrid(state: UiState) {
    val online = state.devices.count { it.online }
    val limited = state.devices.count { it.limitRate != null }
    val blocked = state.devices.count { it.blocked }
    val watched = state.devices.count { it.watched }
    Panel(Modifier.fillMaxWidth().padding(top = 10.dp), content = {
        Column {
            Row(Modifier.fillMaxWidth()) {
                StatCell("Online", online.toString(), "hosts", Modifier.weight(1f))
                VerticalRule()
                StatCell("Limited", limited.toString(), "hosts", Modifier.weight(1f))
            }
            HorizontalRule()
            Row(Modifier.fillMaxWidth()) {
                StatCell("Blocked", blocked.toString(), "hosts", Modifier.weight(1f))
                VerticalRule()
                StatCell("Watched", watched.toString(), "hosts", Modifier.weight(1f))
            }
        }
    })
}

@Composable
private fun FilterTabs(query: String, onQueryChange: (String) -> Unit, filter: HostFilter, onFilterChange: (HostFilter) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        label = { Text("Search ID, IP, MAC, hostname") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp),
        shape = MaterialTheme.shapes.extraSmall
    )
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HostFilter.entries.forEach { option ->
            val selected = option == filter
            Column(Modifier.clickable { onFilterChange(option) }.padding(vertical = 4.dp)) {
                Text(option.label, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.padding(top = 3.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Accent else Color.Transparent))
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onOpen: () -> Unit, onLimit: () -> Unit, onBlock: () -> Unit, onFree: () -> Unit, onWatch: () -> Unit) {
    val limitedOrBlocked = device.limitRate != null || device.blocked
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#" + device.id.toString().padStart(2, '0'), color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp, modifier = Modifier.width(34.dp))
                Text(device.ip, color = NetBandColors.Text, fontFamily = IbmPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!device.online) {
                    Spacer(Modifier.width(8.dp)); Text("offline", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 11.sp)
                }
            }
            Text(
                listOf(shortMac(device.mac), device.hostname.ifBlank { "no hostname" }).joinToString(" · "),
                color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 34.dp, top = 2.dp)
            )
            Text(deviceStatus(device), color = if (limitedOrBlocked) NetBandColors.Accent else NetBandColors.Muted, fontSize = 12.sp, fontFamily = IbmPlexMono,
                modifier = Modifier.padding(start = 34.dp, top = 2.dp), maxLines = 1)
        }
        IconAction(if (device.limitRate != null || device.blocked) Icons.Filled.CheckCircle else Icons.Filled.Speed, if (device.limitRate != null || device.blocked) "Remove limit/block" else "Limit bandwidth", if (device.limitRate != null || device.blocked) onFree else onLimit)
        IconAction(Icons.Filled.Block, if (device.blocked) "Change block rule" else "Block internet", onBlock)
        IconAction(if (device.watched) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (device.watched) "Remove from watch" else "Add to watch", onWatch)
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = description, tint = NetBandColors.Text, modifier = Modifier.size(18.dp))
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

@Composable
private fun EmptyHosts(query: String, onClear: () -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(top = 4.dp), content = {
        Text("No hosts match", color = NetBandColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = PublicSans)
        Text(if (query.isBlank()) "Scan the authorized network or add a host by IP." else "Clear the search or scan range to see all discovered hosts.", color = NetBandColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp), fontFamily = PublicSans)
        MonoButton("Clear filters", onClear, Modifier.padding(top = 12.dp))
    })
}

@Composable
private fun AddHostDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Add host", color = NetBandColors.Text, fontFamily = PublicSans) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP address") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
                OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC (optional)") }, placeholder = { Text("aa:bb:cc:dd:ee:ff") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(ip, mac) }) { Text("Add", fontFamily = PublicSans) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun DeviceDetailDialog(device: Device, state: UiState, onDismiss: () -> Unit, onLimit: () -> Unit, onBlock: () -> Unit, onFree: () -> Unit, onWatch: () -> Unit, onAnalyze: (Int) -> Unit) {
    var analysisSeconds by remember { mutableStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(device.ip, fontFamily = IbmPlexMono, color = NetBandColors.Text); Spacer(Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = NetBandColors.Text) } } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Host", "#" + device.id.toString().padStart(2, '0'))
                DetailRow("MAC", device.mac)
                DetailRow("Hostname", device.hostname.ifBlank { "—" })
                DetailRow("State", deviceStatus(device))
                DetailRow("Watch", if (device.watched) "on" else "off")
                if (device.limitRate != null) DetailRow("Rate", "${device.limitRate} · ${device.direction}")
                HorizontalRule()
                Text("Actions", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    MonoButton("Limit", onLimit, Modifier.weight(1f))
                    MonoButton("Block", onBlock, Modifier.weight(1f))
                    MonoButton("Free", onFree, Modifier.weight(1f))
                }
                MonoButton(if (device.watched) "Unwatch" else "Watch", onWatch, Modifier.fillMaxWidth())
                Text("Analyze over", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 30, 60).forEach { seconds ->
                        val selected = seconds == analysisSeconds
                        TextButton(onClick = { analysisSeconds = seconds }, modifier = Modifier.height(30.dp)) { Text("${seconds}s", color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = IbmPlexMono) }
                        if (selected) Box(Modifier.width(1.dp).height(16.dp).background(NetBandColors.Accent))
                    }
                }
                MonoButton(if (state.analyzing) "Measuring…" else "Analyze", { onAnalyze(analysisSeconds) }, Modifier.fillMaxWidth(), enabled = !state.analyzing)
                state.analysisResult?.let { result ->
                    Text(result, color = NetBandColors.Text, fontSize = 12.sp, fontFamily = IbmPlexMono, maxLines = 10)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.width(78.dp))
        Text(value, color = NetBandColors.Text, fontSize = 12.sp, fontFamily = IbmPlexMono, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LimitDialog(device: Device, onDismiss: () -> Unit, onApply: (String, Direction) -> Unit) {
    var rate by remember { mutableStateOf(device.limitRate ?: "512kbit") }
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Limit ${device.ip}", color = NetBandColors.Text, fontFamily = IbmPlexMono) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate") }, placeholder = { Text("512kbit · 2mbit · 10mbit") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
                DirectionSelector(direction = direction, onDirectionChange = { direction = it })
            }
        },
        confirmButton = { TextButton(onClick = { onApply(rate.trim(), direction) }) { Text("Apply limit", fontFamily = PublicSans) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun BlockDialog(device: Device, onDismiss: () -> Unit, onBlock: (Direction) -> Unit) {
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Block ${device.ip}", color = NetBandColors.Text, fontFamily = IbmPlexMono) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose whether to drop upload, download, or both directions.", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
                DirectionSelector(direction = direction, onDirectionChange = { direction = it })
            }
        },
        confirmButton = { TextButton(onClick = { onBlock(direction) }) { Text("Block", fontFamily = PublicSans, color = NetBandColors.Negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun DirectionSelector(direction: Direction, onDirectionChange: (Direction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Direction.entries.forEach { option ->
            val selected = option == direction
            Column(Modifier.clickable { onDirectionChange(option) }.padding(vertical = 2.dp)) {
                Text(relativeDirection(option).replaceFirstChar { it.uppercase() }, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp)
                Box(Modifier.padding(top = 3.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Accent else Color.Transparent))
            }
        }
    }
}
