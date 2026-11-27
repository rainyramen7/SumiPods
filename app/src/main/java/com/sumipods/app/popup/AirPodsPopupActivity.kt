package com.sumipods.app.popup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumipods.app.SumiPodsApplication
import com.sumipods.app.ble.AirPodsState
import com.sumipods.app.ble.DemoPreset
import com.sumipods.app.ui.components.AirPodsPro2Stage
import com.sumipods.app.ui.components.AirPodsSumiBatteryList
import com.sumipods.app.ui.components.HankoSeal
import com.sumipods.app.ui.components.SumiIcons
import com.sumipods.app.ui.components.washiPaperBackground
import com.sumipods.app.ui.theme.SumiColors
import com.sumipods.app.ui.theme.SumiPodsTheme
import com.sumipods.app.ui.theme.ZenMaruFontFamily
import kotlinx.coroutines.delay

/**
 * Fallback translucent Activity for launching the popup from notifications when system
 * overlay permission is not granted.
 */
class AirPodsPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as SumiPodsApplication).repository

        setContent {
            SumiPodsTheme {
                val state by repository.state.collectAsStateWithLifecycle()
                val prefs by repository.preferences.collectAsStateWithLifecycle()
                val fallbackDemo = remember { AirPodsState.demo(DemoPreset.CHARGING).copy(isLidOpen = true) }

                val displayState = if (state.hasAnyBattery) {
                    state.copy(isLidOpen = true)
                } else {
                    fallbackDemo
                }

                AirPodsPopupOverlay(
                    state = displayState,
                    showJapaneseLabels = prefs.showJapaneseLabels,
                    dimBackground = false,
                    onDismissComplete = {
                        finish()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(0, 0)
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, AirPodsPopupActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
    }
}

@Composable
fun AirPodsPopupOverlay(
    state: AirPodsState,
    showJapaneseLabels: Boolean,
    dimBackground: Boolean = true,
    onDismissComplete: () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    var dismissing by remember { mutableStateOf(false) }
    val dismissThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }

    fun dismiss() {
        if (!dismissing) {
            dismissing = true
            visibleState.targetState = false
        }
    }

    LaunchedEffect(Unit) {
        visibleState.targetState = true
    }

    // Auto-dismiss calmly after 9 seconds — keyed ONLY on target visibility (never on BLE timestamps!)
    LaunchedEffect(visibleState.targetState) {
        if (visibleState.targetState && !dismissing) {
            delay(9_000L)
            dismiss()
        }
    }

    // Complete dismissal immediately when exit transition finishes (honors system Animator Duration Scale)
    LaunchedEffect(dismissing, visibleState.isIdle, visibleState.currentState) {
        if (dismissing && visibleState.isIdle && !visibleState.currentState) {
            onDismissComplete()
        }
    }

    val scrimAlphaState = animateFloatAsState(
        targetValue = if (visibleState.targetState && dimBackground) 0.36f else 0f,
        animationSpec = tween(220),
        label = "popup_scrim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = scrimAlphaState.value
                if (alpha > 0f) {
                    drawRect(Color.Black.copy(alpha = alpha))
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::dismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(170))
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .shadow(elevation = 16.dp, shape = shape, spotColor = Color.Black.copy(alpha = 0.2f))
                    .clip(shape)
                    .washiPaperBackground(baseColor = SumiColors.WashiElevated)
                    .border(BorderStroke(1.dp, SumiColors.HairlineStrong), shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .pointerInput(dismissThresholdPx) {
                        var dragAccumulated = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAccumulated > dismissThresholdPx) dismiss()
                                dragAccumulated = 0f
                            },
                            onDragCancel = {
                                dragAccumulated = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0f) dragAccumulated += dragAmount
                            }
                        )
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                // Subtle MUJI drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SumiColors.HairlineStrong)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HankoSeal(
                            showJapanese = showJapaneseLabels,
                            isActive = true,
                            size = 24.dp
                        )
                        Column {
                            Text(
                                text = state.model.displayName,
                                fontFamily = ZenMaruFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp,
                                color = SumiColors.SumiDeep
                            )
                            Text(
                                text = if (showJapaneseLabels) {
                                    "${state.statusTextJp} · ${state.statusTextEn}"
                                } else {
                                    state.statusTextEn
                                },
                                fontFamily = ZenMaruFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.6.sp,
                                color = SumiColors.SumiMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = ::dismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = SumiIcons.Close,
                            contentDescription = "Dismiss",
                            tint = SumiColors.SumiMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)

                AirPodsPro2Stage(
                    isLidOpen = state.isLidOpen,
                    left = state.left,
                    right = state.right,
                    caseBattery = state.caseBattery,
                    hasAnyBattery = state.hasAnyBattery,
                    showJapaneseLabels = showJapaneseLabels,
                    compact = true,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                HorizontalDivider(color = SumiColors.Hairline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                AirPodsSumiBatteryList(
                    left = state.left,
                    right = state.right,
                    caseBattery = state.caseBattery,
                    showJapaneseLabels = showJapaneseLabels,
                    rowSpacing = 14.dp,
                    barHeight = 13.dp
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun AirPodsPopupOverlayPreview() {
    SumiPodsTheme {
        AirPodsPopupOverlay(
            state = AirPodsState.demo(DemoPreset.CHARGING),
            showJapaneseLabels = true,
            dimBackground = true,
            onDismissComplete = {}
        )
    }
}
