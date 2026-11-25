package com.sumipods.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ble.AirPodsState
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

/**
 * A4 — Kamon Blueprint (家紋 × 図面) AirPods Pro 3 & MagSafe Case illustration.
 *
 * Combines minimalist Japanese architectural drafting crosshairs, a circular dotted Kamon crest
 * framing the case, concentric double-line geometric contours, and Shu-iro vermilion precision accents.
 */
@Composable
fun AirPodsPro2Stage(
    state: AirPodsState,
    showJapaneseLabels: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val lidProgress by animateFloatAsState(
        targetValue = if (state.isLidOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "lid_open_anim"
    )

    val podW = if (compact) 44.dp else 54.dp
    val podH = if (compact) 64.dp else 78.dp
    val caseW = if (compact) 76.dp else 94.dp
    val caseH = if (compact) 64.dp else 78.dp
    val backdropH = if (compact) 98.dp else 124.dp

    val caseLedColor = when {
        state.caseBattery.isLow -> SumiColors.HankoVermilion
        state.caseBattery.isCharging -> SumiColors.AmberOchre
        state.hasAnyBattery -> SumiColors.MossGreen
        else -> SumiColors.SumiFaint
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Kamon crest ring, horizontal datum axis, and 4-corner drafting '+' registration marks
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropH)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.44f

            // Horizontal blueprint datum axis
            drawLine(
                color = SumiColors.Hairline,
                start = Offset(4.dp.toPx(), cy),
                end = Offset(w - 4.dp.toPx(), cy),
                strokeWidth = 0.85.dp.toPx()
            )

            // Subtle vertical center axis ticks above & below the Kamon ring
            val outerR = (if (compact) 40.dp else 52.dp).toPx()
            val innerR = outerR * (0.84f + 0.04f * lidProgress)

            drawLine(
                color = SumiColors.HairlineStrong.copy(alpha = 0.7f),
                start = Offset(cx, cy - outerR - 6.dp.toPx()),
                end = Offset(cx, cy - outerR - 1.5.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = SumiColors.HairlineStrong.copy(alpha = 0.7f),
                start = Offset(cx, cy + outerR + 1.5.dp.toPx()),
                end = Offset(cx, cy + outerR + 6.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Dotted outer Kamon circular crest frame
            drawCircle(
                color = SumiColors.HairlineStrong,
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(3.5.dp.toPx(), 3.5.dp.toPx()),
                        0f
                    )
                )
            )

            // Delicate inner concentric Kamon halo (brightens gently when lid opens)
            drawCircle(
                color = if (state.isLidOpen) {
                    SumiColors.HankoVermilion.copy(alpha = 0.22f * lidProgress + 0.06f)
                } else {
                    SumiColors.Hairline.copy(alpha = 0.85f)
                },
                radius = innerR,
                center = Offset(cx, cy),
                style = Stroke(width = 0.75.dp.toPx())
            )

            // 4-corner architectural '+' registration crosshairs
            val insetX = 6.dp.toPx()
            val insetY = 6.dp.toPx()
            val cross = 3.5.dp.toPx()
            val crossColor = SumiColors.SumiFaint.copy(alpha = 0.75f)
            val corners = listOf(
                Offset(insetX, insetY),
                Offset(w - insetX, insetY),
                Offset(insetX, h - insetY),
                Offset(w - insetX, h - insetY)
            )
            corners.forEach { pt ->
                drawLine(
                    color = crossColor,
                    start = Offset(pt.x - cross, pt.y),
                    end = Offset(pt.x + cross, pt.y),
                    strokeWidth = 0.9.dp.toPx()
                )
                drawLine(
                    color = crossColor,
                    start = Offset(pt.x, pt.y - cross),
                    end = Offset(pt.x, pt.y + cross),
                    strokeWidth = 0.9.dp.toPx()
                )
            }
        }

        // Foreground Hardware Silhouettes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            PodColumn(
                label = if (showJapaneseLabels) "L · 左" else "L",
                active = state.left.isConnected,
                inEar = state.left.isInEar
            ) {
                KamonAirPodGlyph(
                    isLeft = true,
                    alpha = if (state.left.isConnected) 1f else 0.36f,
                    isLow = state.left.isLow,
                    isInEar = state.left.isInEar,
                    width = podW,
                    height = podH
                )
            }

            PodColumn(
                label = if (showJapaneseLabels) "CASE · 筐体" else "CASE",
                active = state.caseBattery.isConnected || state.isLidOpen,
                inEar = false
            ) {
                KamonCaseGlyph(
                    lidOpenProgress = lidProgress,
                    ledColor = caseLedColor,
                    alpha = if (state.caseBattery.isConnected || state.hasAnyBattery) 1f else 0.45f,
                    width = caseW,
                    height = caseH
                )
            }

            PodColumn(
                label = if (showJapaneseLabels) "R · 右" else "R",
                active = state.right.isConnected,
                inEar = state.right.isInEar
            ) {
                KamonAirPodGlyph(
                    isLeft = false,
                    alpha = if (state.right.isConnected) 1f else 0.36f,
                    isLow = state.right.isLow,
                    isInEar = state.right.isInEar,
                    width = podW,
                    height = podH
                )
            }
        }
    }
}

