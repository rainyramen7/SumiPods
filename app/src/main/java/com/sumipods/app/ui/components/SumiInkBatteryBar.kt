package com.sumipods.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumipods.app.ui.theme.SumiColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * 10-step battery bar rendered as Japanese Sumi-e ink brush strokes on washi paper.
 *
 * Features:
 * - Organic hand-brushed stroke silhouettes aligned to a clean 10-step (10%) precision grid
 * - Soft nijimi (滲み) ink-bleed underlayer absorbed into the paper
 * - Subtle kasure (掠れ) dry-brush bristle texture, strongest at the trailing filled tip
 * - Automatic shift to Shu-iro vermilion seal ink when battery <= 20%
 * - Subtle warm ochre ink flow shimmer while charging
 */
@Composable
fun SumiInkBatteryBar(
    percentage: Int?,
    isCharging: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 15.dp
) {
    val targetFill = (percentage?.coerceIn(0, 100) ?: 0) / 10f
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sumi_fill"
    )

    val chargingPhase = if (isCharging) {
        val transition = rememberInfiniteTransition(label = "sumi_charge")
        val phase by transition.animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sumi_charge_phase"
        )
        phase
    } else {
        -1f
    }

    val isLow = percentage != null && percentage <= 20
    val isDisconnected = percentage == null

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val segmentCount = 10
        val gap = 4.dp.toPx()
        val totalGap = gap * (segmentCount - 1)
        val segW = (size.width - totalGap) / segmentCount
        val midY = size.height / 2f
        val strokeH = size.height * 0.72f
        val topBase = midY - strokeH / 2f

        val inkDark = if (isLow) SumiColors.HankoDeep else SumiColors.SumiDeep
        val inkMid = if (isLow) SumiColors.HankoVermilion else SumiColors.SumiInk
        val bleedColor = if (isLow) SumiColors.HankoVermilion else SumiColors.SumiDeep

        val fullSegments = floor(animatedFill).toInt().coerceIn(0, segmentCount)
        val partialFraction = (animatedFill - fullSegments).coerceIn(0f, 1f)
        val activeLastIndex = (ceil(animatedFill).toInt() - 1).coerceAtLeast(0)

        for (i in 0 until segmentCount) {
            val left = i * (segW + gap)

            // Calm recessed washi notch for every slot
            drawUnfilledNotch(
                left = left,
                top = topBase + strokeH * 0.14f,
                width = segW,
                height = strokeH * 0.72f,
                disconnected = isDisconnected
            )

            val segCoverage = when {
                isDisconnected -> 0f
                i < fullSegments -> 1f
                i == fullSegments && partialFraction > 0.02f -> partialFraction
                else -> 0f
            }

            if (segCoverage > 0f) {
                val isTrailingEdge = (i == activeLastIndex)
                val inkWeight = (1f - i * 0.022f).coerceIn(0.76f, 1f)
                drawSumiBrushSegment(
                    left = left,
                    top = topBase,
                    width = segW * segCoverage,
                    height = strokeH,
                    index = i,
                    seed = seed,
                    inkDark = inkDark.copy(alpha = inkWeight),
                    inkMid = inkMid.copy(alpha = inkWeight * 0.94f),
                    bleedColor = bleedColor,
                    isTrailingEdge = isTrailingEdge,
                    isCharging = isCharging,
                    chargingPhase = chargingPhase,
                    normalizedX = (i + 0.5f) / segmentCount
                )
            }
        }
    }
}

private fun DrawScope.drawUnfilledNotch(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    disconnected: Boolean
) {
    val notchColor = if (disconnected) {
        SumiColors.Hairline.copy(alpha = 0.55f)
    } else {
        SumiColors.WashiStone
    }
    drawRoundRect(
        color = notchColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
    )
    // Subtle precision hairline on bottom of each notch
    drawLine(
        color = SumiColors.Hairline.copy(alpha = 0.7f),
        start = Offset(left + 1.dp.toPx(), top + height),
        end = Offset(left + width - 1.dp.toPx(), top + height),
        strokeWidth = 0.6.dp.toPx()
    )
}

