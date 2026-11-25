package com.sumipods.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log

class AirPodsBleScanner(
    private val context: Context,
    private val onAirPodsPacket: (AirPodsState) -> Unit,
    private val onStatusChange: (ConnectionStatus) -> Unit
) {
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var currentPowerMode: ScanPowerMode? = null
    private var lastLogMillis = 0L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with code=$errorCode")
            isScanning = false
        }
    }

    private fun handleResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val manufacturerData = scanRecord.getManufacturerSpecificData(
            AppleContinuityParser.APPLE_COMPANY_ID
        ) ?: return

        val parsed = AppleContinuityParser.parse(
            manufacturerData = manufacturerData,
            rssi = result.rssi
        )

        val now = System.currentTimeMillis()
        if (parsed != null) {
            if (now - lastLogMillis > 1500L) {
                lastLogMillis = now
                Log.i(
                    TAG,
                    "AirPods packet: model=${parsed.model.displayName} (0x${parsed.rawModelId.toString(16)}) " +
                        "L=${parsed.left.percentage}% R=${parsed.right.percentage}% C=${parsed.caseBattery.percentage}% " +
                        "lidOpen=${parsed.isLidOpen} cnt=${parsed.lidOpenCounter} rssi=${parsed.rssi} " +
                        "hex=${manufacturerData.take(12).joinToString("") { "%02X".format(it) }}"
                )
            }
            onAirPodsPacket(parsed)
        } else if (manufacturerData.isNotEmpty() && (manufacturerData[0].toInt() and 0xFF) == 0x07) {
            if (now - lastLogMillis > 2000L) {
                lastLogMillis = now
                Log.i(
                    TAG,
                    "Unparsed Apple 0x07 packet len=${manufacturerData.size} rssi=${result.rssi} " +
                        "hex=${manufacturerData.take(16).joinToString("") { "%02X".format(it) }}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(powerMode: ScanPowerMode = ScanPowerMode.LOW_POWER) {
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            onStatusChange(ConnectionStatus.NO_PERMISSION)
            return
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChange(ConnectionStatus.BLUETOOTH_OFF)
            return
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            onStatusChange(ConnectionStatus.BLUETOOTH_OFF)
            return
        }

        // Never stop/restart an already active scan — avoids Android's 5-scans-per-30s kill limit
        if (isScanning) return

        val filter = ScanFilter.Builder()
            .setManufacturerData(
                AppleContinuityParser.APPLE_COMPANY_ID,
                byteArrayOf()
            )
            .build()

        val androidScanMode = when (powerMode) {
            ScanPowerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
            ScanPowerMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
        }

        val settings = ScanSettings.Builder()
            .setScanMode(androidScanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setReportDelay(0L)
            .build()

        try {
            scanner = leScanner
            leScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            currentPowerMode = powerMode
            Log.i(TAG, "Started continuous Apple BLE scan (mode=$powerMode)")
            onStatusChange(ConnectionStatus.SCANNING)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing Bluetooth scan permission", se)
            isScanning = false
            onStatusChange(ConnectionStatus.NO_PERMISSION)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start BLE scan", t)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping BLE scan", t)
        } finally {
            isScanning = false
            currentPowerMode = null
            scanner = null
        }
    }

    companion object {
        private const val TAG = "SumiPodsBLE"
    }
}
