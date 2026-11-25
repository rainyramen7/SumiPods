package com.sumipods.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleContinuityParserTest {

    @Test
    fun parsesAirPodsPro3StandardOrientation() {
        val packet = AppleContinuityParser.buildTestPacket(
            model = AirPodsModel.AIRPODS_PRO_3,
            leftPct = 90,
            rightPct = 80,
            casePct = 60,
            leftCharging = false,
            rightCharging = true,
            caseCharging = true,
            leftInEar = true,
            rightInEar = false,
            isLidOpen = true,
            lidCounter = 5,
            flipped = false
        )

        val state = AppleContinuityParser.parse(packet, rssi = -55, timestampMillis = 1000L)
        assertNotNull(state)
        state!!
        assertEquals(AirPodsModel.AIRPODS_PRO_3, state.model)
        assertEquals(0x2720, state.rawModelId)
        assertEquals(90, state.left.percentage)
        assertEquals(80, state.right.percentage)
        assertEquals(60, state.caseBattery.percentage)
        assertFalse(state.left.isCharging)
        assertTrue(state.right.isCharging)
        assertTrue(state.caseBattery.isCharging)
        assertTrue(state.left.isInEar)
        assertFalse(state.right.isInEar)
        assertTrue(state.isLidOpen)
        assertEquals(5, state.lidOpenCounter)
        assertEquals(-55, state.rssi)
    }

    @Test
    fun parsesAirPodsPro2LightningFlippedOrientation() {
        val packet = AppleContinuityParser.buildTestPacket(
            model = AirPodsModel.AIRPODS_PRO_2_LIGHTNING,
            leftPct = 40,
            rightPct = 100,
            casePct = 70,
            leftCharging = true,
            rightCharging = false,
            caseCharging = false,
            leftInEar = false,
            rightInEar = true,
            isLidOpen = false,
            lidCounter = 2,
            flipped = true
        )

        val state = AppleContinuityParser.parse(packet, rssi = -61, timestampMillis = 2000L)
        assertNotNull(state)
        state!!
        assertEquals(AirPodsModel.AIRPODS_PRO_2_LIGHTNING, state.model)
        assertEquals(40, state.left.percentage)
        assertEquals(100, state.right.percentage)
        assertEquals(70, state.caseBattery.percentage)
        assertTrue(state.left.isCharging)
        assertFalse(state.right.isCharging)
        assertFalse(state.left.isInEar)
        assertTrue(state.right.isInEar)
        assertFalse(state.isLidOpen)
        assertEquals(2, state.lidOpenCounter)
    }

    @Test
    fun parsesUnknownFutureAirPodsModelIdInsteadOfDropping() {
        val packet = AppleContinuityParser.buildTestPacket(
            customModelId = 0x2A20,
            leftPct = 70,
            rightPct = 70,
            casePct = 50,
            isLidOpen = true
        )

        val state = AppleContinuityParser.parse(packet, rssi = -58)
        assertNotNull(state)
        state!!
        assertEquals(AirPodsModel.AIRPODS_GENERIC, state.model)
        assertEquals(0x2A20, state.rawModelId)
        assertEquals(70, state.left.percentage)
        assertEquals(70, state.right.percentage)
        assertEquals(50, state.caseBattery.percentage)
    }

    @Test
    fun decodesDisconnectedNibblesAsNull() {
        val packet = AppleContinuityParser.buildTestPacket(
            model = AirPodsModel.AIRPODS_PRO_3,
            leftPct = 80,
            rightPct = null,
            casePct = null,
            isLidOpen = false
        )

        val state = AppleContinuityParser.parse(packet, rssi = -58)
        assertNotNull(state)
        assertEquals(80, state!!.left.percentage)
        assertNull(state.right.percentage)
        assertNull(state.caseBattery.percentage)
    }

    @Test
    fun rejectsMalformedOrNonProximityPackets() {
        assertNull(AppleContinuityParser.parse(byteArrayOf(0x07, 0x19), rssi = -50))

        val wrongType = AppleContinuityParser.buildTestPacket().apply { this[0] = 0x10 }
        assertNull(AppleContinuityParser.parse(wrongType, rssi = -50))

        val allDisconnected = AppleContinuityParser.buildTestPacket(
            leftPct = null,
            rightPct = null,
            casePct = null
        )
        assertNull(AppleContinuityParser.parse(allDisconnected, rssi = -50))
    }

    @Test
    fun distinguishesPodsChargingInCaseFromCaseCharging() {
        val podsOnly = AppleContinuityParser.buildTestPacket(
            model = AirPodsModel.AIRPODS_PRO_3,
            leftPct = 80,
            rightPct = 80,
            casePct = 60,
            leftCharging = true,
            rightCharging = true,
            caseCharging = false
        )
        val state = AppleContinuityParser.parse(podsOnly, rssi = -50)!!
        assertEquals("PODS CHARGING IN CASE", state.statusTextEn)
        assertFalse(state.caseBattery.isCharging)
        assertTrue(state.left.isCharging)
        assertTrue(state.right.isCharging)
    }
}
