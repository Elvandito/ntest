package com.elvan.netband

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.netband.ui.HostsScreen
import com.elvan.netband.ui.MonitorScreen
import com.elvan.netband.ui.NetBandColors
import com.elvan.netband.ui.NetBandTheme
import com.elvan.netband.ui.PublicSans
import com.elvan.netband.ui.SettingsScreen
import com.elvan.netband.ui.WatchScreen

class MainActivity : ComponentActivity() {
    private val vm: NetBandViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetBandTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NetBandColors.Ground) {
                    NetBandApp(vm = vm, onQuit = { finish() })
                }
            }
        }
    }
}

@Composable
private fun NetBandApp(vm: NetBandViewModel, onQuit: () -> Unit) {
    var selectedTab by remember { mutableStateOf("hosts") }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                "hosts" -> HostsScreen(vm = vm)
                "monitor" -> MonitorScreen(vm = vm)
                "watch" -> WatchScreen(vm = vm)
                "settings" -> SettingsScreen(vm = vm, onQuit = onQuit)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(NetBandColors.Surface)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(NetBandColors.Border))
            Row(Modifier.fillMaxSize()) {
                DestinationIcon("hosts", selectedTab, Icons.Filled.Devices, "Hosts") { selectedTab = it }
                DestinationIcon("monitor", selectedTab, Icons.Filled.Monitor, "Monitor") { selectedTab = it }
                DestinationIcon("watch", selectedTab, Icons.Filled.Visibility, "Watch") { selectedTab = it }
                DestinationIcon("settings", selectedTab, Icons.Filled.Settings, "Settings") { selectedTab = it }
            }
        }
    }
}

@Composable
private fun RowScope.DestinationIcon(
    id: String,
    selectedTab: String,
    icon: ImageVector,
    label: String,
    onSelect: (String) -> Unit
) {
    val selected = id == selectedTab
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onSelect(id) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(0.5f).height(1.dp).background(if (selected) NetBandColors.Accent else Color.Transparent))
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = if (selected) NetBandColors.Text else NetBandColors.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = if (selected) NetBandColors.Text else NetBandColors.Muted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontFamily = PublicSans)
        }
    }
}
