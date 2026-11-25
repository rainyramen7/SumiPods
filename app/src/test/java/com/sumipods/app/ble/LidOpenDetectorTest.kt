package com.sumipods.app.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidOpenDetectorTest {

    private fun packet(isLidOpen: Boolean, counter: Int, rssi: Int = -55, ts: Long) =
        AirPodsState(
            model = AirPodsModel.AIRPODS_PRO_2_USBC,
            left = ComponentBattery(80),
            right = ComponentBattery(80),
            caseBattery = ComponentBattery(60),
            isLidOpen = isLidOpen,
            lidOpenCounter = counter,
            rssi = rssi,
            lastUpdatedMillis = ts
        )

    @Test
    fun triggersOnClosedToOpenTransitionAndRespectsCooldown() {
        val detector = LidOpenDetector(cooldownMillis = 10_000L, silenceResetMillis = 12_000L)

        // Initial closed packet should not trigger
        assertFalse(detector.onPacket(packet(isLidOpen = false, counter = 1, ts = 1_000L), -74))

        // Lid flips open -> triggers!
        assertTrue(detector.onPacket(packet(isLidOpen = true, counter = 2, ts = 2_000L), -74))

        // Continuous BLE broadcasts while open -> must NOT spam
        assertFalse(detector.onPacket(packet(isLidOpen = true, counter = 2, ts = 2_500L), -74))
        assertFalse(detector.onPacket(packet(isLidOpen = true, counter = 2, ts = 5_000L), -74))

        // Quick re-close and re-open within 10s cooldown -> suppressed
        assertFalse(detector.onPacket(packet(isLidOpen = false, counter = 2, ts = 6_000L), -74))
        assertFalse(detector.onPacket(packet(isLidOpen = true, counter = 3, ts = 7_000L), -74))

        // After cooldown expires, lid close -> open triggers again!
        assertFalse(detector.onPacket(packet(isLidOpen = false, counter = 3, ts = 12_500L), -74))
        assertTrue(detector.onPacket(packet(isLidOpen = true, counter = 4, ts = 13_000L), -74))
    }

    @Test
    fun ignoresWeakRssiSignals() {
        val detector = LidOpenDetector()
        assertFalse(detector.onPacket(packet(isLidOpen = true, counter = 1, rssi = -92, ts = 1_000L), -74))
    }

    @Test
    fun triggersOnFreshOpenPacketAfterSilence() {
        val detector = LidOpenDetector(cooldownMillis = 10_000L, silenceResetMillis = 12_000L)
        assertTrue(detector.onPacket(packet(isLidOpen = true, counter = 1, ts = 1_000L), -74))

        // 20 seconds later, user opens case again out of pocket
        assertTrue(detector.onPacket(packet(isLidOpen = true, counter = 1, ts = 21_000L), -74))
    }
}
