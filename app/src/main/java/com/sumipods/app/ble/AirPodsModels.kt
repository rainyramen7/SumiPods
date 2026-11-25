package com.sumipods.app.ble

enum class AirPodsModel(
    val modelId: Int,
    val displayName: String,
    val shortSubtitle: String,
    val isPro: Boolean = false
) {
    AIRPODS_PRO_3(0x2720, "AIRPODS PRO 3", "PRO 3 · BLE", isPro = true),
    AIRPODS_PRO_3_ALT(0x203F, "AIRPODS PRO 3", "PRO 3 · BLE", isPro = true),
    AIRPODS_PRO_2_USBC(0x2420, "AIRPODS PRO 2", "USB-C · H2", isPro = true),
    AIRPODS_PRO_2_LIGHTNING(0x1420, "AIRPODS PRO 2", "LIGHTNING · H2", isPro = true),
    AIRPODS_PRO_1(0x0E20, "AIRPODS PRO", "GEN 1", isPro = true),
    AIRPODS_4_ANC(0x1B20, "AIRPODS 4", "ANC"),
    AIRPODS_4(0x1920, "AIRPODS 4", "GEN 4"),
    AIRPODS_3(0x1320, "AIRPODS 3", "GEN 3"),
    AIRPODS_2(0x0F20, "AIRPODS 2", "GEN 2"),
    AIRPODS_1(0x0220, "AIRPODS", "GEN 1"),
    AIRPODS_MAX(0x0A20, "AIRPODS MAX", "OVER-EAR"),
    AIRPODS_GENERIC(0x0000, "AIRPODS PRO", "APPLE BLE", isPro = true);

    companion object {
        fun fromModelId(id: Int): AirPodsModel =
            entries.firstOrNull { it.modelId == id && it != AIRPODS_GENERIC } ?: AIRPODS_GENERIC
    }
}

enum class ConnectionStatus {
    CONNECTED,
    SCANNING,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    DEMO
}

enum class ProximityLevel(
    val rssiThreshold: Int,
    val labelEn: String,
    val labelJp: String
) {
    CLOSE(-68, "Close", "近距離"),
    MEDIUM(-82, "Medium", "標準"),
    WIDE(-95, "Wide", "広範囲")
}

enum class DemoPreset(
    val labelEn: String,
    val labelJp: String
) {
    IN_USE("In Use", "装着中"),
    CHARGING("Charging", "充電中"),
    LOW("Low", "残量低下")
}

enum class ScanPowerMode {
    LOW_POWER,
    BALANCED
}

data class ComponentBattery(
    val percentage: Int?,
    val isCharging: Boolean = false,
    val isInEar: Boolean = false
) {
    val isConnected: Boolean get() = percentage != null
    val isLow: Boolean get() = percentage != null && percentage <= 20
}

