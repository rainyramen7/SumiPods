package com.sumipods.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ble.AirPodsState
import com.sumipods.app.ble.ComponentBattery
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

@Composable
fun AirPodsSumiBatteryList(
    state: AirPodsState,
    showJapaneseLabels: Boolean,
    modifier: Modifier = Modifier,
    rowSpacing: Dp = 20.dp,
    barHeight: Dp = 15.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        SumiBatteryRow(
            labelEn = "LEFT",
            labelJp = "左",
            battery = state.left,
            showJapanese = showJapaneseLabels,
            seed = 11,
            barHeight = barHeight
        )
        SumiBatteryRow(
            labelEn = "RIGHT",
            labelJp = "右",
            battery = state.right,
            showJapanese = showJapaneseLabels,
            seed = 23,
            barHeight = barHeight
        )
        SumiBatteryRow(
            labelEn = "CASE",
            labelJp = "充電ケース",
            battery = state.caseBattery,
            showJapanese = showJapaneseLabels,
            seed = 37,
            barHeight = barHeight
        )
    }
}

@Composable
private fun SumiBatteryRow(
    labelEn: String,
    labelJp: String,
    battery: ComponentBattery,
    showJapanese: Boolean,
    seed: Int,
    barHeight: Dp
) {
    val pct = battery.percentage
    val valueColor = when {
        pct == null -> SumiColors.SumiFaint
        battery.isLow -> SumiColors.HankoVermilion
        else -> SumiColors.SumiDeep
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = labelEn,
                    fontFamily = ZenMaruFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                    color = SumiColors.SumiMuted
                )
                if (showJapanese) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = labelJp,
                        fontFamily = ZenMaruFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = SumiColors.SumiFaint
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (battery.isCharging) {
                    Text(
                        text = if (showJapanese) "充電中 ⚡" else "CHARGING ⚡",
                        fontFamily = ZenMaruFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SumiColors.AmberOchre
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else if (battery.isInEar) {
                    Text(
                        text = if (showJapanese) "装着中" else "IN EAR",
                        fontFamily = ZenMaruFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SumiColors.SumiFaint
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = pct?.let { "$it%" } ?: "—",
                    style = TextStyle(
                        fontFamily = ZenMaruFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                        color = valueColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        SumiInkBatteryBar(
            percentage = pct,
            isCharging = battery.isCharging,
            seed = seed,
            barHeight = barHeight
        )
    }
}
