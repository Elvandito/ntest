package com.elvan.netband.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.Device
import com.elvan.netband.Direction
import com.elvan.netband.NetBandViewModel
import com.elvan.netband.UiState
import androidx.compose.ui.graphics.painter.Painter

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
            .background(NetBandColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetBandMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("NetBand", color = NetBandColors.Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
                Text(if (state.hasRoot) "root shell ready" else "scan-only mode", color = if (state.hasRoot) NetBandColors.Amber else NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
            }
            OutlinedIconButton(onClick = { showAdd = true }, contentDescription = "Add host") {
                Icon(Icons.Filled.Add, contentDescription = null, tint = NetBandColors.Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton(if (state.scanning) "Scanning…" else "Scan", { vm.scan() }, enabled = !state.scanning)
        }

        StatusLine(formatConnection(state.netInfo?.iface, state.netInfo?.localIp, state.netInfo?.gatewayIp), Modifier.padding(top = 12.dp))
        if (!state.hasRoot) StatusLine("Root is required to limit or block; scan and manual add still work.", Modifier.padding(top = 2.dp))
        if (state.message.isNotBlank()) StatusLine(state.message, Modifier.padding(top = 2.dp))

        NetworkReadout(state)

        OutlinedTextField(
            value = state.scanRange,
            onValueChange = vm::setScanRange,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            label = { Text("Scan range (optional)") },
            placeholder = { Text("192.168.1.10-192.168.1.80") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp),
            shape = MaterialTheme.shapes.small
        )

        SearchAndFilters(query = query, onQueryChange = { query = it }, filter = filter, onFilterChange = { filter = it })

        SectionHeader("Hosts · ${filtered.size} of ${state.devices.size}") {
            TextButton(onClick = { vm.freeAll() }, modifier = Modifier.height(44.dp)) {
                Text("Free all", color = NetBandColors.Ink2, fontSize = 13.sp, fontFamily = PublicSans)
            }
        }

        if (filtered.isEmpty()) {
            EmptyHosts(query = query, onClear = { query = ""; filter = HostFilter.ALL }, onAdd = { showAdd = true })
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

        Spacer(Modifier.height(28.dp))
    }

    if (showAdd) AddHostDialog(onDismiss = { showAdd = false }, onAdd = { ip, mac -> vm.addHost(ip, mac); showAdd = false })
    selected?.let { device ->
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
    showLimit?.let { device -> LimitDialog(device = device, onDismiss = { showLimit = null }, onApply = { rate, dir -> vm.limit(device, rate, dir); showLimit = null }) }
    showBlock?.let { device -> BlockDialog(device = device, onDismiss = { showBlock = null }, onBlock = { dir -> vm.block(device, dir); showBlock = null }) }
}

@Composable
private fun NetworkReadout(state: UiState) {
    val online = state.devices.count { it.online }
    val limited = state.devices.count { it.limitRate != null }
    val blocked = state.devices.count { it.blocked }
    val watched = state.devices.count { it.watched }
    Panel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Readout("Hosts online", online.toString(), "of ${state.devices.size}", Modifier.weight(1.35f), NetBandColors.Cool)
            VerticalRule()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniReading("Limited", limited.toString(), NetBandColors.Amber)
                MiniReading("Blocked", blocked.toString(), NetBandColors.Bad)
                MiniReading("Watched", watched.toString(), NetBandColors.Muted)
            }
        }
    }
}

@Composable
private fun MiniReading(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = tint, fontFamily = IbmPlexMono, fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(38.dp))
        Text(label, color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp)
    }
}

@Composable
private fun SearchAndFilters(query: String, onQueryChange: (String) -> Unit, filter: HostFilter, onFilterChange: (HostFilter) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        label = { Text("Search host") },
        placeholder = { Text("IP, MAC, ID or hostname") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono, fontSize = 13.sp),
        shape = MaterialTheme.shapes.small
    )

    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HostFilter.entries.forEach { option ->
            val selected = option == filter
            Column(Modifier.widthIn(min = 52.dp).clickable { onFilterChange(option) }.padding(vertical = 5.dp)) {
                Text(option.label, color = if (selected) NetBandColors.Ink else NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.padding(top = 4.dp).height(1.dp).fillMaxWidth().background(if (selected) NetBandColors.Amber else Color.Transparent))
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onOpen: () -> Unit, onLimit: () -> Unit, onBlock: () -> Unit, onFree: () -> Unit, onWatch: () -> Unit) {
    val restricted = device.limitRate != null || device.blocked
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#" + device.id.toString().padStart(2, '0'), color = NetBandColors.Muted, fontFamily = IbmPlexMono, fontSize = 11.sp, modifier = Modifier.width(34.dp))
                Text(device.ip, color = NetBandColors.Ink, fontFamily = IbmPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOf(shortMac(device.mac), device.hostname.ifBlank { "no hostname" }).joinToString("  ·  "),
                color = NetBandColors.Muted, fontSize = 11.sp, fontFamily = PublicSans, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 34.dp, top = 3.dp)
            )
            Text(
                deviceStatus(device),
                color = statusColor(device), fontSize = 11.sp, fontFamily = PublicSans,
                modifier = Modifier.padding(start = 34.dp, top = 4.dp), maxLines = 1
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconAction(if (restricted) Icons.Filled.CheckCircle else Icons.Filled.Speed, if (restricted) "Remove limit or block" else "Limit bandwidth") {
                if (restricted) onFree() else onLimit()
            }
            IconAction(Icons.Filled.Block, if (device.blocked) "Change block rule" else "Block host") { onBlock() }
            IconAction(if (device.watched) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (device.watched) "Stop watching host" else "Watch host") { onWatch() }
        }
    }
}

