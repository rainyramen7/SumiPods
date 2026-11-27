package com.sumipods.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumipods.app.ui.theme.SumiColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

private const val SEGMENT_COUNT = 10

private class CachedSegmentGeometry(
    val left: Float,
    val normalPath: Path,
    val trailingPath: Path,
    val brush: Brush,
    val inkCoreColor: Color,
    val h1: Float,
    val h2: Float,
    val h3: Float,
    val normalizedX: Float
)

/**
 * 10-step battery bar rendered as Japanese Sumi-e ink brush strokes on washi paper.
 *
 * Performance:
 * - Reads `animatedFill` and `chargingPhase` exclusively inside the draw phase (`onDrawBehind`),
 *   resulting in 0 recompositions per frame during fill transitions and 60/120Hz charging shimmer.
 * - Pre-caches all 10 organic brush `Path`s (normal & trailing edge) and `LinearGradient` `Brush`es
 *   inside `Modifier.drawWithCache` keyed on `(size, seed, isLow)`, eliminating native
 *   `Path`/`Shader` allocations per frame while charging.
 * - Accepts an optional hoisted `sharedChargingPhase` so multiple charging bars share 1 animation clock.
 */
@Composable
fun SumiInkBatteryBar(
    percentage: Int?,
    isCharging: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 15.dp,
    sharedChargingPhase: State<Float>? = null
) {
    val targetFill = (percentage?.coerceIn(0, 100) ?: 0) / 10f
    val animatedFillState = animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sumi_fill"
    )

    val activelyCharging = isCharging && percentage != null
    val chargingPhaseState: State<Float>? = when {
        !activelyCharging -> null
        sharedChargingPhase != null -> sharedChargingPhase
        else -> {
            val transition = rememberInfiniteTransition(label = "sumi_charge")
            transition.animateFloat(
                initialValue = -0.25f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sumi_charge_phase"
            )
        }
    }

    val isLow = percentage != null && percentage <= 20
    val isDisconnected = percentage == null

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .drawWithCache {
                val gap = 4.dp.toPx()
                val totalGap = gap * (SEGMENT_COUNT - 1)
                val segW = ((size.width - totalGap) / SEGMENT_COUNT).coerceAtLeast(1f)
                val midY = size.height / 2f
                val strokeH = size.height * 0.72f
                val topBase = midY - strokeH / 2f

                val inkDarkBase = if (isLow) SumiColors.HankoDeep else SumiColors.SumiDeep
                val inkMidBase = if (isLow) SumiColors.HankoVermilion else SumiColors.SumiInk
                val bleedColor = (if (isLow) SumiColors.HankoVermilion else SumiColors.SumiDeep)
                    .copy(alpha = 0.085f)

                val notchTop = topBase + strokeH * 0.14f
                val notchHeight = strokeH * 0.72f
                val notchColor = if (isDisconnected) {
                    SumiColors.Hairline.copy(alpha = 0.55f)
                } else {
                    SumiColors.WashiStone
                }
                val notchHairlineColor = SumiColors.Hairline.copy(alpha = 0.7f)
                val notchCorner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                val notchInset = 1.dp.toPx()
                val notchStrokeW = 0.6.dp.toPx()

                val bleedPad = 1.1.dp.toPx()
                val bleedCorner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                val slantIn = 0.8.dp.toPx()
                val normalTaperOut = 0.5.dp.toPx()
                val trailingTaperOut = 1.3.dp.toPx()
                val rightControlOffset = 0.4.dp.toPx()
                val leftControlOffset = 0.35.dp.toPx()
                val trailingTopDrop = 0.55.dp.toPx()
                val trailingBotLift = 0.45.dp.toPx()

                val bristleStrokeThin = 0.75.dp.toPx()
                val bristleStrokeThick = 1.1.dp.toPx()
                val dryBrushTrailingColor = SumiColors.WashiPaper.copy(alpha = 0.42f)
                val dryBrushNormalColor = SumiColors.WashiPaper.copy(alpha = 0.14f)

                val dpToPxScale = 1.dp.toPx()

                fun populateBrushPath(
                    target: Path,
                    left: Float,
                    width: Float,
                    h1: Float,
                    h2: Float,
                    h3: Float,
                    isTrailingEdge: Boolean
                ) {
                    target.reset()
                    val topJitterL = h1 * 0.85f * dpToPxScale
                    val topJitterR = h2 * 0.95f * dpToPxScale
                    val botJitterL = -h2 * 0.75f * dpToPxScale
                    val botJitterR = h3 * 0.9f * dpToPxScale
                    val archTop = -abs(h1) * 0.8f * dpToPxScale
                    val archBot = abs(h3) * 0.65f * dpToPxScale
                    val taperOut = if (isTrailingEdge) trailingTaperOut else normalTaperOut

                    target.moveTo(left + slantIn, topBase + topJitterL)
                    target.quadraticTo(
                        left + width * 0.48f,
                        topBase + archTop,
                        left + width - taperOut * 0.4f,
                        topBase + topJitterR + if (isTrailingEdge) trailingTopDrop else 0f
                    )
                    target.quadraticTo(
                        left + width + rightControlOffset,
                        topBase + strokeH * 0.48f,
                        left + width - taperOut,
                        topBase + strokeH + botJitterR - if (isTrailingEdge) trailingBotLift else 0f
                    )
                    target.quadraticTo(
                        left + width * 0.52f,
                        topBase + strokeH + archBot,
                        left,
                        topBase + strokeH + botJitterL
                    )
                    target.quadraticTo(
                        left - leftControlOffset,
                        topBase + strokeH * 0.52f,
                        left + slantIn,
                        topBase + topJitterL
                    )
                    target.close()
                }

                val segments = Array(SEGMENT_COUNT) { i ->
                    val left = i * (segW + gap)
                    val h1 = sin((i + 1) * 1.9 + seed * 3.1).toFloat()
                    val h2 = sin((i + 1) * 3.7 + seed * 1.7).toFloat()
                    val h3 = sin((i + 1) * 5.3 + seed * 2.3).toFloat()

                    val normalPath = Path().also {
                        populateBrushPath(it, left, segW, h1, h2, h3, isTrailingEdge = false)
                    }
                    val trailingPath = Path().also {
                        populateBrushPath(it, left, segW, h1, h2, h3, isTrailingEdge = true)
                    }

                    val inkWeight = (1f - i * 0.022f).coerceIn(0.76f, 1f)
                    val inkDark = inkDarkBase.copy(alpha = inkWeight)
                    val inkMid = inkMidBase.copy(alpha = inkWeight * 0.94f)
                    val brush = Brush.linearGradient(
                        colors = listOf(inkDark, inkMid, inkDark.copy(alpha = inkDark.alpha * 0.9f)),
                        start = Offset(left, topBase),
                        end = Offset(left + segW, topBase + strokeH)
                    )

                    CachedSegmentGeometry(
                        left = left,
                        normalPath = normalPath,
                        trailingPath = trailingPath,
                        brush = brush,
                        inkCoreColor = inkDark.copy(alpha = 0.38f),
                        h1 = h1,
                        h2 = h2,
                        h3 = h3,
                        normalizedX = (i + 0.5f) / SEGMENT_COUNT
                    )
                }

                val partialScratchPath = Path()

                onDrawBehind {
                    val animatedFill = animatedFillState.value
                    val chargingPhase = chargingPhaseState?.value ?: -1f

                    val fullSegments = floor(animatedFill).toInt().coerceIn(0, SEGMENT_COUNT)
                    val partialFraction = (animatedFill - fullSegments).coerceIn(0f, 1f)
                    val activeLastIndex = (ceil(animatedFill).toInt() - 1).coerceAtLeast(0)

                    for (i in 0 until SEGMENT_COUNT) {
                        val seg = segments[i]
                        val left = seg.left

                        val segCoverage = when {
                            isDisconnected -> 0f
                            i < fullSegments -> 1f
                            i == fullSegments && partialFraction > 0.02f -> partialFraction
                            else -> 0f
                        }

                        if (segCoverage < 1f) {
                            drawRoundRect(
                                color = notchColor,
                                topLeft = Offset(left, notchTop),
                                size = Size(segW, notchHeight),
                                cornerRadius = notchCorner
                            )
                            drawLine(
                                color = notchHairlineColor,
                                start = Offset(left + notchInset, notchTop + notchHeight),
                                end = Offset(left + segW - notchInset, notchTop + notchHeight),
                                strokeWidth = notchStrokeW
                            )
                        }

                        if (segCoverage > 0f) {
                            val isTrailingEdge = (i == activeLastIndex)
                            val drawWidth = segW * segCoverage
                            val strokePath = if (segCoverage >= 0.999f) {
                                if (isTrailingEdge) seg.trailingPath else seg.normalPath
                            } else {
                                populateBrushPath(
                                    target = partialScratchPath,
                                    left = left,
                                    width = drawWidth,
                                    h1 = seg.h1,
                                    h2 = seg.h2,
                                    h3 = seg.h3,
                                    isTrailingEdge = isTrailingEdge
                                )
                                partialScratchPath
                            }

                            drawRoundRect(
                                color = bleedColor,
                                topLeft = Offset(left - bleedPad * 0.5f, topBase - bleedPad * 0.6f),
                                size = Size(drawWidth + bleedPad, strokeH + bleedPad * 1.2f),
                                cornerRadius = bleedCorner
                            )

                            drawPath(path = strokePath, brush = seg.brush)

                            drawLine(
                                color = seg.inkCoreColor,
                                start = Offset(left + drawWidth * 0.12f, topBase + strokeH * 0.56f),
                                end = Offset(left + drawWidth * 0.86f, topBase + strokeH * 0.44f),
                                strokeWidth = strokeH * 0.28f,
                                cap = StrokeCap.Round
                            )

                            val bristleColor = if (isTrailingEdge) dryBrushTrailingColor else dryBrushNormalColor
                            val bristleCount = if (isTrailingEdge) 3 else 1
                            for (b in 0 until bristleCount) {
                                val ratioY = 0.24f + b * 0.26f + seg.h2 * 0.04f
                                val startFrac = if (isTrailingEdge) 0.32f + b * 0.12f else 0.55f
                                drawLine(
                                    color = bristleColor,
                                    start = Offset(left + drawWidth * startFrac, topBase + strokeH * ratioY),
                                    end = Offset(left + drawWidth * 0.98f, topBase + strokeH * (ratioY + 0.03f)),
                                    strokeWidth = if (b % 2 == 0) bristleStrokeThin else bristleStrokeThick,
                                    cap = StrokeCap.Round
                                )
                            }

                            if (activelyCharging && chargingPhase >= -0.25f) {
                                val dist = abs(seg.normalizedX - chargingPhase)
                                if (dist < 0.22f) {
                                    val glow = ((0.22f - dist) / 0.22f) * 0.36f
                                    drawPath(
                                        path = strokePath,
                                        color = SumiColors.AmberOchre.copy(alpha = glow)
                                    )
                                }
                            }
                        }
                    }
                }
            }
    )
}
