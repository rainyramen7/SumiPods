package com.sumipods.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import com.sumipods.app.ble.ComponentBattery
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

private val SPEAKER_DOT_FRACTIONS = floatArrayOf(0.29f, 0.34f, 0.39f, 0.61f, 0.66f, 0.71f)

/**
 * A4 — Kamon Blueprint (家紋 × 図面) AirPods Pro 3 & MagSafe Case illustration.
 *
 * Performance:
 * - Accepts narrow component slices rather than the full `AirPodsState`, skipping recomposition
 *   on unrelated RSSI or status changes.
 * - Defers `lidProgress` reads strictly to the draw phase via lambda providers (0 recompositions
 *   during the 480ms lid animation).
 * - Caches `PathEffect.dashPathEffect`, all `Stroke` objects, and static blueprint coordinates
 *   inside `Modifier.drawWithCache` (zero per-frame allocations).
 */
@Composable
fun AirPodsPro2Stage(
    isLidOpen: Boolean,
    left: ComponentBattery,
    right: ComponentBattery,
    caseBattery: ComponentBattery,
    hasAnyBattery: Boolean,
    showJapaneseLabels: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val lidProgressState = animateFloatAsState(
        targetValue = if (isLidOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "lid_open_anim"
    )
    val lidProgressProvider: () -> Float = { lidProgressState.value }

    val podW = if (compact) 44.dp else 54.dp
    val podH = if (compact) 64.dp else 78.dp
    val caseW = if (compact) 76.dp else 94.dp
    val caseH = if (compact) 64.dp else 78.dp
    val backdropH = if (compact) 98.dp else 124.dp

    val caseLedColor = when {
        caseBattery.isLow -> SumiColors.HankoVermilion
        caseBattery.isCharging -> SumiColors.AmberOchre
        hasAnyBattery -> SumiColors.MossGreen
        else -> SumiColors.SumiFaint
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Kamon crest ring, horizontal datum axis, and 4-corner drafting '+' registration marks
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropH)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h * 0.44f
                    val center = Offset(cx, cy)

                    val outerR = (if (compact) 40.dp else 52.dp).toPx()
                    val datumInset = 4.dp.toPx()
                    val datumStrokeW = 0.85.dp.toPx()
                    val tickOuter = 6.dp.toPx()
                    val tickInner = 1.5.dp.toPx()
                    val tickStrokeW = 1.dp.toPx()
                    val tickColor = SumiColors.HairlineStrong.copy(alpha = 0.7f)

                    val dashLen = 3.5.dp.toPx()
                    val dashedOuterStroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen), 0f)
                    )
                    val innerRingStroke = Stroke(width = 0.75.dp.toPx())
                    val closedInnerColor = SumiColors.Hairline.copy(alpha = 0.85f)

                    val insetX = 6.dp.toPx()
                    val insetY = 6.dp.toPx()
                    val cross = 3.5.dp.toPx()
                    val crossStrokeW = 0.9.dp.toPx()
                    val crossColor = SumiColors.SumiFaint.copy(alpha = 0.75f)

                    onDrawBehind {
                        val lidProgress = lidProgressProvider()
                        val innerR = outerR * (0.84f + 0.04f * lidProgress)

                        drawLine(
                            color = SumiColors.Hairline,
                            start = Offset(datumInset, cy),
                            end = Offset(w - datumInset, cy),
                            strokeWidth = datumStrokeW
                        )

                        drawLine(
                            color = tickColor,
                            start = Offset(cx, cy - outerR - tickOuter),
                            end = Offset(cx, cy - outerR - tickInner),
                            strokeWidth = tickStrokeW
                        )
                        drawLine(
                            color = tickColor,
                            start = Offset(cx, cy + outerR + tickInner),
                            end = Offset(cx, cy + outerR + tickOuter),
                            strokeWidth = tickStrokeW
                        )

                        drawCircle(
                            color = SumiColors.HairlineStrong,
                            radius = outerR,
                            center = center,
                            style = dashedOuterStroke
                        )

                        drawCircle(
                            color = if (isLidOpen) {
                                SumiColors.HankoVermilion.copy(alpha = 0.22f * lidProgress + 0.06f)
                            } else {
                                closedInnerColor
                            },
                            radius = innerR,
                            center = center,
                            style = innerRingStroke
                        )

                        drawCornerCrosshair(insetX, insetY, cross, crossColor, crossStrokeW)
                        drawCornerCrosshair(w - insetX, insetY, cross, crossColor, crossStrokeW)
                        drawCornerCrosshair(insetX, h - insetY, cross, crossColor, crossStrokeW)
                        drawCornerCrosshair(w - insetX, h - insetY, cross, crossColor, crossStrokeW)
                    }
                }
        )

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
                active = left.isConnected,
                inEar = left.isInEar
            ) {
                KamonAirPodGlyph(
                    isLeft = true,
                    alpha = if (left.isConnected) 1f else 0.36f,
                    isLow = left.isLow,
                    isInEar = left.isInEar,
                    width = podW,
                    height = podH
                )
            }

            PodColumn(
                label = if (showJapaneseLabels) "CASE · 筐体" else "CASE",
                active = caseBattery.isConnected || isLidOpen,
                inEar = false
            ) {
                KamonCaseGlyph(
                    lidProgressProvider = lidProgressProvider,
                    ledColor = caseLedColor,
                    alpha = if (caseBattery.isConnected || hasAnyBattery) 1f else 0.45f,
                    width = caseW,
                    height = caseH
                )
            }

            PodColumn(
                label = if (showJapaneseLabels) "R · 右" else "R",
                active = right.isConnected,
                inEar = right.isInEar
            ) {
                KamonAirPodGlyph(
                    isLeft = false,
                    alpha = if (right.isConnected) 1f else 0.36f,
                    isLow = right.isLow,
                    isInEar = right.isInEar,
                    width = podW,
                    height = podH
                )
            }
        }
    }
}

