package com.sumipods.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEmissionDeduplicatorTest {

    private fun packet(
        leftPct: Int = 80,
        rightPct: Int = 80,
        casePct: Int = 60,
        rssi: Int = -52,
        ts: Long = 1_000L,
        isLidOpen: Boolean = false,
        name: String? = "Ganesh's AirPods Pro"
    ) = AirPodsState(
        model = AirPodsModel.AIRPODS_PRO_3,
        rawModelId = AirPodsModel.AIRPODS_PRO_3.modelId,
        connectedDeviceName = name,
        left = ComponentBattery(leftPct),
        right = ComponentBattery(rightPct),
        caseBattery = ComponentBattery(casePct),
        isLidOpen = isLidOpen,
        rssi = rssi,
        lastUpdatedMillis = ts,
        connectionStatus = ConnectionStatus.CONNECTED
    )

    @Test
    fun suppressesTimestampAndMinorRssiChurnInBackground() {
        val initial = packet(rssi = -52, ts = 1_000L)
        var emitted = 0

        // Simulate 360 BLE packets over 10 minutes with unchanged battery and ±3 dBm RSSI jitter
        for (i in 1..360) {
            val jitter = (i % 5) - 2 // -2..+2 dBm
            val next = packet(rssi = -52 + jitter, ts = 1_000L + i * 1_500L)
            if (StateEmissionDeduplicator.shouldEmitState(
                    current = initial,
                    candidate = next,
                    isAppInForeground = false,
                    nowElapsedMs = next.lastUpdatedMillis,
                    lastRssiEmitElapsedMs = 1_000L
                )
            ) {
                emitted++
            }
        }

        assertEquals(0, emitted)
    }

    @Test
    fun emitsImmediatelyWhenBatteryOrLidChanges() {
        val current = packet(leftPct = 80, isLidOpen = false, ts = 1_000L)
        val batteryDrop = packet(leftPct = 70, isLidOpen = false, ts = 1_500L)
        val lidOpen = packet(leftPct = 80, isLidOpen = true, ts = 1_500L)

        assertTrue(
            StateEmissionDeduplicator.shouldEmitState(
                current = current,
                candidate = batteryDrop,
                isAppInForeground = false,
                nowElapsedMs = 1_500L,
                lastRssiEmitElapsedMs = 1_000L
            )
        )
        assertTrue(
            StateEmissionDeduplicator.shouldEmitState(
                current = current,
                candidate = lidOpen,
                isAppInForeground = false,
                nowElapsedMs = 1_500L,
                lastRssiEmitElapsedMs = 1_000L
            )
        )
    }

    @Test
    fun throttlesForegroundRssiUpdatesToSignificantChanges() {
        val current = packet(rssi = -52, ts = 1_000L)

        // Small ±2 dBm change in foreground -> suppressed
        assertFalse(
            StateEmissionDeduplicator.shouldEmitState(
                current = current,
                candidate = packet(rssi = -54, ts = 5_000L),
                isAppInForeground = true,
                nowElapsedMs = 5_000L,
                lastRssiEmitElapsedMs = 1_000L
            )
        )

        // Large ≥4 dBm change too soon (< 2s) -> suppressed
        assertFalse(
            StateEmissionDeduplicator.shouldEmitState(
                current = current,
                candidate = packet(rssi = -60, ts = 2_000L),
                isAppInForeground = true,
                nowElapsedMs = 2_000L,
                lastRssiEmitElapsedMs = 1_000L
            )
        )

        // Large ≥4 dBm change after ≥2s -> emitted
        assertTrue(
            StateEmissionDeduplicator.shouldEmitState(
                current = current,
                candidate = packet(rssi = -60, ts = 3_500L),
                isAppInForeground = true,
                nowElapsedMs = 3_500L,
                lastRssiEmitElapsedMs = 1_000L
            )
        )
    }

    @Test
    fun notificationSnapshotDeduplicatesIdenticalBatterySummaries() {
        val a = AirPodsMonitorService.buildNotificationSnapshot(packet(rssi = -50, ts = 1_000L))
        val b = AirPodsMonitorService.buildNotificationSnapshot(packet(rssi = -62, ts = 9_000L))
        assertEquals(a, b)
        assertEquals("Ganesh's AirPods Pro", a.title)
        assertEquals("L 80%  ·  R 80%  ·  Case 60%", a.text)
    }
}
