package com.sumipods.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import com.sumipods.app.ui.theme.SumiColors
import kotlin.math.roundToInt

/**
 * Draws the subtle Option 4A warm washi paper background with fine architectural dot-grid
 * and whisper-light organic washi fiber flecks.
 *
 * Performance:
 * - Implemented as a `ModifierNodeElement` delegating to `CacheDrawModifierNode` so parent
 *   recompositions never invalidate the draw cache when parameters are unchanged.
 * - Pre-renders a single 14dp × 14dp repeating `ImageShader` tile (~1.6 KB) and a single batched
 *   mulberry fiber `Path`, turning ~1,700 per-frame `drawCircle`/`drawLine` calls into 3 GPU calls.
 */
fun Modifier.washiPaperBackground(
    baseColor: Color = SumiColors.WashiPaper,
    dotColor: Color = SumiColors.SumiDeep.copy(alpha = 0.11f),
    showFibers: Boolean = true
): Modifier = this then WashiPaperElement(
    baseColor = baseColor,
    dotColor = dotColor,
    showFibers = showFibers
)

private data class WashiPaperElement(
    val baseColor: Color,
    val dotColor: Color,
    val showFibers: Boolean
) : ModifierNodeElement<WashiPaperNode>() {
    override fun create(): WashiPaperNode = WashiPaperNode(baseColor, dotColor, showFibers)

    override fun update(node: WashiPaperNode) {
        node.updateParams(baseColor, dotColor, showFibers)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "washiPaperBackground"
        properties["baseColor"] = baseColor
        properties["dotColor"] = dotColor
        properties["showFibers"] = showFibers
    }
}

private class WashiPaperNode(
    private var baseColor: Color,
    private var dotColor: Color,
    private var showFibers: Boolean
) : DelegatingNode() {

    private val drawNode = delegate(
        CacheDrawModifierNode {
            val spacingPx = 14.dp.toPx()
            val tileSize = spacingPx.roundToInt().coerceAtLeast(1)
            val dotRadius = 0.85.dp.toPx()

            val tileBitmap = ImageBitmap(tileSize, tileSize, ImageBitmapConfig.Argb8888)
            val tileCanvas = Canvas(tileBitmap)
            val dotPaint = Paint().apply {
                color = dotColor
                isAntiAlias = true
            }
            val dotCenter = tileSize * 0.6f
            tileCanvas.drawCircle(Offset(dotCenter, dotCenter), dotRadius, dotPaint)

            val dotBrush = ShaderBrush(
                ImageShader(tileBitmap, TileMode.Repeated, TileMode.Repeated)
            )

            val fiberPath: Path?
            val fiberStroke: Stroke?
            val fiberColor = SumiColors.SumiWash.copy(alpha = 0.045f)
            if (showFibers && size.width > 0f && size.height > 0f) {
                fiberStroke = Stroke(width = 0.8.dp.toPx())
                fiberPath = Path().apply {
                    for (i in 0 until 18) {
                        val fx = ((i * 197 + 43) % 100) / 100f * size.width
                        val fy = ((i * 313 + 71) % 100) / 100f * size.height
                        val len = (10 + (i * 7) % 18).dp.toPx()
                        val dy = (((i % 5) - 2) * 2.5f).dp.toPx()
                        moveTo(fx, fy)
                        lineTo(fx + len, fy + dy)
                    }
                }
            } else {
                fiberPath = null
                fiberStroke = null
            }

            onDrawBehind {
                drawRect(color = baseColor)
                drawRect(brush = dotBrush)
                if (fiberPath != null && fiberStroke != null) {
                    drawPath(path = fiberPath, color = fiberColor, style = fiberStroke)
                }
            }
        }
    )

    fun updateParams(newBaseColor: Color, newDotColor: Color, newShowFibers: Boolean) {
        if (baseColor != newBaseColor || dotColor != newDotColor || showFibers != newShowFibers) {
            baseColor = newBaseColor
            dotColor = newDotColor
            showFibers = newShowFibers
            drawNode.invalidateDrawCache()
        }
    }
}
