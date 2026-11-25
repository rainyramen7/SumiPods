package com.sumipods.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sumipods.app.R

/**
 * Option 4A — MUJI × Ma (間) Washi Palette
 */
object SumiColors {
    val WashiPaper = Color(0xFFF5F2EB)       // Warm unbleached washi background
    val WashiElevated = Color(0xFFFAF8F3)    // Clean elevated washi sheet (popup / settings)
    val WashiPod = Color(0xFFFBFAF6)         // Matte ceramic ivory for AirPods hardware
    val WashiStone = Color(0xFFE5E0D5)       // Recessed washi notch & subtle pill surface
    val Hairline = Color(0xFFDCD7CC)         // Calm 1dp structural divider
    val HairlineStrong = Color(0xFFC4BDB0)   // Interactive border & blueprint registration ticks

    val SumiDeep = Color(0xFF171614)         // Rich sumi-e ink core
    val SumiInk = Color(0xFF262420)          // Primary sumi charcoal
    val SumiWash = Color(0xFF4A463F)         // Hardware stroke / medium wash
    val SumiMuted = Color(0xFF6D685E)        // Secondary text
    val SumiFaint = Color(0xFF948E82)        // Japanese micro-labels & captions

    val HankoVermilion = Color(0xFFC73E2B)   // Shu-iro vermilion seal red (stamp & low <= 20%)
    val HankoDeep = Color(0xFF9C2A1B)        // Deep vermilion ink pooling
    val MossGreen = Color(0xFF4A6B53)        // Quiet healthy case LED
    val AmberOchre = Color(0xFFB57B24)       // Warm charging ochre accent
}

/**
 * F3 — Zen Maru Gothic (丸ゴシック): softly rounded Japanese ceramic-craft typography.
 */
val ZenMaruFontFamily = FontFamily(
    Font(R.font.zen_maru_gothic_regular, FontWeight.Normal),
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold)
)

private val SumiLightColorScheme = lightColorScheme(
    primary = SumiColors.SumiDeep,
    onPrimary = SumiColors.WashiPaper,
    secondary = SumiColors.HankoVermilion,
    onSecondary = SumiColors.WashiPaper,
    background = SumiColors.WashiPaper,
    onBackground = SumiColors.SumiDeep,
    surface = SumiColors.WashiElevated,
    onSurface = SumiColors.SumiDeep,
    surfaceVariant = SumiColors.WashiStone,
    onSurfaceVariant = SumiColors.SumiMuted,
    outline = SumiColors.Hairline,
    error = SumiColors.HankoVermilion,
    onError = SumiColors.WashiPaper
)

val SumiTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = 2.2.sp,
        color = SumiColors.SumiDeep
    ),
    titleMedium = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 1.6.sp,
        color = SumiColors.SumiDeep
    ),
    bodyMedium = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = SumiColors.SumiMuted
    ),
    labelLarge = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.4.sp,
        color = SumiColors.SumiDeep
    ),
    labelMedium = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = SumiColors.SumiMuted
    ),
    labelSmall = TextStyle(
        fontFamily = ZenMaruFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.8.sp,
        color = SumiColors.SumiFaint
    )
)

@Composable
fun SumiPodsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SumiLightColorScheme,
        typography = SumiTypography,
        content = content
    )
}