private fun statusColor(device: Device): Color = when {
    device.blocked -> NetBandColors.Bad
    device.limitRate != null -> NetBandColors.Amber
    device.online -> NetBandColors.Cool
    else -> NetBandColors.Muted
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = description, tint = NetBandColors.Ink2, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun HorizontalRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Rule))
}

@Composable
private fun VerticalRule() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(NetBandColors.Rule2).padding(vertical = 4.dp))
}

@Composable
private fun EmptyHosts(query: String, onClear: () -> Unit, onAdd: () -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(top = 0.dp)) {
        Text("No hosts" + if (query.isNotBlank()) " match “$query”" else "", color = NetBandColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PublicSans)
        Text(if (query.isBlank()) "Scan this network or add a known address. Saved hosts stay on this phone until you clear them." else "Clear the search to see the full host list.", color = NetBandColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PublicSans, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (query.isBlank()) "Add host" else "Clear search", if (query.isBlank()) onAdd else onClear, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddHostDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Add host", color = NetBandColors.Ink, fontFamily = PublicSans) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP address") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
                OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC address (optional)") }, placeholder = { Text("aa:bb:cc:dd:ee:ff") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(ip, mac) }, modifier = Modifier.height(44.dp)) { Text("Add host", color = NetBandColors.Amber, fontFamily = PublicSans) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun DeviceDetailDialog(device: Device, state: UiState, onDismiss: () -> Unit, onLimit: () -> Unit, onBlock: () -> Unit, onFree: () -> Unit, onWatch: () -> Unit, onAnalyze: (Int) -> Unit) {
    var analysisSeconds by remember { mutableStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(device.ip, fontFamily = IbmPlexMono, color = NetBandColors.Ink); Spacer(Modifier.weight(1f)); IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, null, tint = NetBandColors.Ink) } } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Host", "#" + device.id.toString().padStart(2, '0'))
                DetailRow("MAC", device.mac)
                DetailRow("Hostname", device.hostname.ifBlank { "Not resolved" })
                DetailRow("State", deviceStatus(device))
                DetailRow("Watch", if (device.watched) "on" else "off")
                if (device.limitRate != null) DetailRow("Rate", "${device.limitRate} · ${relativeDirection(device.direction)}")
                HorizontalRule()
                Text("Controls", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoButton("Limit", onLimit, Modifier.weight(1f))
                    MonoButton("Block", onBlock, Modifier.weight(1f), destructive = true)
                    MonoButton("Free", onFree, Modifier.weight(1f))
                }
                MonoButton(if (device.watched) "Stop watching" else "Watch host", onWatch, Modifier.fillMaxWidth())
                Text("Analyze over", color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 30, 60).forEach { seconds ->
                        val selected = seconds == analysisSeconds
                        TextButton(onClick = { analysisSeconds = seconds }, modifier = Modifier.height(44.dp)) {
                            Text("${seconds}s", color = if (selected) NetBandColors.Amber else NetBandColors.Muted, fontFamily = IbmPlexMono)
                        }
                    }
                }
                MonoButton(if (state.analyzing) "Measuring…" else "Analyze $analysisSeconds seconds", { onAnalyze(analysisSeconds) }, Modifier.fillMaxWidth(), enabled = !state.analyzing)
                if (state.analysisTargetId == device.id) state.analysisResult?.let { result ->
                    Text(result, color = NetBandColors.Ink2, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = IbmPlexMono)
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
        Text(label, color = NetBandColors.Muted, fontSize = 12.sp, fontFamily = PublicSans, modifier = Modifier.width(80.dp))
        Text(value, color = NetBandColors.Ink, fontSize = 12.sp, fontFamily = IbmPlexMono, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LimitDialog(device: Device, onDismiss: () -> Unit, onApply: (String, Direction) -> Unit) {
    var rate by remember { mutableStateOf(device.limitRate ?: "512kbit") }
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Limit ${device.ip}", color = NetBandColors.Ink, fontFamily = IbmPlexMono) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate") }, placeholder = { Text("512kbit · 2mbit") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMono))
                DirectionSelector(direction = direction, onDirectionChange = { direction = it })
            }
        },
        confirmButton = { TextButton(onClick = { onApply(rate.trim(), direction) }, modifier = Modifier.height(44.dp)) { Text("Apply limit", color = NetBandColors.Amber, fontFamily = PublicSans) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun BlockDialog(device: Device, onDismiss: () -> Unit, onBlock: (Direction) -> Unit) {
    var direction by remember { mutableStateOf(device.direction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetBandColors.Raised,
        title = { Text("Block ${device.ip}", color = NetBandColors.Ink, fontFamily = IbmPlexMono) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Drop traffic for this host.", color = NetBandColors.Muted, fontFamily = PublicSans, fontSize = 13.sp)
                DirectionSelector(direction = direction, onDirectionChange = { direction = it })
            }
        },
        confirmButton = { TextButton(onClick = { onBlock(direction) }, modifier = Modifier.height(44.dp)) { Text("Block host", fontFamily = PublicSans, color = NetBandColors.Bad) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) { Text("Cancel", fontFamily = PublicSans) } }
    )
}

@Composable
private fun DirectionSelector(direction: Direction, onDirectionChange: (Direction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Direction.entries.forEach { option ->
            val selected = option == direction
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, if (selected) NetBandColors.Amber else NetBandColors.Rule2, RoundedCornerShape(6.dp))
                    .clickable { onDirectionChange(option) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(relativeDirection(option).replaceFirstChar { it.uppercase() }, color = if (selected) NetBandColors.Ink else NetBandColors.Muted, fontFamily = PublicSans, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
