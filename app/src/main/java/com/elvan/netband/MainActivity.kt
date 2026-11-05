package com.elvan.netband

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: NetBandViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NetBandScreen(vm)
            }
        }
    }
}

@Composable
fun NetBandScreen(vm: NetBandViewModel) {
    val state by vm.state.collectAsState()
    var rateDialogDevice by remember { mutableStateOf<Device?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("NetBand") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(12.dp)
        ) {
            Text("Root: ${if (state.hasRoot) "ya" else "tidak (scan saja)"}")
            val ni = state.netInfo
            if (ni != null) {
                Text("Iface: ${ni.iface}   Local: ${ni.localIp}")
                Text("Gateway: ${ni.gatewayIp} (${state.gatewayMac.ifEmpty { "?" }})")
            } else {
                Text("Jaringan belum terdeteksi")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.scan() }, enabled = !state.scanning) {
                Text(if (state.scanning) "Scanning..." else "Scan network")
            }
            if (state.message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(state.devices) { d ->
                    DeviceRow(
                        device = d,
                        onLimit = { rateDialogDevice = d },
                        onBlock = { vm.block(d, Direction.BOTH) },
                        onFree = { vm.free(d) }
                    )
                }
            }
        }
    }

    rateDialogDevice?.let { d ->
        RateDialog(
            device = d,
            onDismiss = { rateDialogDevice = null },
            onConfirm = { rate, dir ->
                vm.limit(d, rate, dir)
                rateDialogDevice = null
            }
        )
    }
}

@Composable
fun DeviceRow(device: Device, onLimit: () -> Unit, onBlock: () -> Unit, onFree: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("#${device.id}  ${device.ip}  ${device.hostname}")
            Text(device.mac, style = MaterialTheme.typography.bodySmall)
            val status = when {
                device.blocked -> "Blocked"
                device.limitRate != null -> "Limited ${device.limitRate} (${device.direction})"
                !device.online -> "Offline"
                else -> "Free"
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onLimit) { Text("Limit") }
                TextButton(onClick = onBlock) { Text("Block") }
                TextButton(onClick = onFree) { Text("Free") }
            }
        }
    }
}

@Composable
fun RateDialog(device: Device, onDismiss: () -> Unit, onConfirm: (String, Direction) -> Unit) {
    var rate by remember { mutableStateOf("512kbit") }
    var dir by remember { mutableStateOf(Direction.BOTH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit ${device.ip}") },
        text = {
            Column {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Rate (contoh: 512kbit, 2mbit)") }
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf(Direction.UP, Direction.DOWN, Direction.BOTH).forEach { option ->
                        FilterChip(
                            selected = dir == option,
                            onClick = { dir = option },
                            label = { Text(option.name) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rate, dir) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
