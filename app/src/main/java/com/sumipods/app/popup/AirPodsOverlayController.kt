package com.sumipods.app.popup

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sumipods.app.ble.AirPodsRepository
import com.sumipods.app.ble.AirPodsState
import com.sumipods.app.ble.DemoPreset
import com.sumipods.app.ble.PermissionHelper
import com.sumipods.app.ui.theme.SumiPodsTheme

/**
 * Displays the MUJI × Washi AirPods auto-popup directly over any app via WindowManager
 * (TYPE_APPLICATION_OVERLAY) without pausing or stealing keyboard focus from the user's current app.
 */
class AirPodsOverlayController(
    context: Context,
    private val repository: AirPodsRepository
) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun show(): Boolean {
        if (!PermissionHelper.canDrawOverlays(appContext)) return false

        var shown = false
        val runnable = Runnable {
            if (activeView != null) {
                shown = true
                return@Runnable
            }
            var owner: OverlayLifecycleOwner? = null
            var composeView: ComposeView? = null
            try {
                owner = OverlayLifecycleOwner().also { it.onCreate() }
                composeView = ComposeView(appContext).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setContent {
                        SumiPodsTheme {
                            val state by repository.state.collectAsStateWithLifecycle()
                            val prefs by repository.preferences.collectAsStateWithLifecycle()
                            val fallbackDemo = remember {
                                AirPodsState.demo(DemoPreset.CHARGING).copy(isLidOpen = true)
                            }
                            val displayState = if (state.hasAnyBattery) {
                                state.copy(isLidOpen = true)
                            } else {
                                fallbackDemo
                            }
                            AirPodsPopupOverlay(
                                state = displayState,
                                showJapaneseLabels = prefs.showJapaneseLabels,
                                dimBackground = true,
                                onDismissComplete = { dismiss() }
                            )
                        }
                    }
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM
                }

                windowManager.addView(composeView, params)
                owner.onResume()
                activeView = composeView
                lifecycleOwner = owner
                shown = true
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to attach overlay window", t)
                runCatching { composeView?.disposeComposition() }
                runCatching { owner?.onDestroy() }
                activeView = null
                lifecycleOwner = null
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
            return shown
        } else {
            mainHandler.post(runnable)
            return true
        }
    }

    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            val view = activeView ?: return@post
            activeView = null
            val owner = lifecycleOwner
            lifecycleOwner = null

            runCatching { view.disposeComposition() }
            if (view.isAttachedToWindow) {
                runCatching { windowManager.removeViewImmediate(view) }
                    .onFailure { runCatching { windowManager.removeView(view) } }
            }
            runCatching { owner?.onDestroy() }
        }
    }

    private class OverlayLifecycleOwner :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val viewModelStore: ViewModelStore = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun onResume() {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
        }

        fun onDestroy() {
            if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED &&
                lifecycleRegistry.currentState != Lifecycle.State.DESTROYED
            ) {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
            viewModelStore.clear()
        }
    }

    companion object {
        private const val TAG = "AirPodsOverlay"
    }
}
