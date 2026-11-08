package com.elvan.netband

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            NetBandTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NetBandColors.Ground) {
                    NetBandApp(vm = vm, onQuit = { finish() })
                }
            }
        }
    }
}

private data class Destination(val id: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("hosts", "Hosts", Icons.Filled.Devices),
    Destination("monitor", "Monitor", Icons.Filled.Monitor),
    Destination("watch", "Watch", Icons.Filled.Visibility),
    Destination("settings", "Settings", Icons.Filled.Settings)
)

@Composable
private fun NetBandApp(vm: NetBandViewModel, onQuit: () -> Unit) {
    var selectedTab by remember { mutableStateOf("hosts") }

    Box(Modifier.fillMaxSize().background(NetBandColors.Ground)) {
        Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
            when (selectedTab) {
                "hosts" -> HostsScreen(vm = vm)
                "monitor" -> MonitorScreen(vm = vm)
                "watch" -> WatchScreen(vm = vm)
                "settings" -> SettingsScreen(vm = vm, onQuit = onQuit)
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(NetBandColors.Paper3)
                .padding(6.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                destinations.forEach { destination ->
                    BottomNavItem(
                        destination = destination,
                        selected = destination.id == selectedTab,
                        onSelect = { selectedTab = destination.id },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) NetBandColors.BlueSoft else Color.Transparent)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                destination.icon,
                contentDescription = destination.label,
                tint = if (selected) NetBandColors.Blue else NetBandColors.Ink2,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                destination.label,
                color = if (selected) NetBandColors.Blue else NetBandColors.Ink2,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = PublicSans,
                maxLines = 1
            )
        }
    }
}