private fun DrawScope.drawCornerCrosshair(
    x: Float,
    y: Float,
    cross: Float,
    color: Color,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = Offset(x - cross, y),
        end = Offset(x + cross, y),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(x, y - cross),
        end = Offset(x, y + cross),
        strokeWidth = strokeWidth
    )
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
    Spacer(
        modifier = Modifier
            .size(width, height)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val fill = SumiColors.WashiPod.copy(alpha = alpha)
                val lineColor = (if (isLow) SumiColors.HankoVermilion else SumiColors.SumiInk).copy(alpha = alpha)
                val tipFill = SumiColors.WashiPaper.copy(alpha = alpha)
                val tipStrokeColor = lineColor.copy(alpha = alpha * 0.75f)
                val axisColor = SumiColors.HairlineStrong.copy(alpha = alpha * 0.85f)
                val inEarRingColor = SumiColors.MossGreen.copy(alpha = 0.55f * alpha)
                val pinColor = SumiColors.HankoVermilion.copy(alpha = alpha * 0.9f)

                val mainStroke = Stroke(width = 1.35.dp.toPx())
                val fineStroke = Stroke(width = 0.8.dp.toPx())
                val inEarRingStroke = Stroke(width = 1.dp.toPx())
                val axisStrokeW = 0.75.dp.toPx()

                val headW = w * 0.56f
                val headH = h * 0.34f
                val headTop = h * 0.12f
                val headLeft = if (isLeft) w * 0.17f else w * 0.27f
                val headTopLeft = Offset(headLeft, headTop)
                val headSize = Size(headW, headH)
                val headCorner = CornerRadius(headH * 0.48f, headH * 0.48f)

                val stemW = w * 0.20f
                val stemH = h * 0.50f
                val stemTop = headTop + headH * 0.72f
                val stemLeft = if (isLeft) headLeft + headW * 0.56f else headLeft + headW * 0.24f
                val stemTopLeft = Offset(stemLeft, stemTop)
                val stemSize = Size(stemW, stemH)
                val stemCorner = CornerRadius(stemW / 2f, stemW / 2f)

                val tipW = w * 0.18f
                val tipH = headH * 0.58f
                val tipLeft = if (isLeft) headLeft - tipW * 0.44f else headLeft + headW - tipW * 0.56f
                val tipTop = headTop + (headH - tipH) / 2f
                val tipTopLeft = Offset(tipLeft, tipTop)
                val tipSize = Size(tipW, tipH)

                val headMidY = headTop + headH * 0.5f
                val axisStart = Offset(headLeft + headW * 0.14f, headMidY)
                val axisEnd = Offset(headLeft + headW * 0.86f, headMidY)

                val sensorX = if (isLeft) headLeft + headW * 0.66f else headLeft + headW * 0.34f
                val sensorCenter = Offset(sensorX, headMidY)
                val sensorRadius = 2.6.dp.toPx()
                val inEarRingRadius = 5.2.dp.toPx()
                val pinCenter = Offset(stemLeft + stemW / 2f, stemTop + stemH * 0.82f)
                val pinRadius = 1.5.dp.toPx()

                onDrawBehind {
                    drawOval(color = tipFill, topLeft = tipTopLeft, size = tipSize)
                    drawOval(color = tipStrokeColor, topLeft = tipTopLeft, size = tipSize, style = fineStroke)

                    drawRoundRect(color = fill, topLeft = stemTopLeft, size = stemSize, cornerRadius = stemCorner)
                    drawRoundRect(
                        color = lineColor,
                        topLeft = stemTopLeft,
                        size = stemSize,
                        cornerRadius = stemCorner,
                        style = mainStroke
                    )

                    drawRoundRect(color = fill, topLeft = headTopLeft, size = headSize, cornerRadius = headCorner)
                    drawRoundRect(
                        color = lineColor,
                        topLeft = headTopLeft,
                        size = headSize,
                        cornerRadius = headCorner,
                        style = mainStroke
                    )

                    drawLine(color = axisColor, start = axisStart, end = axisEnd, strokeWidth = axisStrokeW)
                    drawCircle(color = lineColor, radius = sensorRadius, center = sensorCenter)
                    if (isInEar) {
                        drawCircle(
                            color = inEarRingColor,
                            radius = inEarRingRadius,
                            center = sensorCenter,
                            style = inEarRingStroke
                        )
                    }
                    drawCircle(color = pinColor, radius = pinRadius, center = pinCenter)
                }
            }
    )
}

