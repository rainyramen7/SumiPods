package com.sumipods.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.ZenMaruFontFamily

/**
 * Option 4A signature Shu-iro vermilion Hanko seal badge.
 */
@Composable
fun HankoSeal(
    showJapanese: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val sealColor = if (isActive) SumiColors.HankoVermilion else SumiColors.SumiFaint
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(color = sealColor)
            drawCircle(
                color = SumiColors.WashiPaper.copy(alpha = 0.45f),
                radius = this.size.minDimension * 0.41f,
                style = Stroke(width = 0.9.dp.toPx())
            )
        }
        Text(
            text = if (showJapanese) "音" else "●",
            fontFamily = ZenMaruFontFamily,
            color = SumiColors.WashiPaper,
            fontSize = if (showJapanese) 12.sp else 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
