package com.easit.aiscanner.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.core.util.Consumer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.easit.aiscanner.R

/**
 * A Compose UI shown in its own [WindowManager] overlay window (TYPE_APPLICATION_OVERLAY) from a
 * Service, which has no Activity to host Compose.
 *
 * It supplies what Compose expects from an Activity: a lifecycle (RESUMED while shown), saved
 * state, a ViewModelStore, and an OnBackPressedDispatcher so `BackHandler` works. The Back key
 * only reaches a *focusable* window (see [fullScreenParams]); unfocusable windows such as the
 * bubble never receive it.
 *
 * Usage: create once per window, [setContent], then [show]/[hide] as often as needed, and
 * [destroy] when the owning service stops. Call everything on the main thread.
 */
class OverlayWindow(context: Context) :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    OnBackPressedDispatcherOwner {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val onBackPressedDispatcher: OnBackPressedDispatcher =
        OnBackPressedDispatcher(null, Consumer<Boolean> { backInvokedBridge?.update() })

    /**
     * Apps targeting Android 16 no longer receive KEYCODE_BACK; the system calls the window's
     * OnBackInvokedCallback instead. Bridges that to [onBackPressedDispatcher] (API 33+).
     */
    private val backInvokedBridge: BackInvokedBridge? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) BackInvokedBridge() else null

    /**
     * Whether the window has been added. Tracked here rather than read from
     * `root.isAttachedToWindow`, which only turns true on the first traversal after `addView`:
     * an [update] or [hide] right after [show] would otherwise be lost.
     */
    private var added = false

    /** Themed so any View-system attributes (ripples, text appearance) resolve. */
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_AIScanner)

    private val composeView = ComposeView(themedContext).apply {
        // The window comes and goes; keep the composition until destroy().
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@OverlayWindow))
    }

    /** Root view: routes the Back key to [onBackPressedDispatcher]. */
    private val root = object : FrameLayout(themedContext) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && onBackPressedDispatcher.hasEnabledCallbacks()) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    onBackPressedDispatcher.onBackPressed()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** The parameters of the window while shown (also after [hide], for the next [show]). */
    var layoutParams: WindowManager.LayoutParams? = null
        private set

    val isShowing: Boolean get() = added

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeOnBackPressedDispatcherOwner(this)
        root.addView(composeView)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                backInvokedBridge?.update()
            }

            override fun onViewDetachedFromWindow(view: View) {
                backInvokedBridge?.update()
            }
        })
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    /**
     * Adds the window (or moves it, if already shown).
     * @return false if the system refused it (e.g. the overlay permission was revoked)
     */
    fun show(params: WindowManager.LayoutParams): Boolean {
        check(lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) { "OverlayWindow destroyed" }
        layoutParams = params
        if (added) {
            runCatching { windowManager.updateViewLayout(root, params) }
            return true
        }
        return try {
            windowManager.addView(root, params)
            added = true
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Applies new [params] (position, size, flags) to the shown window. */
    fun update(params: WindowManager.LayoutParams) {
        layoutParams = params
        if (added) runCatching { windowManager.updateViewLayout(root, params) }
    }

    /** Removes the window but keeps the composition and its state for the next [show]. */
    fun hide() {
        if (added) {
            added = false
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            runCatching { windowManager.removeViewImmediate(root) }
        }
    }

    /** Removes the window for good and disposes the composition. */
    fun destroy() {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    /**
     * Registers an [OnBackInvokedCallback] on this window's dispatcher while the window is shown
     * and [onBackPressedDispatcher] has enabled callbacks (so Back reaches `BackHandler`s on
     * Android 16+), and unregisters it otherwise. On earlier versions, without the manifest
     * opt-in, the system ignores the callback and the KEYCODE_BACK path in [root] applies.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private inner class BackInvokedBridge {
        private val callback = OnBackInvokedCallback { onBackPressedDispatcher.onBackPressed() }
        private var registeredOn: OnBackInvokedDispatcher? = null

        fun update() {
            val target = if (added && root.isAttachedToWindow && onBackPressedDispatcher.hasEnabledCallbacks()) {
                root.findOnBackInvokedDispatcher()
            } else {
                null
            }
            val current = registeredOn
            if (current != null && current !== target) {
                runCatching { current.unregisterOnBackInvokedCallback(callback) }
                registeredOn = null
            }
            if (target != null && registeredOn == null) {
                target.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                registeredOn = target
            }
        }
    }

    companion object {
        /**
         * A full-screen overlay drawn under the status/navigation bars and display cutout.
         * Compose `WindowInsets` (e.g. safeDrawing) report the bars, so pad content with them.
         *
         * @param focusable receive Back and IME input; use for the capture editor
         */
        fun fullScreenParams(focusable: Boolean): WindowManager.LayoutParams =
            baseParams(
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                focusable = focusable,
            )

        /**
         * A wrap-content overlay positioned by [x]/[y] from the top-left of the screen, for the
         * bubble and other small floating UI. Never focusable, so it doesn't steal input.
         */
        fun floatingParams(x: Int, y: Int): WindowManager.LayoutParams =
            baseParams(
                width = WindowManager.LayoutParams.WRAP_CONTENT,
                height = WindowManager.LayoutParams.WRAP_CONTENT,
                focusable = false,
            ).apply {
                this.x = x
                this.y = y
            }

        private fun baseParams(width: Int, height: Int, focusable: Boolean) =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Extend under every system bar; content handles insets itself.
                    setFitInsetsTypes(0)
                }
            }
    }
}
