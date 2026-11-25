package com.sumipods.app.ble

/**
 * Decodes Apple Continuity Proximity Pairing BLE manufacturer data (Company ID 0x004C)
 * broadcast by AirPods Pro 3 (0x2720), AirPods Pro 2 (0x1420 / 0x2420), and all other AirPods.
 */
object AppleContinuityParser {

    const val APPLE_COMPANY_ID = 0x004C
    private const val PROXIMITY_PAIRING_TYPE = 0x07
    private const val MIN_PROXIMITY_LENGTH = 0x13
    private const val MAX_PROXIMITY_LENGTH = 0x1F
    private const val MIN_PACKET_SIZE = 11
    private const val STANDARD_PACKET_SIZE = 27
    private const val BATTERY_DISCONNECTED = 0x0F

    fun parse(
        manufacturerData: ByteArray,
        rssi: Int,
        timestampMillis: Long = System.currentTimeMillis()
    ): AirPodsState? {
        if (manufacturerData.size < MIN_PACKET_SIZE) return null

        // Some Continuity packets contain multiple TLV messages; locate type 0x07
        val offset = findProximityMessageOffset(manufacturerData) ?: return null
        if (manufacturerData.size - offset < MIN_PACKET_SIZE) return null

        val length = manufacturerData[offset + 1].toInt() and 0xFF
        if (length !in MIN_PROXIMITY_LENGTH..MAX_PROXIMITY_LENGTH) {
            return null
        }

        // Byte 2 is pairing prefix (0x01 for paired/status broadcasts; 0x00 for initial setup)
        val prefix = manufacturerData[offset + 2].toInt() and 0xFF
        if (prefix != 0x01 && prefix != 0x00) {
            return null
        }

        val modelId = ((manufacturerData[offset + 3].toInt() and 0xFF) shl 8) or
            (manufacturerData[offset + 4].toInt() and 0xFF)
        if (modelId == 0x0000) return null

        val model = AirPodsModel.fromModelId(modelId)

        val statusByte = manufacturerData[offset + 5].toInt() and 0xFF
        val isFlipped = (statusByte and 0x20) == 0

        val podsByte = manufacturerData[offset + 6].toInt() and 0xFF
        val primaryNib = (podsByte ushr 4) and 0x0F
        val secondaryNib = podsByte and 0x0F

        val chargeAndCaseByte = manufacturerData[offset + 7].toInt() and 0xFF
        val chargeFlags = (chargeAndCaseByte ushr 4) and 0x0F
        val caseNib = chargeAndCaseByte and 0x0F

        // Validate nibbles belong to Apple's battery encoding (0..10 or 0xF disconnected)
        if (!isValidBatteryNibble(primaryNib) ||
            !isValidBatteryNibble(secondaryNib) ||
            !isValidBatteryNibble(caseNib)
        ) {
            return null
        }

        val primaryLevel = decodeBatteryNibble(primaryNib)
        val secondaryLevel = decodeBatteryNibble(secondaryNib)
        val caseLevel = decodeBatteryNibble(caseNib)

        // Require at least one valid battery reading so non-headphone beacons are ignored
        if (primaryLevel == null && secondaryLevel == null && caseLevel == null) {
            return null
        }

        val primaryCharging = (chargeFlags and 0x01) != 0
        val secondaryCharging = (chargeFlags and 0x02) != 0
        val caseCharging = (chargeFlags and 0x04) != 0

        val primaryInEar = (statusByte and 0x02) != 0
        val secondaryInEar = (statusByte and 0x08) != 0

        val leftBattery = if (isFlipped) {
            ComponentBattery(secondaryLevel, secondaryCharging, secondaryInEar)
        } else {
            ComponentBattery(primaryLevel, primaryCharging, primaryInEar)
        }

        val rightBattery = if (isFlipped) {
            ComponentBattery(primaryLevel, primaryCharging, primaryInEar)
        } else {
            ComponentBattery(secondaryLevel, secondaryCharging, secondaryInEar)
        }

        val lidByte = manufacturerData[offset + 8].toInt() and 0xFF
        val isLidOpen = (lidByte and 0x08) == 0
        val lidOpenCounter = lidByte and 0x07

        return AirPodsState(
            model = model,
            rawModelId = modelId,
            left = leftBattery,
            right = rightBattery,
            caseBattery = ComponentBattery(caseLevel, caseCharging),
            isLidOpen = isLidOpen,
            lidOpenCounter = lidOpenCounter,
            rssi = rssi,
            lastUpdatedMillis = timestampMillis,
            connectionStatus = ConnectionStatus.CONNECTED,
            isDemo = false
        )
    }

