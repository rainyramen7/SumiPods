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
    fun isAirPodsDevice(device: BluetoothDevice?, cachedName: String? = safeName(device)): Boolean {
        if (device == null) return false
        if (cachedName?.contains("AirPods", ignoreCase = true) == true) return true

        return runCatching {
            device.uuids?.any { it.uuid == APPLE_AAP_UUID } == true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun safeName(device: BluetoothDevice?): String? =
        runCatching { device?.name }.getOrNull()
}

/**
 * Queries Android's A2DP and HFP Bluetooth profiles to determine whether any paired AirPods
 * are currently connected to this phone.
 *
 * Uses synchronous `getProfileConnectionState()` short-circuiting so zero Binder service
 * bindings occur when no Bluetooth audio device is connected, and always binds with
 * `applicationContext` to prevent Activity ServiceConnection leaks.
 */
object AirPodsConnectionProbe {

    private val AUDIO_PROFILES = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

    @SuppressLint("MissingPermission")
    suspend fun findConnectedAirPods(context: Context): BluetoothDevice? {
        if (!PermissionHelper.hasBluetoothPermissions(context)) return null
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return null
        if (!adapter.isEnabled) return null

        for (profile in AUDIO_PROFILES) {
            val state = runCatching { adapter.getProfileConnectionState(profile) }
                .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
            if (state != BluetoothProfile.STATE_CONNECTED) continue

            val connected = getConnectedDevices(appContext, adapter, profile)
            val match = connected.firstOrNull { AirPodsDeviceMatcher.isAirPodsDevice(it) }
            if (match != null) return match
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun isAnyAudioProfileConnected(context: Context, profileToCheck: Int): Boolean {
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return false
        if (!adapter.isEnabled) return false
        return runCatching { adapter.getProfileConnectionState(profileToCheck) }
            .getOrDefault(BluetoothProfile.STATE_DISCONNECTED) == BluetoothProfile.STATE_CONNECTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun getConnectedDevices(
        appContext: Context,
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
        val bound = runCatching { adapter.getProfileProxy(appContext, listener, profileType) }
            .getOrDefault(false)
        if (!bound) return emptyList()
        return withTimeoutOrNull(1_500L) { deferred.await() } ?: emptyList()
    }
}

/**
 * System broadcast receiver that starts BLE battery monitoring ONLY when your paired AirPods
 * connect to this phone via A2DP/HFP, and shuts it down completely when both audio profiles
 * disconnect (0% idle drain, 0% stranger AirPods popups, 0% non-audio BT wakeups).
 */
class AirPodsConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!PermissionHelper.hasBluetoothPermissions(context)) return

        val state = intent.getIntExtra(
            BluetoothProfile.EXTRA_STATE,
            BluetoothProfile.STATE_DISCONNECTED
        )
        if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_DISCONNECTED) {
            return
        }

        val device = extractDevice(intent)
        val deviceName = AirPodsDeviceMatcher.safeName(device)
        if (!AirPodsDeviceMatcher.isAirPodsDevice(device, cachedName = deviceName)) return

        val app = context.applicationContext as? SumiPodsApplication ?: return
        val repository = app.repository
        val address = device?.address

        when (action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Paired AirPods profile connected ($action): $deviceName")
                    handleConnected(context, repository, deviceName, address)
                } else {
                    // Only tear down monitoring if the companion audio profile is also disconnected
                    val otherProfile = if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                        BluetoothProfile.HEADSET
                    } else {
                        BluetoothProfile.A2DP
                    }
                    if (AirPodsConnectionProbe.isAnyAudioProfileConnected(context, otherProfile)) {
                        Log.d(TAG, "Ignoring partial profile disconnect; companion profile still active")
                        return
                    }
                    Log.i(TAG, "Paired AirPods disconnected ($action): $deviceName")
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
