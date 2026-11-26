package com.sumipods.app.ble

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure policy engine enforcing Option 1 (Connected AirPods Only):
 * - Completely ignores all BLE packets unless the user's paired AirPods are actively connected.
 * - Triggers an auto-popup on the first valid BLE battery packet after connection, or on a
 *   subsequent case-lid open event while connected (with anti-spam cooldown).
 */
class ConnectedAirPodsPolicy(
    private val lidOpenDetector: LidOpenDetector = LidOpenDetector(),
    private val connectPopupCooldownMillis: Long = 10_000L
) {
    var isPairedConnected: Boolean = false
        private set
    private var pendingConnectPopup: Boolean = false
    private var lastPopupEmittedMillis: Long = 0L

    fun onConnected(isAppInForeground: Boolean) {
        val wasConnected = isPairedConnected
        isPairedConnected = true
        if (!wasConnected) {
            lidOpenDetector.reset()
            pendingConnectPopup = !isAppInForeground
        }
    }

    fun onDisconnected() {
        isPairedConnected = false
        pendingConnectPopup = false
        lidOpenDetector.reset()
    }

    fun evaluatePacket(
        packet: AirPodsState,
        rssiThreshold: Int,
        autoPopupEnabled: Boolean,
        isAppInForeground: Boolean,
        nowMillis: Long = packet.lastUpdatedMillis
    ): PacketDecision {
        if (!isPairedConnected) {
            return PacketDecision(acceptPacket = false, triggerPopup = false)
        }
        if (packet.rssi < rssiThreshold) {
            return PacketDecision(acceptPacket = false, triggerPopup = false)
        }

        val lidEvent = lidOpenDetector.onPacket(packet, rssiThreshold, nowMillis)
        val withinCooldown = lastPopupEmittedMillis > 0L &&
            (nowMillis - lastPopupEmittedMillis) < connectPopupCooldownMillis

        val shouldPopup = !isAppInForeground &&
            autoPopupEnabled &&
            !withinCooldown &&
            (pendingConnectPopup || lidEvent)

        if (shouldPopup) {
            pendingConnectPopup = false
            lastPopupEmittedMillis = nowMillis
        } else if (isAppInForeground) {
            pendingConnectPopup = false
        }

        return PacketDecision(acceptPacket = true, triggerPopup = shouldPopup)
    }

    data class PacketDecision(
        val acceptPacket: Boolean,
        val triggerPopup: Boolean
    )
}

class AirPodsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val policy = ConnectedAirPodsPolicy()

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _state = MutableStateFlow(
        if (_preferences.value.demoModeEnabled) {
            AirPodsState.demo(_preferences.value.demoPreset)
        } else {
            AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
        }
    )
    val state: StateFlow<AirPodsState> = _state.asStateFlow()

    private val _popupEvents = MutableSharedFlow<AirPodsState>(extraBufferCapacity = 4)
    val popupEvents: SharedFlow<AirPodsState> = _popupEvents.asSharedFlow()

    val scanner: AirPodsBleScanner = AirPodsBleScanner(
        context = appContext,
        onAirPodsPacket = ::onLivePacket,
        onStatusChange = ::onScannerStatusChanged
    )

    private var lastLiveState: AirPodsState? = null
    private var connectedAddress: String? = null
    private var connectedName: String? = null

    @Volatile
    var isAppInForeground: Boolean = false

    val isPairedAirPodsConnected: Boolean
        get() = policy.isPairedConnected

    fun onPairedAirPodsConnected(deviceName: String?, address: String?) {
        connectedName = deviceName
        connectedAddress = address
        policy.onConnected(isAppInForeground = isAppInForeground)

        if (!_preferences.value.demoModeEnabled) {
            _state.update { current ->
                if (current.hasAnyBattery && current.connectionStatus == ConnectionStatus.CONNECTED) {
                    current.copy(connectedDeviceName = deviceName)
                } else {
                    current.copy(
                        connectedDeviceName = deviceName,
                        connectionStatus = ConnectionStatus.SCANNING
                    )
                }
            }
        }
    }

    fun onPairedAirPodsDisconnected(address: String? = null) {
        if (address != null && connectedAddress != null && address != connectedAddress) {
            return
        }
        connectedAddress = null
        connectedName = null
        lastLiveState = null
        policy.onDisconnected()

        if (!_preferences.value.demoModeEnabled) {
            _state.value = AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
        }
    }

    fun onLivePacket(packet: AirPodsState) {
        val currentPrefs = _preferences.value

        // Strict Option 1 gate: reject all BLE packets when user's paired AirPods are disconnected
        val decision = policy.evaluatePacket(
            packet = packet,
            rssiThreshold = currentPrefs.proximityLevel.rssiThreshold,
            autoPopupEnabled = currentPrefs.autoPopupEnabled,
            isAppInForeground = isAppInForeground
        )
        if (!decision.acceptPacket) return

        // Prefer closer/stronger signal if multiple AirPods are nearby while user's are connected
        val prev = lastLiveState
        if (prev != null &&
            (packet.lastUpdatedMillis - prev.lastUpdatedMillis) < 3_500L &&
            prev.rawModelId != packet.rawModelId &&
            packet.rssi < prev.rssi - 8
        ) {
            return
        }

        val enriched = packet.copy(connectedDeviceName = connectedName)
        lastLiveState = enriched
        if (!currentPrefs.demoModeEnabled) {
            _state.value = enriched
        }

        if (decision.triggerPopup) {
            _popupEvents.tryEmit(enriched)
        }
    }

    fun onScannerStatusChanged(status: ConnectionStatus) {
        if (_preferences.value.demoModeEnabled) return
        if (status == ConnectionStatus.NO_PERMISSION || status == ConnectionStatus.BLUETOOTH_OFF) {
            _state.update { it.copy(connectionStatus = status) }
            return
        }
        if (!policy.isPairedConnected) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
            return
        }
        val current = _state.value
        if (status == ConnectionStatus.SCANNING && current.connectionStatus == ConnectionStatus.CONNECTED) {
            return
        }
        _state.update { it.copy(connectionStatus = status) }
    }

    fun checkStaleState(nowMillis: Long = System.currentTimeMillis()) {
        if (_preferences.value.demoModeEnabled) return
        if (!policy.isPairedConnected) {
            _state.value = AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
            return
        }
        val current = _state.value
        if (current.connectionStatus == ConnectionStatus.CONNECTED &&
            current.lastUpdatedMillis > 0L &&
            (nowMillis - current.lastUpdatedMillis) > STALE_TIMEOUT_MILLIS
        ) {
            _state.value = AirPodsState(
                connectedDeviceName = connectedName,
                connectionStatus = ConnectionStatus.SCANNING
            )
        }
    }

    fun setAutoPopupEnabled(enabled: Boolean) {
        updatePrefs { it.copy(autoPopupEnabled = enabled) }
    }

    fun setShowJapaneseLabels(enabled: Boolean) {
        updatePrefs { it.copy(showJapaneseLabels = enabled) }
    }

    fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        updatePrefs { it.copy(backgroundMonitoringEnabled = enabled) }
    }

    fun setProximityLevel(level: ProximityLevel) {
        updatePrefs { it.copy(proximityLevel = level) }
    }

    fun setDemoModeEnabled(enabled: Boolean) {
        updatePrefs { it.copy(demoModeEnabled = enabled) }
        if (enabled) {
            _state.value = AirPodsState.demo(_preferences.value.demoPreset)
        } else {
            val live = lastLiveState
            _state.value = if (policy.isPairedConnected &&
                live != null &&
                (System.currentTimeMillis() - live.lastUpdatedMillis) <= STALE_TIMEOUT_MILLIS
            ) {
                live
            } else if (policy.isPairedConnected) {
                AirPodsState(
                    connectedDeviceName = connectedName,
                    connectionStatus = ConnectionStatus.SCANNING
                )
            } else {
                AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
            }
        }
    }

    fun setDemoPreset(preset: DemoPreset) {
        updatePrefs { it.copy(demoPreset = preset, demoModeEnabled = true) }
        _state.value = AirPodsState.demo(preset)
    }

    fun currentPopupState(): AirPodsState {
        val current = _state.value
        return if (current.hasAnyBattery) {
            current.copy(isLidOpen = true)
        } else {
            AirPodsState.demo(DemoPreset.CHARGING).copy(isLidOpen = true)
        }
    }

    private fun updatePrefs(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        prefs.edit()
            .putBoolean(KEY_AUTO_POPUP, updated.autoPopupEnabled)
            .putBoolean(KEY_SHOW_JP, updated.showJapaneseLabels)
            .putBoolean(KEY_BG_MONITOR, updated.backgroundMonitoringEnabled)
            .putString(KEY_PROXIMITY, updated.proximityLevel.name)
            .putBoolean(KEY_DEMO_MODE, updated.demoModeEnabled)
            .putString(KEY_DEMO_PRESET, updated.demoPreset.name)
            .apply()
    }

    private fun loadPreferences(): AppPreferences {
        val proximity = prefs.getString(KEY_PROXIMITY, ProximityLevel.MEDIUM.name)
            ?.let { runCatching { ProximityLevel.valueOf(it) }.getOrNull() }
            ?: ProximityLevel.MEDIUM
        val demoPreset = prefs.getString(KEY_DEMO_PRESET, DemoPreset.IN_USE.name)
            ?.let { runCatching { DemoPreset.valueOf(it) }.getOrNull() }
            ?: DemoPreset.IN_USE
        return AppPreferences(
            autoPopupEnabled = prefs.getBoolean(KEY_AUTO_POPUP, true),
            showJapaneseLabels = prefs.getBoolean(KEY_SHOW_JP, true),
            backgroundMonitoringEnabled = prefs.getBoolean(KEY_BG_MONITOR, true),
            proximityLevel = proximity,
            demoModeEnabled = prefs.getBoolean(KEY_DEMO_MODE, false),
            demoPreset = demoPreset
        )
    }

    companion object {
        private const val PREFS_NAME = "sumipods_prefs"
        private const val KEY_AUTO_POPUP = "auto_popup"
        private const val KEY_SHOW_JP = "show_jp"
        private const val KEY_BG_MONITOR = "bg_monitor"
        private const val KEY_PROXIMITY = "proximity"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_DEMO_PRESET = "demo_preset"
        private const val STALE_TIMEOUT_MILLIS = 45_000L
    }
}
