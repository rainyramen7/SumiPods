package com.sumipods.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sumipods.app.SumiPodsApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

object AirPodsDeviceMatcher {
    private val APPLE_AAP_UUID: UUID = UUID.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

    @SuppressLint("MissingPermission")
    fun isAirPodsDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val nameMatches = runCatching {
            device.name?.contains("AirPods", ignoreCase = true) == true
        }.getOrDefault(false)
        if (nameMatches) return true

        val hasAppleAapUuid = runCatching {
            device.uuids?.any { it.uuid == APPLE_AAP_UUID } == true
        }.getOrDefault(false)
        return hasAppleAapUuid
    }

    @SuppressLint("MissingPermission")
    fun safeName(device: BluetoothDevice?): String? =
        runCatching { device?.name }.getOrNull()
}

/**
 * Queries Android's A2DP and HFP Bluetooth profiles to determine whether any paired AirPods
 * are currently connected to this phone.
 */
object AirPodsConnectionProbe {

    @SuppressLint("MissingPermission")
    suspend fun findConnectedAirPods(context: Context): BluetoothDevice? {
        if (!PermissionHelper.hasBluetoothPermissions(context)) return null
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return null
        if (!adapter.isEnabled) return null

        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val connected = getConnectedDevices(context, adapter, profile)
            val match = connected.firstOrNull { AirPodsDeviceMatcher.isAirPodsDevice(it) }
            if (match != null) return match
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun getConnectedDevices(
        context: Context,
        adapter: BluetoothAdapter,
        profileType: Int
    ): List<BluetoothDevice> {
        val deferred = CompletableDeferred<List<BluetoothDevice>>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val list = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                runCatching { adapter.closeProfileProxy(profile, proxy) }
                if (!deferred.isCompleted) deferred.complete(list)
            }

            override fun onServiceDisconnected(profile: Int) {
                if (!deferred.isCompleted) deferred.complete(emptyList())
            }
        }
        val bound = runCatching { adapter.getProfileProxy(context, listener, profileType) }
            .getOrDefault(false)
        if (!bound) return emptyList()
        return withTimeoutOrNull(1500L) { deferred.await() } ?: emptyList()
    }
}

/**
 * System broadcast receiver that starts BLE battery monitoring ONLY when your paired AirPods
 * connect to this phone, and shuts it down completely when they disconnect (0% idle drain,
 * 0% stranger AirPods popups).
 */
class AirPodsConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!PermissionHelper.hasBluetoothPermissions(context)) return

        val device = extractDevice(intent)
        if (!AirPodsDeviceMatcher.isAirPodsDevice(device)) return

        val app = context.applicationContext as? SumiPodsApplication ?: return
        val repository = app.repository
        val deviceName = AirPodsDeviceMatcher.safeName(device)
        val address = device?.address

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.i(TAG, "Paired AirPods ACL connected: $deviceName")
                handleConnected(context, repository, deviceName, address)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.i(TAG, "Paired AirPods ACL disconnected: $deviceName")
                handleDisconnected(context, repository, address)
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED
                )
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Paired AirPods profile connected ($action): $deviceName")
                    handleConnected(context, repository, deviceName, address)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Paired AirPods profile disconnected ($action): $deviceName")
                    handleDisconnected(context, repository, address)
                }
            }
        }
    }

    private fun handleConnected(
        context: Context,
        repository: AirPodsRepository,
        deviceName: String?,
        address: String?
    ) {
        repository.onPairedAirPodsConnected(deviceName, address)
        if (repository.preferences.value.backgroundMonitoringEnabled) {
            AirPodsMonitorService.start(context, powerMode = ScanPowerMode.LOW_POWER)
        } else if (repository.isAppInForeground) {
            repository.scanner.startScanning(ScanPowerMode.LOW_POWER)
        }
    }

    private fun handleDisconnected(
        context: Context,
        repository: AirPodsRepository,
        address: String?
    ) {
        repository.onPairedAirPodsDisconnected(address)
        repository.scanner.stopScanning()
        AirPodsMonitorService.stop(context)
    }

    @Suppress("DEPRECATION")
    private fun extractDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "SumiPodsConn"
    }
}
