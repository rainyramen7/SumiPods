package com.sumipods.app.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedAirPodsPolicyTest {

    private fun samplePacket(isLidOpen: Boolean = true, counter: Int = 1, rssi: Int = -52, ts: Long) =
        AirPodsState(
            model = AirPodsModel.AIRPODS_PRO_3,
            left = ComponentBattery(90),
            right = ComponentBattery(80),
            caseBattery = ComponentBattery(60),
            isLidOpen = isLidOpen,
            lidOpenCounter = counter,
            rssi = rssi,
            lastUpdatedMillis = ts
        )

    @Test
    fun rejectsAllNearbyStrangerPacketsWhenPairedAirPodsDisconnected() {
        val policy = ConnectedAirPodsPolicy()

        val decision = policy.evaluatePacket(
            packet = samplePacket(isLidOpen = true, rssi = -40, ts = 1_000L),
            rssiThreshold = -74,
            autoPopupEnabled = true,
            isAppInForeground = false
        )

        assertFalse(decision.acceptPacket)
        assertFalse(decision.triggerPopup)
    }

    @Test
    fun triggersPopupOnceOnFirstPacketAfterPairedAirPodsConnectInBackground() {
        val policy = ConnectedAirPodsPolicy()
        policy.onConnected(isAppInForeground = false)

        // First packet after connecting (even if lid already closed) triggers popup once
        val first = policy.evaluatePacket(
            packet = samplePacket(isLidOpen = false, counter = 1, ts = 2_000L),
            rssiThreshold = -74,
            autoPopupEnabled = true,
            isAppInForeground = false
        )
        assertTrue(first.acceptPacket)
        assertTrue(first.triggerPopup)

        // Immediate follow-up packet does not re-trigger popup
        val second = policy.evaluatePacket(
            packet = samplePacket(isLidOpen = true, counter = 2, ts = 3_000L),
            rssiThreshold = -74,
            autoPopupEnabled = true,
            isAppInForeground = false
        )
        assertTrue(second.acceptPacket)
        assertFalse(second.triggerPopup)
    }

    @Test
    fun suppressesPopupWhenAppIsAlreadyOpenInForeground() {
        val policy = ConnectedAirPodsPolicy()
        policy.onConnected(isAppInForeground = true)

        val decision = policy.evaluatePacket(
            packet = samplePacket(isLidOpen = true, counter = 1, ts = 5_000L),
            rssiThreshold = -74,
            autoPopupEnabled = true,
            isAppInForeground = true
        )
        assertTrue(decision.acceptPacket)
        assertFalse(decision.triggerPopup)
    }

    @Test
    fun disconnectImmediatelyBlocksSubsequentPackets() {
        val policy = ConnectedAirPodsPolicy()
        policy.onConnected(isAppInForeground = false)
        policy.onDisconnected()

        val afterDisconnect = policy.evaluatePacket(
            packet = samplePacket(isLidOpen = true, counter = 3, ts = 20_000L),
            rssiThreshold = -74,
            autoPopupEnabled = true,
            isAppInForeground = false
        )
        assertFalse(afterDisconnect.acceptPacket)
        assertFalse(afterDisconnect.triggerPopup)
    }
}
