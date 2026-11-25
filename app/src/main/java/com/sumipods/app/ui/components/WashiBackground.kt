package com.sumipods.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sumipods.app.ui.theme.SumiColors

/**
 * Draws the subtle Option 4A warm washi paper background with fine architectural dot-grid
 * and whisper-light organic washi fiber flecks.
 */
fun Modifier.washiPaperBackground(
    baseColor: Color = SumiColors.WashiPaper,
    dotColor: Color = SumiColors.SumiDeep.copy(alpha = 0.11f),
    showFibers: Boolean = true
): Modifier = this.drawBehind {
    drawRect(color = baseColor)

    val spacing = 14.dp.toPx()
    val dotRadius = 0.85.dp.toPx()

    var y = spacing * 0.6f
    while (y < size.height) {
        var x = spacing * 0.6f
        while (x < size.width) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            x += spacing
        }
        y += spacing
    }

    // Subtle organic washi mulberry fibers (deterministic, ultra-restrained)
    if (showFibers && size.width > 0f && size.height > 0f) {
        val fiberColor = SumiColors.SumiWash.copy(alpha = 0.045f)
        val stroke = 0.8.dp.toPx()
        for (i in 0 until 18) {
            val fx = ((i * 197 + 43) % 100) / 100f * size.width
            val fy = ((i * 313 + 71) % 100) / 100f * size.height
            val len = (10 + (i * 7) % 18).dp.toPx()
            val dy = (((i % 5) - 2) * 2.5f).dp.toPx()
            drawLine(
                color = fiberColor,
                start = Offset(fx, fy),
                end = Offset(fx + len, fy + dy),
                strokeWidth = stroke
            )
        }
    }
}
