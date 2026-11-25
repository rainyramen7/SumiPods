package com.sumipods.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumipods.app.ble.AirPodsMonitorService
import com.sumipods.app.ble.AirPodsRepository
import com.sumipods.app.ble.PermissionHelper
import com.sumipods.app.ble.ScanPowerMode
import com.sumipods.app.ui.screens.MainScreen
import com.sumipods.app.ui.screens.SettingsBottomSheet
import com.sumipods.app.ui.theme.SumiPodsTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: AirPodsRepository

    private var hasBluetoothPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionsAndScanning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = (application as SumiPodsApplication).repository

        setContent {
            SumiPodsTheme {
                val state by repository.state.collectAsStateWithLifecycle()
                val preferences by repository.preferences.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        state = state,
                        preferences = preferences,
                        hasBluetoothPermission = hasBluetoothPermission,
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                PermissionHelper.requiredRuntimePermissions().toTypedArray()
                            )
                        },
                        onOpenOverlaySettings = ::openOverlaySettings,
                        onOpenSettings = { showSettings = true },
                        onSelectDemoPreset = { preset ->
                            repository.setDemoPreset(preset)
                        }
                    )
                }

                if (showSettings) {
                    SettingsBottomSheet(
                        preferences = preferences,
                        hasOverlayPermission = hasOverlayPermission,
                        onDismiss = { showSettings = false },
                        onToggleAutoPopup = { repository.setAutoPopupEnabled(it) },
                        onToggleBackgroundMonitor = { enabled ->
                            repository.setBackgroundMonitoringEnabled(enabled)
                            if (enabled && hasBluetoothPermission) {
                                AirPodsMonitorService.start(
                                    this@MainActivity,
                                    powerMode = ScanPowerMode.LOW_POWER
                                )
                            } else {
                                AirPodsMonitorService.stop(this@MainActivity)
                            }
                        },
                        onToggleJapaneseLabels = { repository.setShowJapaneseLabels(it) },
                        onSelectProximity = { repository.setProximityLevel(it) },
                        onToggleDemoMode = { repository.setDemoModeEnabled(it) },
                        onOpenOverlaySettings = ::openOverlaySettings
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        repository.isAppInForeground = true
        refreshPermissionsAndScanning()
    }

    override fun onPause() {
        super.onPause()
        repository.isAppInForeground = false
        if (!repository.preferences.value.backgroundMonitoringEnabled) {
            repository.scanner.stopScanning()
        }
    }

    private fun refreshPermissionsAndScanning() {
        hasBluetoothPermission = PermissionHelper.hasBluetoothPermissions(this)
        hasOverlayPermission = PermissionHelper.canDrawOverlays(this)

        if (hasBluetoothPermission) {
            // Single steady low-power BLE scan — never restarted across foreground/background switches
            repository.scanner.startScanning(powerMode = ScanPowerMode.LOW_POWER)
            if (repository.preferences.value.backgroundMonitoringEnabled) {
                AirPodsMonitorService.start(this, powerMode = ScanPowerMode.LOW_POWER)
            }
        } else {
            repository.scanner.stopScanning()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