    private fun findProximityMessageOffset(data: ByteArray): Int? {
        var idx = 0
        while (idx + 2 < data.size) {
            val type = data[idx].toInt() and 0xFF
            val len = data[idx + 1].toInt() and 0xFF
            if (type == PROXIMITY_PAIRING_TYPE && len in MIN_PROXIMITY_LENGTH..MAX_PROXIMITY_LENGTH) {
                return idx
            }
            if (len <= 0) return null
            idx += 2 + len
        }
        return null
    }

    private fun isValidBatteryNibble(nibble: Int): Boolean =
        nibble in 0..10 || nibble == BATTERY_DISCONNECTED

    internal fun decodeBatteryNibble(nibble: Int): Int? {
        if (nibble == BATTERY_DISCONNECTED || nibble !in 0..10) return null
        return nibble * 10
    }

    /**
     * Constructs a valid 27-byte Apple Continuity Proximity Pairing packet for unit tests.
     */
    fun buildTestPacket(
        model: AirPodsModel = AirPodsModel.AIRPODS_PRO_3,
        customModelId: Int? = null,
        leftPct: Int? = 90,
        rightPct: Int? = 80,
        casePct: Int? = 60,
        leftCharging: Boolean = false,
        rightCharging: Boolean = false,
        caseCharging: Boolean = false,
        leftInEar: Boolean = false,
        rightInEar: Boolean = false,
        isLidOpen: Boolean = true,
        lidCounter: Int = 1,
        flipped: Boolean = false
    ): ByteArray {
        val bytes = ByteArray(STANDARD_PACKET_SIZE)
        val id = customModelId ?: model.modelId
        bytes[0] = PROXIMITY_PAIRING_TYPE.toByte()
        bytes[1] = 0x19.toByte()
        bytes[2] = 0x01
        bytes[3] = ((id ushr 8) and 0xFF).toByte()
        bytes[4] = (id and 0xFF).toByte()

        val primaryPct = if (flipped) rightPct else leftPct
        val secondaryPct = if (flipped) leftPct else rightPct
        val primaryCharging = if (flipped) rightCharging else leftCharging
        val secondaryCharging = if (flipped) leftCharging else rightCharging
        val primaryInEar = if (flipped) rightInEar else leftInEar
        val secondaryInEar = if (flipped) leftInEar else rightInEar

        var status = if (flipped) 0x00 else 0x20
        if (primaryInEar) status = status or 0x02
        if (secondaryInEar) status = status or 0x08
        bytes[5] = status.toByte()

        val pNib = encodeBatteryNibble(primaryPct)
        val sNib = encodeBatteryNibble(secondaryPct)
        bytes[6] = ((pNib shl 4) or sNib).toByte()

        var chargeNib = 0
        if (primaryCharging) chargeNib = chargeNib or 0x01
        if (secondaryCharging) chargeNib = chargeNib or 0x02
        if (caseCharging) chargeNib = chargeNib or 0x04
        val cNib = encodeBatteryNibble(casePct)
        bytes[7] = ((chargeNib shl 4) or cNib).toByte()

        val lidBit = if (isLidOpen) 0x00 else 0x08
        bytes[8] = (lidBit or (lidCounter and 0x07)).toByte()
        return bytes
    }

    private fun encodeBatteryNibble(pct: Int?): Int =
        pct?.coerceIn(0, 100)?.div(10) ?: BATTERY_DISCONNECTED
}
