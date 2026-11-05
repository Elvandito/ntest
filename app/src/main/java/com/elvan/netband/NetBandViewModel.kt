package com.elvan.netband

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val hasRoot: Boolean = false,
    val netInfo: NetInfo? = null,
    val gatewayMac: String = "",
    val devices: List<Device> = emptyList(),
    val scanning: Boolean = false,
    val message: String = ""
)

class NetBandViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var deviceMap: MutableMap<String, Device> = mutableMapOf()
    private var spoofer: ArpSpoofer? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = RootShell.isRootAvailable()
            val ni = NetworkInfo.detect(getApplication())
            deviceMap = DeviceStore.load(getApplication())
            _state.value = _state.value.copy(
                hasRoot = hasRoot,
                netInfo = ni,
                devices = deviceMap.values.sortedBy { it.id },
                message = if (ni == null) "Tidak bisa deteksi jaringan (tidak ada koneksi aktif)" else ""
            )
            if (hasRoot) Limiter.resolveBinaries()
            if (ni != null) resolveGateway(ni)
        }
    }

    private fun resolveGateway(ni: NetInfo) {
        val arp = Scanner.readArpTable(ni.iface)
        val mac = arp[ni.gatewayIp] ?: ""
        _state.value = _state.value.copy(gatewayMac = mac)
        if (mac.isNotEmpty() && spoofer == null) {
            spoofer = ArpSpoofer(
                getApplication(), ni.iface,
                ni.localIp, NetworkInfo.localMac(ni.iface),
                ni.gatewayIp, mac
            )
        }
    }

    fun scan() = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        _state.value = _state.value.copy(scanning = true, message = "Scanning...")

        val ips = Scanner.buildHostList(ni.localIp, ni.prefixLength)
        if (ips.isEmpty()) {
            _state.value = _state.value.copy(scanning = false, message = "Subnet terlalu besar, scan dilewati")
            return@launch
        }

        Scanner.pingSweep(ips)
        val neigh = Scanner.readArpTable(ni.iface)

        var nextId = (deviceMap.values.maxOfOrNull { it.id } ?: 0) + 1
        for ((ip, mac) in neigh) {
            if (ip == ni.localIp || ip == ni.gatewayIp) continue
            val existing = deviceMap[mac]
            if (existing != null) {
                existing.ip = ip
                existing.online = true
            } else {
                deviceMap[mac] = Device(id = nextId++, ip = ip, mac = mac, online = true)
            }
        }

        val seenMacs = neigh.values.toSet()
        deviceMap.values.forEach { d ->
            if (d.mac !in seenMacs && d.limitRate == null && !d.blocked) d.online = false
        }

        DeviceStore.save(getApplication(), deviceMap)
        resolveGateway(ni)
        _state.value = _state.value.copy(
            scanning = false,
            devices = deviceMap.values.sortedBy { it.id },
            message = "Ditemukan ${neigh.size} host"
        )
    }

    fun limit(device: Device, rate: String, direction: Direction) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        if (!_state.value.hasRoot) { setMessage("Butuh root untuk limit"); return@launch }
        val ok = Limiter.limit(ni.iface, device, rate, direction)
        if (ok) spoofer?.addTarget(device.ip, device.mac)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (ok) "Limited ${device.ip} -> $rate" else "Gagal limit ${device.ip}")
    }

    fun block(device: Device, direction: Direction) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        if (!_state.value.hasRoot) { setMessage("Butuh root untuk block"); return@launch }
        val ok = Limiter.block(ni.iface, device, direction)
        if (ok) spoofer?.addTarget(device.ip, device.mac)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (ok) "Blocked ${device.ip}" else "Gagal block ${device.ip}")
    }

    fun free(device: Device) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        Limiter.unlimit(ni.iface, device)
        spoofer?.removeTarget(device.ip)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage("Freed ${device.ip}")
    }

    private fun refreshDevices() {
        _state.value = _state.value.copy(devices = deviceMap.values.sortedBy { it.id })
    }

    private fun setMessage(m: String) {
        _state.value = _state.value.copy(message = m)
    }

    override fun onCleared() {
        super.onCleared()
        val ni = _state.value.netInfo
        if (ni != null) Limiter.flush(ni.iface, deviceMap.values)
        spoofer?.stopAll()
    }
}
