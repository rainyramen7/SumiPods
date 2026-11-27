package com.sumipods.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log

class AirPodsBleScanner(
    private val context: Context,
    private val shouldProcessPacket: (rssi: Int) -> Boolean = { true },
    private val onAirPodsPacket: (AirPodsState) -> Unit,
    private val onStatusChange: (ConnectionStatus) -> Unit
) {
    private val isDebuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private var scanner: BluetoothLeScanner? = null
    @Volatile
    private var isScanning = false
    private var lastLogElapsedMs = 0L

    // Sliding window of recent startScan() monotonic timestamps to stay safely under
    // Android's 5-starts-per-30-seconds ScanManager kill limit.
    private val recentStartTimestamps = LongArray(MAX_STARTS_PER_WINDOW)
    private var recentStartCursor = 0

    private val proximityFilter: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setManufacturerData(
                AppleContinuityParser.APPLE_COMPANY_ID,
                byteArrayOf(PROXIMITY_PAIRING_TYPE_BYTE),
                byteArrayOf(EXACT_BYTE_MASK)
            )
            .build()
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            // Process batched results from the BT controller FIFO in order
            for (i in 0 until results.size) {
                handleResult(results[i])
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with code=$errorCode")
            isScanning = false
        }
    }

    private fun handleResult(result: ScanResult) {
        val rssi = result.rssi
        // Fast gate BEFORE extracting/parsing manufacturer bytes — zero allocations for rejected packets
        if (!shouldProcessPacket(rssi)) return

        val scanRecord = result.scanRecord ?: return
        val manufacturerData = scanRecord.getManufacturerSpecificData(
            AppleContinuityParser.APPLE_COMPANY_ID
        ) ?: return
        if (manufacturerData.size < MIN_PROXIMITY_BYTES) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val parsed = AppleContinuityParser.parse(
            manufacturerData = manufacturerData,
            rssi = rssi,
            timestampMillis = nowElapsed
        )

        if (parsed != null) {
            if (isDebuggable && nowElapsed - lastLogElapsedMs > 1_500L) {
                lastLogElapsedMs = nowElapsed
                Log.i(
                    TAG,
                    "AirPods packet: model=${parsed.model.displayName} (0x${parsed.rawModelId.toString(16)}) " +
                        "L=${parsed.left.percentage}% R=${parsed.right.percentage}% C=${parsed.caseBattery.percentage}% " +
                        "lidOpen=${parsed.isLidOpen} cnt=${parsed.lidOpenCounter} rssi=${parsed.rssi}"
                )
            }
            onAirPodsPacket(parsed)
        } else if (isDebuggable && (manufacturerData[0].toInt() and 0xFF) == 0x07) {
            if (nowElapsed - lastLogElapsedMs > 2_000L) {
                lastLogElapsedMs = nowElapsed
                Log.i(
                    TAG,
                    "Unparsed Apple 0x07 packet len=${manufacturerData.size} rssi=$rssi"
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

        // Sliding-window rate guard: allow at most 4 starts per 30s window
        val nowElapsed = SystemClock.elapsedRealtime()
        val oldestInWindow = recentStartTimestamps[recentStartCursor]
        if (oldestInWindow > 0L && (nowElapsed - oldestInWindow) < RATE_LIMIT_WINDOW_MS) {
            if (isDebuggable) {
                Log.w(TAG, "Skipping BLE scan start to stay under Android 5-starts/30s throttle")
            }
            return
        }

        val androidScanMode = when (powerMode) {
            ScanPowerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
            ScanPowerMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
        }

        // AirPods Continuity Proximity Pairing (0x07) is strictly legacy BLE 4.x on LE 1M PHY.
        // Using setLegacy(true) + PHY_LE_1M avoids wasting radio power scanning Extended/Coded PHYs,
        // and offloaded batching coalesces bursts inside the BT chip FIFO before waking the AP CPU.
        val reportDelayMs = if (adapter.isOffloadedScanBatchingSupported) {
            OFFLOADED_BATCH_DELAY_MS
        } else {
            0L
        }

        val settings = ScanSettings.Builder()
            .setScanMode(androidScanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(true)
            .setPhy(BluetoothDevice.PHY_LE_1M)
            .setReportDelay(reportDelayMs)
            .build()

        try {
            scanner = leScanner
            leScanner.startScan(proximityFilter, settings, scanCallback)
            isScanning = true
            recentStartTimestamps[recentStartCursor] = nowElapsed
            recentStartCursor = (recentStartCursor + 1) % MAX_STARTS_PER_WINDOW
            if (isDebuggable) {
                Log.i(TAG, "Started legacy 1M BLE scan (mode=$powerMode, batchMs=$reportDelayMs)")
            }
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
            scanner = null
        }
    }

    fun isCurrentlyScanning(): Boolean = isScanning

    companion object {
        private const val TAG = "SumiPodsBLE"
        private const val PROXIMITY_PAIRING_TYPE_BYTE: Byte = 0x07
        private const val EXACT_BYTE_MASK: Byte = 0xFF.toByte()
        private const val MIN_PROXIMITY_BYTES = 11
        private const val OFFLOADED_BATCH_DELAY_MS = 2_000L
        private const val MAX_STARTS_PER_WINDOW = 4
        private const val RATE_LIMIT_WINDOW_MS = 30_000L
    }
}