@Composable
private fun KamonCaseGlyph(
    lidProgressProvider: () -> Float,
    ledColor: Color,
    alpha: Float,
    width: Dp,
    height: Dp
) {
    Spacer(
        modifier = Modifier
            .size(width, height)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val fill = SumiColors.WashiPod.copy(alpha = alpha)
                val lineColor = SumiColors.SumiInk.copy(alpha = alpha)
                val seamColor = lineColor.copy(alpha = 0.8f * alpha)
                val innerContourColor = SumiColors.Hairline.copy(alpha = alpha)
                val notchColor = SumiColors.HankoVermilion.copy(alpha = alpha)
                val ledHaloColor = ledColor.copy(alpha = 0.18f * alpha)
                val ledCoreColor = ledColor.copy(alpha = alpha)
                val speakerDotColor = SumiColors.SumiMuted.copy(alpha = 0.45f * alpha)

                val outerStroke = Stroke(width = 1.4.dp.toPx())
                val innerStroke = Stroke(width = 0.8.dp.toPx())
                val seamStrokeW = 1.1.dp.toPx()
                val notchStrokeW = 2.4.dp.toPx()

                val caseW = w * 0.82f
                val caseH = h * 0.64f
                val caseLeft = (w - caseW) / 2f
                val caseTop = h * 0.22f
                val caseTopLeft = Offset(caseLeft, caseTop)
                val caseSize = Size(caseW, caseH)
                val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                val lidCorner = CornerRadius(13.dp.toPx(), 13.dp.toPx())
                val shadowCorner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                val inset = 4.dp.toPx()
                val innerTopLeft = Offset(caseLeft + inset, caseTop + inset)
                val innerSize = Size(caseW - inset * 2f, caseH - inset * 2f)
                val innerCorner = CornerRadius(10.dp.toPx(), 10.dp.toPx())

                val seamY = caseTop + caseH * 0.31f
                val maxLidLift = h * 0.13f
                val lidHeight = caseH * 0.31f
                val lidSize = Size(caseW, lidHeight)
                val shadowInsetX = 3.5.dp.toPx()
                val shadowW = caseW - 7.dp.toPx()
                val shadowBaseH = 4.dp.toPx()
                val seamInsetX = 1.dp.toPx()
                val notchHalfW = caseW * 0.13f

                val ledCenter = Offset(w / 2f, caseTop + caseH * 0.56f)
                val ledHaloR = 5.5.dp.toPx()
                val ledCoreR = 2.4.dp.toPx()

                val speakerY = caseTop + caseH - 4.dp.toPx()
                val speakerR = 0.85.dp.toPx()

                onDrawBehind {
                    val lidOpenProgress = lidProgressProvider()
                    val lidLift = lidOpenProgress * maxLidLift

                    if (lidOpenProgress > 0.05f) {
                        drawRoundRect(
                            color = SumiColors.SumiDeep.copy(alpha = 0.11f * lidOpenProgress * alpha),
                            topLeft = Offset(caseLeft + shadowInsetX, seamY - lidLift * 0.55f),
                            size = Size(shadowW, lidLift * 0.72f + shadowBaseH),
                            cornerRadius = shadowCorner
                        )
                    }

                    // Lower body
                    drawRoundRect(color = fill, topLeft = caseTopLeft, size = caseSize, cornerRadius = corner)
                    drawRoundRect(
                        color = lineColor,
                        topLeft = caseTopLeft,
                        size = caseSize,
                        cornerRadius = corner,
                        style = outerStroke
                    )

                    // Concentric inner Kamon blueprint contour
                    drawRoundRect(
                        color = innerContourColor,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        cornerRadius = innerCorner,
                        style = innerStroke
                    )

                    // Animated upper lid cap
                    val lidTopLeft = Offset(caseLeft, caseTop - lidLift)
                    drawRoundRect(color = fill, topLeft = lidTopLeft, size = lidSize, cornerRadius = lidCorner)
                    drawRoundRect(
                        color = lineColor,
                        topLeft = lidTopLeft,
                        size = lidSize,
                        cornerRadius = lidCorner,
                        style = outerStroke
                    )

                    // Lid seam hairline
                    val animatedSeamY = seamY - lidLift * 0.1f
                    drawLine(
                        color = seamColor,
                        start = Offset(caseLeft + seamInsetX, animatedSeamY),
                        end = Offset(caseLeft + caseW - seamInsetX, animatedSeamY),
                        strokeWidth = seamStrokeW
                    )

                    // Signature A4 Shu-iro vermilion thumb-notch bar on the seam
                    drawLine(
                        color = notchColor,
                        start = Offset(w / 2f - notchHalfW, animatedSeamY),
                        end = Offset(w / 2f + notchHalfW, animatedSeamY),
                        strokeWidth = notchStrokeW,
                        cap = StrokeCap.Round
                    )

                    // Front status LED
                    drawCircle(color = ledHaloColor, radius = ledHaloR, center = ledCenter)
                    drawCircle(color = ledCoreColor, radius = ledCoreR, center = ledCenter)

                    // Bottom speaker grill dots (zero allocations)
                    for (i in SPEAKER_DOT_FRACTIONS.indices) {
                        drawCircle(
                            color = speakerDotColor,
                            radius = speakerR,
                            center = Offset(caseLeft + caseW * SPEAKER_DOT_FRACTIONS[i], speakerY)
                        )
                    }
                }
            }
    )
}
