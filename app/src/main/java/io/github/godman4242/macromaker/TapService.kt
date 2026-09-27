package io.github.godman4242.macromaker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * The only part holding the Accessibility permission. It taps the screen at the user's points and
 * draws the floating controls. It cannot read the screen: canRetrieveWindowContent is false and it
 * subscribes to no accessibility events.
 */
class TapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    val runner = TapRunner(handler, ::dispatchTap, ::onRunChanged)
    private var controls: Controls? = null
    private var wasRunning = false

    /** Shows the panel unless the user hid it with ✕ — reboots and app updates reconnect the service too. */
    override fun onServiceConnected() {
        instance = this
        if (!PlanStore.controlsHidden(this)) showControls()
    }

    fun showControls() {
        if (controls == null) controls = Controls(this).also { it.show() }
    }

    fun hideControls() {
        controls?.remove()
        controls = null
    }

    fun startRun(plan: TapPlan = PlanStore.load(this)): StopReason? = runner.start(plan)

    fun stopRun() = runner.stop(StopReason.USER)

    private fun onRunChanged() {
        controls?.refresh(runner)
        val justStopped = wasRunning && !runner.running
        wasRunning = runner.running
        if (justStopped || runner.lastStop == StopReason.NO_POINTS) {
            messageFor(runner.lastStop)?.let { controls?.say(it) }
        }
    }

    private fun messageFor(reason: StopReason?): String? = when (reason) {
        StopReason.TAP_LIMIT -> resources.getQuantityString(R.plurals.stopped_tap_limit, runner.tapsDone, runner.tapsDone)
        StopReason.TIME_LIMIT -> getString(R.string.stopped_time_limit)
        StopReason.NO_POINTS -> getString(R.string.stopped_no_points)
        StopReason.DISPATCH_FAILED -> getString(R.string.stopped_dispatch_failed)
        StopReason.USER, StopReason.SERVICE_OFF, null -> null
    }

    private fun dispatchTap(point: TapPoint, onDone: (Boolean) -> Unit): Boolean {
        val path = Path().apply { moveTo(point.x, point.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
        }, handler)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controls?.keepPanelOnScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // subscribes to none

    override fun onInterrupt() = Unit // means "stop spoken feedback"; this service gives none

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        runner.stop(StopReason.SERVICE_OFF)
        hideControls()
        instance = null
    }

    companion object {
        /** Set while Android has the service switched on. */
        @Volatile var instance: TapService? = null
            private set

        private const val TAP_DURATION_MS = 10L
    }
}
