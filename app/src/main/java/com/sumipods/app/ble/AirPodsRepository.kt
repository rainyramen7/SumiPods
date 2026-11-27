package com.sumipods.app.ble

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

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
    @Volatile
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

/**
 * Pure state-emission deduplicator that prevents volatile BLE timestamps and minor ±1-3 dBm RSSI
 * jitter from triggering redundant StateFlow emissions, UI recompositions, and Notification IPCs.
 */
object StateEmissionDeduplicator {
    const val FOREGROUND_RSSI_DELTA_THRESHOLD = 4
    const val FOREGROUND_RSSI_THROTTLE_MS = 2_000L

    fun shouldEmitState(
        current: AirPodsState,
        candidate: AirPodsState,
        isAppInForeground: Boolean,
        nowElapsedMs: Long,
        lastRssiEmitElapsedMs: Long,
        candidateDeviceName: String? = candidate.connectedDeviceName
    ): Boolean {
        val sameContent = current.model == candidate.model &&
            current.rawModelId == candidate.rawModelId &&
            current.connectedDeviceName == candidateDeviceName &&
            current.left == candidate.left &&
            current.right == candidate.right &&
            current.caseBattery == candidate.caseBattery &&
            current.isLidOpen == candidate.isLidOpen &&
            current.connectionStatus == candidate.connectionStatus &&
            current.isDemo == candidate.isDemo
        if (!sameContent) return true
        if (!isAppInForeground) return false
        return abs(candidate.rssi - current.rssi) >= FOREGROUND_RSSI_DELTA_THRESHOLD &&
            (nowElapsedMs - lastRssiEmitElapsedMs) >= FOREGROUND_RSSI_THROTTLE_MS
    }
}

class AirPodsRepository(
    context: Context,
    private val elapsedClockMs: () -> Long = SystemClock::elapsedRealtime
) {

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
        shouldProcessPacket = { rssi ->
            policy.isPairedConnected && rssi >= _preferences.value.proximityLevel.rssiThreshold
        },
        onAirPodsPacket = ::onLivePacket,
        onStatusChange = ::onScannerStatusChanged
    )

    private var lastLiveState: AirPodsState? = null
    @Volatile
    var lastLivePacketElapsedMs: Long = 0L
        private set
    private var lastRssiUiEmitElapsedMs: Long = 0L
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
                    if (current.connectedDeviceName == deviceName) current
                    else current.copy(connectedDeviceName = deviceName)
                } else {
                    if (current.connectedDeviceName == deviceName &&
                        current.connectionStatus == ConnectionStatus.SCANNING
                    ) {
                        current
                    } else {
                        current.copy(
                            connectedDeviceName = deviceName,
                            connectionStatus = ConnectionStatus.SCANNING
                        )
                    }
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
        lastLivePacketElapsedMs = 0L
        lastRssiUiEmitElapsedMs = 0L
        policy.onDisconnected()

        if (!_preferences.value.demoModeEnabled) {
            val disconnected = AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
            if (!_state.value.hasSameUiContent(disconnected)) {
                _state.value = disconnected
            }
        }
    }

    fun onLivePacket(packet: AirPodsState) {
        val currentPrefs = _preferences.value
        val nowElapsed = if (packet.lastUpdatedMillis > 0L) packet.lastUpdatedMillis else elapsedClockMs()

        // Strict Option 1 gate: reject all BLE packets when user's paired AirPods are disconnected
        val decision = policy.evaluatePacket(
            packet = packet,
            rssiThreshold = currentPrefs.proximityLevel.rssiThreshold,
            autoPopupEnabled = currentPrefs.autoPopupEnabled,
            isAppInForeground = isAppInForeground,
            nowMillis = nowElapsed
        )
        if (!decision.acceptPacket) return

        // Prefer closer/stronger signal if multiple AirPods are nearby while user's are connected
        val prev = lastLiveState
        if (prev != null &&
            (nowElapsed - lastLivePacketElapsedMs) < 3_500L &&
            prev.rawModelId != packet.rawModelId &&
            packet.rssi < prev.rssi - 8
        ) {
            return
        }

        lastLivePacketElapsedMs = nowElapsed
        val currentState = _state.value
        val needsEmit = !currentPrefs.demoModeEnabled && StateEmissionDeduplicator.shouldEmitState(
            current = currentState,
            candidate = packet,
            isAppInForeground = isAppInForeground,
            nowElapsedMs = nowElapsed,
            lastRssiEmitElapsedMs = lastRssiUiEmitElapsedMs,
            candidateDeviceName = connectedName
        )

        if (needsEmit || decision.triggerPopup || prev == null || !prev.hasSameUiContent(packet)) {
            val enriched = if (packet.connectedDeviceName == connectedName) {
                packet
            } else {
                packet.copy(connectedDeviceName = connectedName)
            }
            lastLiveState = enriched

            if (needsEmit) {
                lastRssiUiEmitElapsedMs = nowElapsed
                _state.value = enriched
            }
            if (decision.triggerPopup) {
                _popupEvents.tryEmit(enriched)
            }
        }
    }

    fun onScannerStatusChanged(status: ConnectionStatus) {
        if (_preferences.value.demoModeEnabled) return
        if (status == ConnectionStatus.NO_PERMISSION || status == ConnectionStatus.BLUETOOTH_OFF) {
            _state.update { if (it.connectionStatus == status) it else it.copy(connectionStatus = status) }
            return
        }
        if (!policy.isPairedConnected) {
            _state.update {
                if (it.connectionStatus == ConnectionStatus.DISCONNECTED) it
                else it.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
            }
            return
        }
        val current = _state.value
        if (status == ConnectionStatus.SCANNING && current.connectionStatus == ConnectionStatus.CONNECTED) {
            return
        }
        if (current.connectionStatus != status) {
            _state.update { it.copy(connectionStatus = status) }
        }
    }

    /**
     * Checks whether the live reading is stale and returns the remaining milliseconds until staleness
     * (or 0L if already stale / not connected), enabling event-driven one-shot scheduling instead of polling.
     */
    fun checkStaleState(nowElapsedMs: Long = elapsedClockMs()): Long {
        if (_preferences.value.demoModeEnabled) return 0L
        if (!policy.isPairedConnected) {
            val disconnected = AirPodsState(connectionStatus = ConnectionStatus.DISCONNECTED)
            if (!_state.value.hasSameUiContent(disconnected)) {
                _state.value = disconnected
            }
            return 0L
        }
        val current = _state.value
        if (current.connectionStatus != ConnectionStatus.CONNECTED || lastLivePacketElapsedMs <= 0L) {
            return 0L
        }
        val elapsed = nowElapsedMs - lastLivePacketElapsedMs
        return if (elapsed > STALE_TIMEOUT_MILLIS) {
            _state.value = AirPodsState(
                connectedDeviceName = connectedName,
                connectionStatus = ConnectionStatus.SCANNING
            )
            0L
        } else {
            STALE_TIMEOUT_MILLIS - elapsed
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
                (elapsedClockMs() - lastLivePacketElapsedMs) <= STALE_TIMEOUT_MILLIS
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
        val current = _preferences.value
        val updated = transform(current)
        if (updated == current) return
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
        const val STALE_TIMEOUT_MILLIS = 45_000L
    }
}
