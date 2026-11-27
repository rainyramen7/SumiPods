package com.sumipods.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ble.AirPodsState
import com.sumipods.app.ble.AppPreferences
import com.sumipods.app.ble.ConnectionStatus
import com.sumipods.app.ble.DemoPreset
import com.sumipods.app.ui.components.AirPodsPro2Stage
import com.sumipods.app.ui.components.AirPodsSumiBatteryList
import com.sumipods.app.ui.components.HankoSeal
import com.sumipods.app.ui.components.SumiIcons
import com.sumipods.app.ui.components.washiPaperBackground
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.SumiPodsTheme
import com.sumipods.app.ui.theme.ZenMaruFontFamily

@Composable
fun MainScreen(
    state: AirPodsState,
    preferences: AppPreferences,
    hasBluetoothPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDemoPreset: (DemoPreset) -> Unit
) {
    val showJp = preferences.showJapaneseLabels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .washiPaperBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Calm MUJI Header with generous Ma spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.model.displayName,
                            fontFamily = ZenMaruFontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.4.sp,
                            color = SumiColors.SumiDeep
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = if (showJp) {
                                "${state.statusTextJp} · ${state.statusTextEn}"
                            } else {
                                state.statusTextEn
                            },
                            fontFamily = ZenMaruFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                            color = SumiColors.SumiMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HankoSeal(
                            showJapanese = showJp,
                            isActive = state.hasAnyBattery || state.connectionStatus == ConnectionStatus.DEMO
                        )
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = SumiIcons.Tune,
                                contentDescription = "Settings",
                                tint = SumiColors.SumiWash,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Optional inline setup strip when Bluetooth or Overlay permission is missing
                if (!hasBluetoothPermission && !preferences.demoModeEnabled) {
                    Spacer(modifier = Modifier.height(18.dp))
                    SetupNoticeBanner(
                        title = "Allow Bluetooth to read AirPods Pro",
                        actionLabel = "GRANT",
                        accentVermilion = true,
                        onClick = onRequestPermissions
                    )
                } else if (preferences.autoPopupEnabled && !hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(18.dp))
                    SetupNoticeBanner(
                        title = "Enable overlay for instant case-open popup",
                        actionLabel = "ENABLE",
                        accentVermilion = false,
                        onClick = onOpenOverlaySettings
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // 2. Calm AirPods Pro 3 Kamon Blueprint Stage framed by quiet 1dp hairlines
                HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)
                AirPodsPro2Stage(
                    isLidOpen = state.isLidOpen,
                    left = state.left,
                    right = state.right,
                    caseBattery = state.caseBattery,
                    hasAnyBattery = state.hasAnyBattery,
                    showJapaneseLabels = showJp,
                    modifier = Modifier.padding(vertical = 26.dp)
                )
                HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

                Spacer(modifier = Modifier.height(28.dp))

                // 3. Sumi Ink 10-Step Battery Readouts
                AirPodsSumiBatteryList(
                    left = state.left,
                    right = state.right,
                    caseBattery = state.caseBattery,
                    showJapaneseLabels = showJp,
                    rowSpacing = 22.dp,
                    barHeight = 16.dp
                )

                // Demo state switcher (only shown if user enables Demo Mode inside Settings)
                if (preferences.demoModeEnabled) {
                    Spacer(modifier = Modifier.height(22.dp))
                    DemoPresetStrip(
                        selected = preferences.demoPreset,
                        showJapanese = showJp,
                        onSelect = onSelectDemoPreset
                    )
                }
            }

            // 4. Quiet Footer Telemetry (isolated composable scope for RSSI updates)
            FooterTelemetryRow(
                isLidOpen = state.isLidOpen,
                isDemo = state.isDemo,
                hasAnyBattery = state.hasAnyBattery,
                footerModelTag = state.footerModelTag,
                rssi = state.rssi,
                showJapanese = showJp
            )
        }
    }
}

@Composable
private fun FooterTelemetryRow(
    isLidOpen: Boolean,
    isDemo: Boolean,
    hasAnyBattery: Boolean,
    footerModelTag: String,
    rssi: Int,
    showJapanese: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lidText = if (showJapanese) {
                if (isLidOpen) "蓋 開 · LID OPEN" else "蓋 閉 · LID CLOSED"
            } else {
                if (isLidOpen) "LID OPEN" else "LID CLOSED"
            }
            Text(
                text = lidText,
                fontFamily = ZenMaruFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = SumiColors.SumiMuted
            )
            Text(
                text = if (isDemo) {
                    "DEMO · 10% STEP"
                } else if (hasAnyBattery) {
                    "$footerModelTag · ${rssi}dBm"
                } else {
                    "BLE · 10% STEP"
                },
                fontFamily = ZenMaruFontFamily,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = SumiColors.SumiFaint
            )
        }
    }
}

@Composable
private fun SetupNoticeBanner(
    title: String,
    actionLabel: String,
    accentVermilion: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (accentVermilion) {
        SumiColors.HankoVermilion.copy(alpha = 0.45f)
    } else {
        SumiColors.HairlineStrong
    }
    val actionColor = if (accentVermilion) SumiColors.HankoVermilion else SumiColors.SumiDeep

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SumiColors.WashiElevated.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontFamily = ZenMaruFontFamily,
            fontSize = 12.sp,
            color = SumiColors.SumiDeep,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = actionLabel,
            fontFamily = ZenMaruFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = actionColor
        )
    }
}

@Composable
private fun DemoPresetStrip(
    selected: DemoPreset,
    showJapanese: Boolean,
    onSelect: (DemoPreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, SumiColors.Hairline, RoundedCornerShape(8.dp))
            .background(SumiColors.WashiElevated.copy(alpha = 0.7f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DemoPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) SumiColors.SumiDeep else SumiColors.WashiElevated.copy(alpha = 0f))
                    .clickable { onSelect(preset) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showJapanese) preset.labelJp else preset.labelEn,
                    fontFamily = ZenMaruFontFamily,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) SumiColors.WashiPaper else SumiColors.SumiMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
fun MainScreenInUsePreview() {
    SumiPodsTheme {
        MainScreen(
            state = AirPodsState.demo(DemoPreset.IN_USE),
            preferences = AppPreferences(demoModeEnabled = true),
            hasBluetoothPermission = true,
            hasOverlayPermission = true,
            onRequestPermissions = {},
            onOpenOverlaySettings = {},
            onOpenSettings = {},
            onSelectDemoPreset = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
fun MainScreenLowBatteryPreview() {
    SumiPodsTheme {
        MainScreen(
            state = AirPodsState.demo(DemoPreset.LOW),
            preferences = AppPreferences(demoModeEnabled = true, demoPreset = DemoPreset.LOW),
            hasBluetoothPermission = true,
            hasOverlayPermission = true,
            onRequestPermissions = {},
            onOpenOverlaySettings = {},
            onOpenSettings = {},
            onSelectDemoPreset = {}
        )
    }
}