data class AirPodsState(
    val model: AirPodsModel = AirPodsModel.AIRPODS_PRO_3,
    val rawModelId: Int = AirPodsModel.AIRPODS_PRO_3.modelId,
    val left: ComponentBattery = ComponentBattery(null),
    val right: ComponentBattery = ComponentBattery(null),
    val caseBattery: ComponentBattery = ComponentBattery(null),
    val isLidOpen: Boolean = false,
    val lidOpenCounter: Int = 0,
    val rssi: Int = -65,
    val lastUpdatedMillis: Long = 0L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.SCANNING,
    val isDemo: Boolean = false
) {
    val hasAnyBattery: Boolean
        get() = left.isConnected || right.isConnected || caseBattery.isConnected

    val statusTextEn: String
        get() = when (connectionStatus) {
            ConnectionStatus.NO_PERMISSION -> "BLUETOOTH PERMISSION NEEDED"
            ConnectionStatus.BLUETOOTH_OFF -> "BLUETOOTH OFF"
            ConnectionStatus.SCANNING -> "OPEN CASE NEAR PHONE"
            ConnectionStatus.CONNECTED, ConnectionStatus.DEMO -> when {
                left.isInEar && right.isInEar -> "IN BOTH EARS"
                left.isInEar && right.isCharging -> "LEFT IN EAR · RIGHT CHARGING"
                right.isInEar && left.isCharging -> "RIGHT IN EAR · LEFT CHARGING"
                left.isInEar -> "LEFT IN EAR"
                right.isInEar -> "RIGHT IN EAR"
                caseBattery.isCharging && (left.isCharging || right.isCharging) -> "CASE & PODS CHARGING"
                caseBattery.isCharging -> "CASE CHARGING"
                left.isCharging && right.isCharging -> "PODS CHARGING IN CASE"
                left.isCharging -> "LEFT POD CHARGING"
                right.isCharging -> "RIGHT POD CHARGING"
                isLidOpen -> "CASE OPEN"
                left.isLow || right.isLow || caseBattery.isLow -> "LOW BATTERY"
                else -> "CONNECTED"
            }
        }

    val statusTextJp: String
        get() = when (connectionStatus) {
            ConnectionStatus.NO_PERMISSION -> "権限が必要です"
            ConnectionStatus.BLUETOOTH_OFF -> "Bluetooth オフ"
            ConnectionStatus.SCANNING -> "検索中"
            ConnectionStatus.CONNECTED, ConnectionStatus.DEMO -> when {
                left.isInEar && right.isInEar -> "両耳装着中"
                left.isInEar && right.isCharging -> "左装着・右充電中"
                right.isInEar && left.isCharging -> "右装着・左充電中"
                left.isInEar -> "左耳装着中"
                right.isInEar -> "右耳装着中"
                caseBattery.isCharging && (left.isCharging || right.isCharging) -> "ケース・両耳充電中"
                caseBattery.isCharging -> "ケース充電中"
                left.isCharging && right.isCharging -> "ケース内で充電中"
                left.isCharging -> "左耳充電中"
                right.isCharging -> "右耳充電中"
                isLidOpen -> "ケース開放中"
                left.isLow || right.isLow || caseBattery.isLow -> "残量低下"
                else -> "接続中"
            }
        }

    val footerModelTag: String
        get() = if (model == AirPodsModel.AIRPODS_GENERIC && rawModelId != 0) {
            "ID 0x${rawModelId.toString(16).uppercase().padStart(4, '0')}"
        } else {
            model.shortSubtitle
        }

    companion object {
        fun demo(preset: DemoPreset): AirPodsState {
            val now = System.currentTimeMillis()
            return when (preset) {
                DemoPreset.IN_USE -> AirPodsState(
                    model = AirPodsModel.AIRPODS_PRO_3,
                    rawModelId = AirPodsModel.AIRPODS_PRO_3.modelId,
                    left = ComponentBattery(90, isCharging = false, isInEar = true),
                    right = ComponentBattery(80, isCharging = false, isInEar = true),
                    caseBattery = ComponentBattery(60, isCharging = false),
                    isLidOpen = false,
                    lidOpenCounter = 2,
                    rssi = -54,
                    lastUpdatedMillis = now,
                    connectionStatus = ConnectionStatus.DEMO,
                    isDemo = true
                )
                DemoPreset.CHARGING -> AirPodsState(
                    model = AirPodsModel.AIRPODS_PRO_3,
                    rawModelId = AirPodsModel.AIRPODS_PRO_3.modelId,
                    left = ComponentBattery(100, isCharging = true, isInEar = false),
                    right = ComponentBattery(90, isCharging = true, isInEar = false),
                    caseBattery = ComponentBattery(70, isCharging = true),
                    isLidOpen = true,
                    lidOpenCounter = 3,
                    rssi = -49,
                    lastUpdatedMillis = now,
                    connectionStatus = ConnectionStatus.DEMO,
                    isDemo = true
                )
                DemoPreset.LOW -> AirPodsState(
                    model = AirPodsModel.AIRPODS_PRO_3,
                    rawModelId = AirPodsModel.AIRPODS_PRO_3.modelId,
                    left = ComponentBattery(10, isCharging = false, isInEar = false),
                    right = ComponentBattery(20, isCharging = false, isInEar = false),
                    caseBattery = ComponentBattery(10, isCharging = false),
                    isLidOpen = true,
                    lidOpenCounter = 4,
                    rssi = -58,
                    lastUpdatedMillis = now,
                    connectionStatus = ConnectionStatus.DEMO,
                    isDemo = true
                )
            }
        }
    }
}

data class AppPreferences(
    val autoPopupEnabled: Boolean = true,
    val showJapaneseLabels: Boolean = true,
    val backgroundMonitoringEnabled: Boolean = true,
    val proximityLevel: ProximityLevel = ProximityLevel.MEDIUM,
    val demoModeEnabled: Boolean = false,
    val demoPreset: DemoPreset = DemoPreset.IN_USE
)
