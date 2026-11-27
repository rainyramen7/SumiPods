package com.sumipods.app.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure notification model used with `distinctUntilChanged()` so `NotificationManager.notify()`
 * is only invoked when user-visible title or battery summary actually changes.
 */
data class MonitorNotificationSnapshot(
    val title: String,
    val text: String
)

/**
 * Connected-only foreground service:
 * - Started ONLY when your paired AirPods connect to this phone.
 * - Runs a single continuous legacy 1M PHY `SCAN_MODE_LOW_POWER` BLE scan with offloaded batching.
 * - Deduplicates notification IPCs (dispatched off the Main Thread) and caches PendingIntent.
 * - Uses event-driven one-shot staleness timing instead of perpetual polling.
 * - Pauses BLE scanning after 45s of continuous screen-off (keeping last battery reading visible)
 *   and resumes on screen-on, cutting pocket-listening BLE radio power dramatically.
 * - Automatically stops itself when AirPods disconnect or Bluetooth turns off.
 */
class AirPodsMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: AirPodsRepository
    private lateinit var overlayController: AirPodsOverlayController
    private lateinit var notificationManager: NotificationManager
    private lateinit var openAppPendingIntent: PendingIntent

    private var isObserving = false
    private var isSystemReceiverRegistered = false
    private var isScreenOffPaused = false
    private var activePowerMode = ScanPowerMode.LOW_POWER
    private var staleJob: Job? = null
    private var screenOffPauseJob: Job? = null
    private var lastPostedSnapshot: MonitorNotificationSnapshot? = null

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> scheduleScreenOffScanPause()
                Intent.ACTION_SCREEN_ON -> resumeScanAfterScreenOn()
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (btState == BluetoothAdapter.STATE_TURNING_OFF || btState == BluetoothAdapter.STATE_OFF) {
                        repository.onPairedAirPodsDisconnected()
                        repository.scanner.stopScanning()
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repository = (application as SumiPodsApplication).repository
        overlayController = AirPodsOverlayController(this, repository)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ensureNotificationChannels()
        registerSystemEventsReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialSnapshot = buildNotificationSnapshot(repository.state.value)
        val initialNotification = buildMonitorNotification(initialSnapshot)
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
            lastPostedSnapshot = initialSnapshot
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to enter foreground mode", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PermissionHelper.hasBluetoothPermissions(this) || !repository.isPairedAirPodsConnected) {
            stopSelf()
            return START_NOT_STICKY
        }

        val modeName = intent?.getStringExtra(EXTRA_POWER_MODE)
        activePowerMode = modeName?.let { runCatching { ScanPowerMode.valueOf(it) }.getOrNull() }
            ?: ScanPowerMode.LOW_POWER

        observeStateAndPopupsOnce()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenInteractive = powerManager?.isInteractive != false
        if (isScreenInteractive) {
            isScreenOffPaused = false
            repository.scanner.startScanning(activePowerMode)
        } else {
            // Started while screen is already off (e.g., user put AirPods in pocket):
            // scan briefly to fetch initial battery reading, then pause after debounce.
            repository.scanner.startScanning(activePowerMode)
            scheduleScreenOffScanPause()
        }

        return START_NOT_STICKY
    }

    private fun observeStateAndPopupsOnce() {
        if (isObserving) return
        isObserving = true

        // Collector 1: Deduplicated notification updates dispatched on Dispatchers.IO (0 Main-Thread IPCs)
        serviceScope.launch {
            repository.state
                .map { state ->
                    if (!repository.isPairedAirPodsConnected) null
                    else buildNotificationSnapshot(state)
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (snapshot == null) {
                        stopSelf()
                        return@collect
                    }
                    if (snapshot == lastPostedSnapshot) return@collect
                    lastPostedSnapshot = snapshot
                    val notification = buildMonitorNotification(snapshot)
                    withContext(Dispatchers.IO) {
                        notificationManager.notify(NOTIFICATION_ID_MONITOR, notification)
                    }
                }
        }

        // Collector 2: Event-driven one-shot staleness check (0 polling wakeups when idle or screen off)
        serviceScope.launch {
            repository.state
                .map { it.connectionStatus }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == ConnectionStatus.CONNECTED && !isScreenOffPaused) {
                        scheduleOneShotStaleCheck()
                    } else {
                        staleJob?.cancel()
                    }
                }
        }

        // Collector 3: Case-open / connect popup events
        serviceScope.launch {
            repository.popupEvents.collect { popupState ->
                if (!repository.preferences.value.autoPopupEnabled) return@collect
                if (repository.isAppInForeground) return@collect
                if (!repository.isPairedAirPodsConnected) return@collect
                showCaseOpenPopup(popupState)
            }
        }
    }

    private fun scheduleOneShotStaleCheck() {
        staleJob?.cancel()
        staleJob = serviceScope.launch {
            while (!isScreenOffPaused && repository.isPairedAirPodsConnected) {
                val remaining = repository.checkStaleState()
                if (remaining <= 0L) break
                delay(remaining + 500L)
            }
        }
    }

    private fun scheduleScreenOffScanPause() {
        screenOffPauseJob?.cancel()
        screenOffPauseJob = serviceScope.launch {
            delay(SCREEN_OFF_SCAN_PAUSE_DELAY_MS)
            if (repository.isPairedAirPodsConnected && !repository.isAppInForeground) {
                isScreenOffPaused = true
                staleJob?.cancel()
                repository.scanner.stopScanning()
            }
        }
    }

    private fun resumeScanAfterScreenOn() {
        screenOffPauseJob?.cancel()
        if (!isScreenOffPaused) return
        isScreenOffPaused = false
        if (repository.isPairedAirPodsConnected && PermissionHelper.hasBluetoothPermissions(this)) {
            repository.scanner.startScanning(activePowerMode)
            if (repository.state.value.connectionStatus == ConnectionStatus.CONNECTED) {
                scheduleOneShotStaleCheck()
            }
        }
    }

    private fun registerSystemEventsReceiver() {
        if (isSystemReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            systemEventsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isSystemReceiverRegistered = true
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

        serviceScope.launch(Dispatchers.IO) {
            notificationManager.notify(NOTIFICATION_ID_POPUP, alert)
        }
    }

    private fun buildMonitorNotification(snapshot: MonitorNotificationSnapshot): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
            .setSmallIcon(R.drawable.ic_notification_pods)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent)
            .build()

    private fun ensureNotificationChannels() {
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
        notificationManager.createNotificationChannel(monitorChannel)
        notificationManager.createNotificationChannel(popupChannel)
    }

    override fun onDestroy() {
        isRunning = false
        staleJob?.cancel()
        screenOffPauseJob?.cancel()
        if (isSystemReceiverRegistered) {
            runCatching { unregisterReceiver(systemEventsReceiver) }
            isSystemReceiverRegistered = false
        }
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
        private const val EXTRA_POWER_MODE = "extra_power_mode"
        private const val SCREEN_OFF_SCAN_PAUSE_DELAY_MS = 45_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun buildNotificationSnapshot(state: AirPodsState): MonitorNotificationSnapshot {
            val title = state.connectedDeviceName ?: state.model.displayName
            val text = if (state.hasAnyBattery) {
                formatBatterySummary(state)
            } else {
                "Connected · reading battery..."
            }
            return MonitorNotificationSnapshot(title = title, text = text)
        }

        fun formatBatterySummary(state: AirPodsState): String {
            val sb = StringBuilder(36)
            state.left.percentage?.let { pct ->
                sb.append("L ").append(pct).append('%')
                if (state.left.isCharging) sb.append('⚡')
            }
            state.right.percentage?.let { pct ->
                if (sb.isNotEmpty()) sb.append("  ·  ")
                sb.append("R ").append(pct).append('%')
                if (state.right.isCharging) sb.append('⚡')
            }
            state.caseBattery.percentage?.let { pct ->
                if (sb.isNotEmpty()) sb.append("  ·  ")
                sb.append("Case ").append(pct).append('%')
                if (state.caseBattery.isCharging) sb.append('⚡')
            }
            return if (sb.isEmpty()) "Connected · reading battery..." else sb.toString()
        }

        fun start(context: Context, powerMode: ScanPowerMode = ScanPowerMode.LOW_POWER) {
            if (!PermissionHelper.hasBluetoothPermissions(context)) return
            if (isRunning) return
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
            if (!isRunning) return
            val intent = Intent(context, AirPodsMonitorService::class.java)
            try {
                context.stopService(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
