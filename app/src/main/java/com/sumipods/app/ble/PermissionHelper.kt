package com.sumipods.app.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    private val BLUETOOTH_PERMISSIONS_S_PLUS: List<String> = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val BLUETOOTH_PERMISSIONS_LEGACY: List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val RUNTIME_PERMISSIONS_TIRAMISU_PLUS: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val RUNTIME_PERMISSIONS_S_TO_S2: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val RUNTIME_PERMISSIONS_LEGACY: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    fun requiredBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_PERMISSIONS_S_PLUS
        } else {
            BLUETOOTH_PERMISSIONS_LEGACY
        }

    fun requiredRuntimePermissionsArray(): Array<String> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> RUNTIME_PERMISSIONS_TIRAMISU_PLUS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> RUNTIME_PERMISSIONS_S_TO_S2
            else -> RUNTIME_PERMISSIONS_LEGACY
        }

    fun hasBluetoothPermissions(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
}