private fun DrawScope.drawSumiBrushSegment(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    index: Int,
    seed: Int,
    inkDark: Color,
    inkMid: Color,
    bleedColor: Color,
    isTrailingEdge: Boolean,
    isCharging: Boolean,
    chargingPhase: Float,
    normalizedX: Float
) {
    // Deterministic organic brush variations per segment
    val h1 = sin((index + 1) * 1.9 + seed * 3.1).toFloat()
    val h2 = sin((index + 1) * 3.7 + seed * 1.7).toFloat()
    val h3 = sin((index + 1) * 5.3 + seed * 2.3).toFloat()

    val topJitterL = h1 * 0.85.dp.toPx()
    val topJitterR = h2 * 0.95.dp.toPx()
    val botJitterL = -h2 * 0.75.dp.toPx()
    val botJitterR = h3 * 0.9.dp.toPx()
    val archTop = -kotlin.math.abs(h1) * 0.8.dp.toPx()
    val archBot = kotlin.math.abs(h3) * 0.65.dp.toPx()

    // Slight calligraphic brush slant on entry & tapered flick on trailing edge
    val slantIn = 0.8.dp.toPx()
    val taperOut = if (isTrailingEdge) 1.3.dp.toPx() else 0.5.dp.toPx()

    val strokePath = Path().apply {
        moveTo(left + slantIn, top + topJitterL)
        quadraticTo(
            left + width * 0.48f,
            top + archTop,
            left + width - taperOut * 0.4f,
            top + topJitterR + if (isTrailingEdge) 0.55.dp.toPx() else 0f
        )
        quadraticTo(
            left + width + 0.4.dp.toPx(),
            top + height * 0.48f,
            left + width - taperOut,
            top + height + botJitterR - if (isTrailingEdge) 0.45.dp.toPx() else 0f
        )
        quadraticTo(
            left + width * 0.52f,
            top + height + archBot,
            left,
            top + height + botJitterL
        )
        quadraticTo(
            left - 0.35.dp.toPx(),
            top + height * 0.52f,
            left + slantIn,
            top + topJitterL
        )
        close()
    }

    // 1. Nijimi (滲み) soft ink bleed absorbed into the washi paper
    val bleedPad = 1.1.dp.toPx()
    drawRoundRect(
        color = bleedColor.copy(alpha = 0.085f),
        topLeft = Offset(left - bleedPad * 0.5f, top - bleedPad * 0.6f),
        size = Size(width + bleedPad, height + bleedPad * 1.2f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )

    // 2. Main Sumi ink brush body with rich tonal depth
    val brush = Brush.linearGradient(
        colors = listOf(inkDark, inkMid, inkDark.copy(alpha = inkDark.alpha * 0.9f)),
        start = Offset(left, top),
        end = Offset(left + width, top + height)
    )
    drawPath(path = strokePath, brush = brush)

    // 3. Pooled darker ink core along the brush belly
    drawLine(
        color = inkDark.copy(alpha = 0.38f),
        start = Offset(left + width * 0.12f, top + height * 0.56f),
        end = Offset(left + width * 0.86f, top + height * 0.44f),
        strokeWidth = height * 0.28f,
        cap = StrokeCap.Round
    )

    // 4. Kasure (掠れ) dry-brush bristle texture — faint paper grain showing through ink
    val dryBrushAlpha = if (isTrailingEdge) 0.42f else 0.14f
    val bristleCount = if (isTrailingEdge) 3 else 1
    for (b in 0 until bristleCount) {
        val ratioY = 0.24f + b * 0.26f + h2 * 0.04f
        val startFrac = if (isTrailingEdge) 0.32f + b * 0.12f else 0.55f
        drawLine(
            color = SumiColors.WashiPaper.copy(alpha = dryBrushAlpha),
            start = Offset(left + width * startFrac, top + height * ratioY),
            end = Offset(left + width * 0.98f, top + height * (ratioY + 0.03f)),
            strokeWidth = (0.75f + (b % 2) * 0.35f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // 5. Charging warm ochre ink shimmer traveling gently along the brush strokes
    if (isCharging && chargingPhase >= -0.25f) {
        val dist = kotlin.math.abs(normalizedX - chargingPhase)
        if (dist < 0.22f) {
            val glow = ((0.22f - dist) / 0.22f) * 0.36f
            drawPath(
                path = strokePath,
                color = SumiColors.AmberOchre.copy(alpha = glow)
            )
        }
    }
}
