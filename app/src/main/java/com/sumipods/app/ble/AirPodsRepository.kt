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

class AirPodsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lidOpenDetector = LidOpenDetector()

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _state = MutableStateFlow(
        if (_preferences.value.demoModeEnabled) {
            AirPodsState.demo(_preferences.value.demoPreset)
        } else {
            AirPodsState(connectionStatus = ConnectionStatus.SCANNING)
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

    @Volatile
    var isAppInForeground: Boolean = false

    fun onLivePacket(packet: AirPodsState) {
        val currentPrefs = _preferences.value

        // Main screen accepts packets down to -95 dBm so your AirPods are never missed on a desk.
        if (packet.rssi < DISPLAY_MIN_RSSI) return

        // Prefer closer/Pro packets if multiple AirPods are nearby simultaneously
        val prev = lastLiveState
        if (prev != null &&
            (packet.lastUpdatedMillis - prev.lastUpdatedMillis) < 3_500L &&
            prev.rawModelId != packet.rawModelId &&
            packet.rssi < prev.rssi - 8
        ) {
            return
        }

        lastLiveState = packet
        if (!currentPrefs.demoModeEnabled) {
            _state.value = packet
        }

        // Always keep LidOpenDetector state in sync, but only emit popup when app is not open
        val isLidOpenEvent = lidOpenDetector.onPacket(
            packet,
            currentPrefs.proximityLevel.rssiThreshold
        )
        if (!isAppInForeground && currentPrefs.autoPopupEnabled && isLidOpenEvent) {
            _popupEvents.tryEmit(packet)
        }
    }

    fun onScannerStatusChanged(status: ConnectionStatus) {
        if (_preferences.value.demoModeEnabled) return
        val current = _state.value
        if (status == ConnectionStatus.SCANNING && current.connectionStatus == ConnectionStatus.CONNECTED) {
            return
        }
        _state.update { it.copy(connectionStatus = status) }
    }

    fun checkStaleState(nowMillis: Long = System.currentTimeMillis()) {
        if (_preferences.value.demoModeEnabled) return
        val current = _state.value
        if (current.connectionStatus == ConnectionStatus.CONNECTED &&
            current.lastUpdatedMillis > 0L &&
            (nowMillis - current.lastUpdatedMillis) > STALE_TIMEOUT_MILLIS
        ) {
            _state.value = AirPodsState(connectionStatus = ConnectionStatus.SCANNING)
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
            _state.value = if (live != null &&
                (System.currentTimeMillis() - live.lastUpdatedMillis) <= STALE_TIMEOUT_MILLIS
            ) {
                live
            } else {
                AirPodsState(connectionStatus = ConnectionStatus.SCANNING)
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
        private const val DISPLAY_MIN_RSSI = -95
    }
}