@Composable
private fun PodColumn(
    label: String,
    active: Boolean,
    inEar: Boolean,
    glyph: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        glyph()
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (inEar) "$label ●" else label,
            fontFamily = ZenMaruFontFamily,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.8.sp,
            color = if (active) SumiColors.SumiMuted else SumiColors.SumiFaint
        )
    }
}

@Composable
private fun KamonAirPodGlyph(
    isLeft: Boolean,
    alpha: Float,
    isLow: Boolean,
    isInEar: Boolean,
    width: Dp,
    height: Dp
) {
    Canvas(modifier = Modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val fill = SumiColors.WashiPod.copy(alpha = alpha)
        val lineColor = (if (isLow) SumiColors.HankoVermilion else SumiColors.SumiInk).copy(alpha = alpha)
        val mainStroke = Stroke(width = 1.35.dp.toPx())
        val fineStroke = Stroke(width = 0.8.dp.toPx())

        val headW = w * 0.56f
        val headH = h * 0.34f
        val headTop = h * 0.12f
        val headLeft = if (isLeft) w * 0.17f else w * 0.27f

        val stemW = w * 0.20f
        val stemH = h * 0.50f
        val stemTop = headTop + headH * 0.72f
        val stemLeft = if (isLeft) headLeft + headW * 0.56f else headLeft + headW * 0.24f

        // Subtle ear-tip silhouette (precision drafting oval)
        val tipW = w * 0.18f
        val tipH = headH * 0.58f
        val tipLeft = if (isLeft) headLeft - tipW * 0.44f else headLeft + headW - tipW * 0.56f
        val tipTop = headTop + (headH - tipH) / 2f

        drawOval(
            color = SumiColors.WashiPaper.copy(alpha = alpha),
            topLeft = Offset(tipLeft, tipTop),
            size = Size(tipW, tipH)
        )
        drawOval(
            color = lineColor.copy(alpha = alpha * 0.75f),
            topLeft = Offset(tipLeft, tipTop),
            size = Size(tipW, tipH),
            style = fineStroke
        )

        // Stem
        drawRoundRect(
            color = fill,
            topLeft = Offset(stemLeft, stemTop),
            size = Size(stemW, stemH),
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(stemLeft, stemTop),
            size = Size(stemW, stemH),
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f),
            style = mainStroke
        )

        // Head housing
        drawRoundRect(
            color = fill,
            topLeft = Offset(headLeft, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headH * 0.48f, headH * 0.48f)
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(headLeft, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headH * 0.48f, headH * 0.48f),
            style = mainStroke
        )

        // Internal architectural blueprint axis across the pod head
        val headMidY = headTop + headH * 0.5f
        drawLine(
            color = SumiColors.HairlineStrong.copy(alpha = alpha * 0.85f),
            start = Offset(headLeft + headW * 0.14f, headMidY),
            end = Offset(headLeft + headW * 0.86f, headMidY),
            strokeWidth = 0.75.dp.toPx()
        )

        // Geometric acoustic sensor dot (clean circular aperture)
        val sensorX = if (isLeft) headLeft + headW * 0.66f else headLeft + headW * 0.34f
        drawCircle(
            color = lineColor,
            radius = 2.6.dp.toPx(),
            center = Offset(sensorX, headMidY)
        )
        if (isInEar) {
            drawCircle(
                color = SumiColors.MossGreen.copy(alpha = 0.55f * alpha),
                radius = 5.2.dp.toPx(),
                center = Offset(sensorX, headMidY),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Signature A4 Kamon vermilion micro-pin on the stem tip
        drawCircle(
            color = SumiColors.HankoVermilion.copy(alpha = alpha * 0.9f),
            radius = 1.5.dp.toPx(),
            center = Offset(stemLeft + stemW / 2f, stemTop + stemH * 0.82f)
        )
    }
}

@Composable
private fun KamonCaseGlyph(
    lidOpenProgress: Float,
    ledColor: Color,
    alpha: Float,
    width: Dp,
    height: Dp
) {
    Canvas(modifier = Modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val fill = SumiColors.WashiPod.copy(alpha = alpha)
        val lineColor = SumiColors.SumiInk.copy(alpha = alpha)
        val outerStroke = Stroke(width = 1.4.dp.toPx())
        val innerStroke = Stroke(width = 0.8.dp.toPx())

        val caseW = w * 0.82f
        val caseH = h * 0.64f
        val caseLeft = (w - caseW) / 2f
        val caseTop = h * 0.22f
        val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())

        val lidLift = lidOpenProgress * (h * 0.13f)
        val seamY = caseTop + caseH * 0.31f

        if (lidOpenProgress > 0.05f) {
            drawRoundRect(
                color = SumiColors.SumiDeep.copy(alpha = 0.11f * lidOpenProgress * alpha),
                topLeft = Offset(caseLeft + 3.5.dp.toPx(), seamY - lidLift * 0.55f),
                size = Size(caseW - 7.dp.toPx(), lidLift * 0.72f + 4.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }

        // Lower body
        drawRoundRect(
            color = fill,
            topLeft = Offset(caseLeft, caseTop),
            size = Size(caseW, caseH),
            cornerRadius = corner
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(caseLeft, caseTop),
            size = Size(caseW, caseH),
            cornerRadius = corner,
            style = outerStroke
        )

        // Concentric inner Kamon blueprint contour
        val inset = 4.dp.toPx()
        drawRoundRect(
            color = SumiColors.Hairline.copy(alpha = alpha),
            topLeft = Offset(caseLeft + inset, caseTop + inset),
            size = Size(caseW - inset * 2f, caseH - inset * 2f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            style = innerStroke
        )

        // Animated upper lid cap
        val lidHeight = caseH * 0.31f
        drawRoundRect(
            color = fill,
            topLeft = Offset(caseLeft, caseTop - lidLift),
            size = Size(caseW, lidHeight),
            cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx())
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(caseLeft, caseTop - lidLift),
            size = Size(caseW, lidHeight),
            cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
            style = outerStroke
        )

        // Lid seam hairline
        drawLine(
            color = lineColor.copy(alpha = 0.8f * alpha),
            start = Offset(caseLeft + 1.dp.toPx(), seamY - lidLift * 0.1f),
            end = Offset(caseLeft + caseW - 1.dp.toPx(), seamY - lidLift * 0.1f),
            strokeWidth = 1.1.dp.toPx()
        )

        // Signature A4 Shu-iro vermilion thumb-notch bar on the seam
        val notchW = caseW * 0.26f
        drawLine(
            color = SumiColors.HankoVermilion.copy(alpha = alpha),
            start = Offset(w / 2f - notchW / 2f, seamY - lidLift * 0.1f),
            end = Offset(w / 2f + notchW / 2f, seamY - lidLift * 0.1f),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Front status LED with delicate circular blueprint ring
        val ledCenter = Offset(w / 2f, caseTop + caseH * 0.56f)
        drawCircle(
            color = ledColor.copy(alpha = 0.18f * alpha),
            radius = 5.5.dp.toPx(),
            center = ledCenter
        )
        drawCircle(
            color = ledColor.copy(alpha = alpha),
            radius = 2.4.dp.toPx(),
            center = ledCenter
        )

        drawSpeakerDots(caseLeft, caseTop + caseH, caseW, alpha)
    }
}

private fun DrawScope.drawSpeakerDots(
    caseLeft: Float,
    caseBottom: Float,
    caseW: Float,
    alpha: Float
) {
    val dotColor = SumiColors.SumiMuted.copy(alpha = 0.45f * alpha)
    val y = caseBottom - 4.dp.toPx()
    val r = 0.85.dp.toPx()
    val offsets = listOf(0.29f, 0.34f, 0.39f, 0.61f, 0.66f, 0.71f)
    for (frac in offsets) {
        drawCircle(
            color = dotColor,
            radius = r,
            center = Offset(caseLeft + caseW * frac, y)
        )
    }
}
