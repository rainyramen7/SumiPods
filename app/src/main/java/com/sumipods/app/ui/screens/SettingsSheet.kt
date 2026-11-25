package com.sumipods.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ble.AppPreferences
import com.sumipods.app.ble.ProximityLevel
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    preferences: AppPreferences,
    hasOverlayPermission: Boolean,
    onDismiss: () -> Unit,
    onToggleAutoPopup: (Boolean) -> Unit,
    onToggleBackgroundMonitor: (Boolean) -> Unit,
    onToggleJapaneseLabels: (Boolean) -> Unit,
    onSelectProximity: (ProximityLevel) -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SumiColors.WashiElevated,
        contentColor = SumiColors.SumiDeep,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (preferences.showJapaneseLabels) "SETTINGS · 設定" else "SETTINGS",
                    fontFamily = ZenMaruFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = SumiColors.SumiDeep
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

            SettingSwitchRow(
                title = "Auto-Popup on Case Open",
                subtitle = if (hasOverlayPermission) {
                    "Slides up over any app when you flip open the case"
                } else {
                    "Grant 'Display over other apps' for instant background popup"
                },
                checked = preferences.autoPopupEnabled,
                onCheckedChange = onToggleAutoPopup
            )

            if (preferences.autoPopupEnabled && !hasOverlayPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SumiColors.WashiPaper)
                        .border(BorderStroke(1.dp, SumiColors.HankoVermilion.copy(alpha = 0.55f)), RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenOverlaySettings)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable 'Display over other apps'",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SumiColors.HankoVermilion
                    )
                    Text(
                        text = "OPEN →",
                        fontFamily = ZenMaruFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SumiColors.HankoVermilion
                    )
                }
            }

            HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

            SettingSwitchRow(
                title = "Background BLE Monitor",
                subtitle = "Low-power listener for AirPods Pro while app is closed",
                checked = preferences.backgroundMonitoringEnabled,
                onCheckedChange = onToggleBackgroundMonitor
            )

            HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

            SettingSwitchRow(
                title = "Japanese Micro-Labels",
                subtitle = "Show subtle bilingual markers (左 · 右 · 充電ケース)",
                checked = preferences.showJapaneseLabels,
                onCheckedChange = onToggleJapaneseLabels
            )

            HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

            // Proximity sensitivity selector
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Text(
                    text = "Case Proximity Range",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SumiColors.SumiDeep
                )
                Text(
                    text = "Ignores distant AirPods from nearby people",
                    fontSize = 12.sp,
                    color = SumiColors.SumiMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProximityLevel.entries.forEach { level ->
                        val selected = level == preferences.proximityLevel
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) SumiColors.SumiDeep else SumiColors.WashiPaper)
                                .border(
                                    1.dp,
                                    if (selected) SumiColors.SumiDeep else SumiColors.HairlineStrong,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectProximity(level) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (preferences.showJapaneseLabels) level.labelJp else level.labelEn,
                                fontFamily = ZenMaruFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) SumiColors.WashiPaper else SumiColors.SumiMuted
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

            SettingSwitchRow(
                title = "Demo Preview Mode",
                subtitle = "Simulate In Use, Charging, and Low Battery states",
                checked = preferences.demoModeEnabled,
                onCheckedChange = onToggleDemoMode
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SumiColors.SumiDeep
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = SumiColors.SumiMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SumiColors.WashiPaper,
                checkedTrackColor = SumiColors.SumiDeep,
                uncheckedThumbColor = SumiColors.SumiMuted,
                uncheckedTrackColor = SumiColors.WashiStone,
                uncheckedBorderColor = SumiColors.HairlineStrong
            )
        )
    }
}
