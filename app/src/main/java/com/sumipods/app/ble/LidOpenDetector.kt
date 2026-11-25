package com.sumipods.app.ble

/**
 * Stateful detector that identifies when the user flips open their AirPods Pro 2 case lid,
 * while preventing duplicate popups during continuous BLE advertisements.
 */
class LidOpenDetector(
    private val cooldownMillis: Long = 10_000L,
    private val silenceResetMillis: Long = 12_000L
) {
    private var lastLidOpen: Boolean? = null
    private var lastLidCounter: Int? = null
    private var lastPacketTimeMillis: Long = 0L
    private var lastPopupTriggerMillis: Long = 0L

    fun onPacket(
        state: AirPodsState,
        rssiThreshold: Int,
        nowMillis: Long = state.lastUpdatedMillis
    ): Boolean {
        if (state.rssi < rssiThreshold) return false

        val wasSilent = lastPacketTimeMillis == 0L || (nowMillis - lastPacketTimeMillis) > silenceResetMillis
        val previousLidOpen = lastLidOpen
        val previousCounter = lastLidCounter

        lastPacketTimeMillis = nowMillis
        lastLidOpen = state.isLidOpen
        lastLidCounter = state.lidOpenCounter

        if (!state.isLidOpen) {
            return false
        }

        val transitionedClosedToOpen = previousLidOpen == false
        val counterAdvanced = previousCounter != null && state.lidOpenCounter != previousCounter
        val freshOpenAfterSilence = wasSilent

        val isLidOpenEvent = transitionedClosedToOpen || counterAdvanced || freshOpenAfterSilence
        if (!isLidOpenEvent) return false

        val withinCooldown = lastPopupTriggerMillis > 0L && (nowMillis - lastPopupTriggerMillis) < cooldownMillis
        if (withinCooldown) return false

        lastPopupTriggerMillis = nowMillis
        return true
    }

    fun reset() {
        lastLidOpen = null
        lastLidCounter = null
        lastPacketTimeMillis = 0L
        lastPopupTriggerMillis = 0L
    }
}
