package com.sumipods.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ble.ComponentBattery
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

private val BatteryPercentStyle = TextStyle(
    fontFamily = ZenMaruFontFamily,
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum"
)

@Composable
fun AirPodsSumiBatteryList(
    left: ComponentBattery,
    right: ComponentBattery,
    caseBattery: ComponentBattery,
    showJapaneseLabels: Boolean,
    modifier: Modifier = Modifier,
    rowSpacing: Dp = 20.dp,
    barHeight: Dp = 15.dp
) {
    val anyCharging = (left.isCharging && left.isConnected) ||
        (right.isCharging && right.isConnected) ||
        (caseBattery.isCharging && caseBattery.isConnected)

    val sharedChargingPhase: State<Float>? = if (anyCharging) {
        val transition = rememberInfiniteTransition(label = "sumi_charge_shared")
        transition.animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sumi_charge_shared_phase"
        )
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        SumiBatteryRow(
            labelEn = "LEFT",
            labelJp = "左",
            battery = left,
            showJapanese = showJapaneseLabels,
            seed = 11,
            barHeight = barHeight,
            sharedChargingPhase = sharedChargingPhase
        )
        SumiBatteryRow(
            labelEn = "RIGHT",
            labelJp = "右",
            battery = right,
            showJapanese = showJapaneseLabels,
            seed = 23,
            barHeight = barHeight,
            sharedChargingPhase = sharedChargingPhase
        )
        SumiBatteryRow(
            labelEn = "CASE",
            labelJp = "充電ケース",
            battery = caseBattery,
            showJapanese = showJapaneseLabels,
            seed = 37,
            barHeight = barHeight,
            sharedChargingPhase = sharedChargingPhase
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
    barHeight: Dp,
    sharedChargingPhase: State<Float>?
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
                    style = BatteryPercentStyle,
                    color = valueColor
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        SumiInkBatteryBar(
            percentage = pct,
            isCharging = battery.isCharging,
            seed = seed,
            barHeight = barHeight,
            sharedChargingPhase = sharedChargingPhase
        )
    }
}
