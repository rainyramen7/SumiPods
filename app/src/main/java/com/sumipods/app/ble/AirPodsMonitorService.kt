package com.sumipods.app.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sumipods.app.MainActivity
import com.sumipods.app.R
import com.sumipods.app.SumiPodsApplication
import com.sumipods.app.popup.AirPodsOverlayController
import com.sumipods.app.popup.AirPodsPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Connected-only foreground service:
 * - Started ONLY when your paired AirPods connect to this phone.
 * - Runs a single continuous SCAN_MODE_LOW_POWER BLE scan to read your live battery levels.
 * - Automatically stops itself and removes its notification as soon as your AirPods disconnect,
 *   ensuring zero idle battery drain and zero stranger AirPods notifications.
 */
class AirPodsMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: AirPodsRepository
    private lateinit var overlayController: AirPodsOverlayController
    private var staleJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as SumiPodsApplication).repository
        overlayController = AirPodsOverlayController(this, repository)
        ensureNotificationChannels()
        observeStateAndPopups()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PermissionHelper.hasBluetoothPermissions(this) || !repository.isPairedAirPodsConnected) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildMonitorNotification(repository.state.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_MONITOR,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID_MONITOR, initialNotification)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to enter foreground mode", t)
            stopSelf()
            return START_NOT_STICKY
        }

        val modeName = intent?.getStringExtra(EXTRA_POWER_MODE)
        val powerMode = modeName?.let { runCatching { ScanPowerMode.valueOf(it) }.getOrNull() }
            ?: ScanPowerMode.LOW_POWER
        repository.scanner.startScanning(powerMode)
        startStaleCheckLoop()
        return START_NOT_STICKY
    }

    private fun observeStateAndPopups() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        serviceScope.launch {
            repository.state.collectLatest { state ->
                if (!repository.isPairedAirPodsConnected) {
                    stopSelf()
                    return@collectLatest
                }
                notificationManager.notify(
                    NOTIFICATION_ID_MONITOR,
                    buildMonitorNotification(state)
                )
            }
        }

        serviceScope.launch {
            repository.popupEvents.collect { popupState ->
                if (!repository.preferences.value.autoPopupEnabled) return@collect
                if (repository.isAppInForeground) return@collect
                if (!repository.isPairedAirPodsConnected) return@collect
                showCaseOpenPopup(popupState)
            }
        }
    }

    private fun startStaleCheckLoop() {
        staleJob?.cancel()
        staleJob = serviceScope.launch {
            while (isActive) {
                delay(20_000L)
                repository.checkStaleState()
            }
        }
    }

    private fun showCaseOpenPopup(state: AirPodsState) {
        if (overlayController.show()) {
            return
        }

        val popupIntent = AirPodsPopupActivity.createIntent(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_POPUP,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = formatBatterySummary(state)
        val alert = NotificationCompat.Builder(this, CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_pods)
            .setContentTitle("${state.model.displayName} Connected")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(8_000L)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_POPUP, alert)
    }

    private fun buildMonitorNotification(state: AirPodsState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = state.connectedDeviceName ?: state.model.displayName
        val text = if (state.hasAnyBattery) {
            formatBatterySummary(state)
        } else {
            "Connected · reading battery..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
            .setSmallIcon(R.drawable.ic_notification_pods)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun formatBatterySummary(state: AirPodsState): String {
        val parts = buildList {
            state.left.percentage?.let { pct ->
                add("L $pct%${if (state.left.isCharging) "⚡" else ""}")
            }
            state.right.percentage?.let { pct ->
                add("R $pct%${if (state.right.isCharging) "⚡" else ""}")
            }
            state.caseBattery.percentage?.let { pct ->
                add("Case $pct%${if (state.caseBattery.isCharging) "⚡" else ""}")
            }
        }
        return if (parts.isEmpty()) "Connected · reading battery..." else parts.joinToString("  ·  ")
    }

    private fun ensureNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val monitorChannel = NotificationChannel(
            CHANNEL_ID_MONITOR,
            "AirPods Battery Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Displays your connected AirPods battery levels while in use."
            setShowBadge(false)
        }
        val popupChannel = NotificationChannel(
            CHANNEL_ID_POPUP,
            "Case Open Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fallback alert when your AirPods connect without overlay permission."
        }
        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(popupChannel)
    }

    override fun onDestroy() {
        staleJob?.cancel()
        overlayController.dismiss()
        if (!repository.isPairedAirPodsConnected || !repository.isAppInForeground) {
            repository.scanner.stopScanning()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AirPodsMonitorService"
        private const val CHANNEL_ID_MONITOR = "sumipods_monitor"
        private const val CHANNEL_ID_POPUP = "sumipods_popup_alert"
        private const val NOTIFICATION_ID_MONITOR = 1001
        private const val NOTIFICATION_ID_POPUP = 1002
        private const val REQUEST_CODE_POPUP = 2001
        private const val ACTION_STOP = "com.sumipods.app.ACTION_STOP_MONITOR"
        private const val EXTRA_POWER_MODE = "extra_power_mode"

        fun start(context: Context, powerMode: ScanPowerMode = ScanPowerMode.LOW_POWER) {
            if (!PermissionHelper.hasBluetoothPermissions(context)) return
            val intent = Intent(context, AirPodsMonitorService::class.java).apply {
                putExtra(EXTRA_POWER_MODE, powerMode.name)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to start foreground service", t)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AirPodsMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
