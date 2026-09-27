package io.github.godman4242.macromaker

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The floating panel (move, start/stop, add, remove, hide) and the numbered tap targets. Every
 * window is an accessibility overlay, so no separate "draw over other apps" permission is needed.
 * While a run is going the targets turn click-through, so the taps reach the app underneath.
 */
class Controls(private val service: TapService) {
    private val context = ContextThemeWrapper(service, R.style.AppTheme)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val targetSize = dp(TARGET_DP)
    private val targets = mutableListOf<Pair<TextView, LayoutParams>>()
    private var targetsTouchable = true

    private val panelParams = overlayParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
        y = service.resources.displayMetrics.heightPixels / 3
    }
    private val playButton = panelButton(service.getString(R.string.panel_start), R.string.cd_start) { toggleRun() }.apply {
        maxLines = 1
        setAutoSizeTextTypeUniformWithConfiguration(8, 22, 1, TypedValue.COMPLEX_UNIT_SP) // "■ 12345" still fits
    }
    // Drag-only views: DragToMove calls performClick() for a touch that doesn't move.
    @SuppressLint("ClickableViewAccessibility")
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(context.getColor(R.color.panel_bg))
        }
        val handle = panelButton("✥", R.string.cd_move) {}
        addView(handle)
        addView(playButton)
        addView(panelButton("+", R.string.cd_add) { addTargetByUser() })
        addView(panelButton("−", R.string.cd_remove) { removeLastTarget() })
        addView(panelButton("✕", R.string.cd_hide) { hideByUser() })
        handle.setOnTouchListener(DragToMove(this, panelParams, overhang = 0) {})
    }

    private val message = TextView(context).apply {
        textSize = 15f
        setTextColor(context.getColor(R.color.panel_text))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(context.getColor(R.color.panel_bg))
        }
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val messageParams = overlayParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
        flags = flags or LayoutParams.FLAG_NOT_TOUCHABLE
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = dp(96)
    }
    private val hideMessage = Runnable { runCatching { wm.removeView(message) } }

    fun show() {
        wm.addView(panel, panelParams)
        PlanStore.load(service).points.forEach(::addTarget)
        refresh(service.runner)
    }

    fun remove() {
        (targets.map { it.first } + panel + message).forEach { runCatching { wm.removeView(it) } }
        targets.clear()
    }

    /**
     * Shows [text] for a few seconds near the bottom of the screen. Our own window rather than a
     * Toast: Android 13+ hides an app's toasts until it's allowed to post notifications.
     */
    fun say(text: String) {
        message.text = text
        if (message.parent == null) runCatching { wm.addView(message, messageParams) }
        message.removeCallbacks(hideMessage)
        message.postDelayed(hideMessage, MESSAGE_MS)
    }

    fun refresh(runner: TapRunner) {
        playButton.text = if (runner.running) service.getString(R.string.panel_stop, runner.tapsDone) else service.getString(R.string.panel_start)
        playButton.contentDescription = service.getString(if (runner.running) R.string.cd_stop else R.string.cd_start)
        setTargetsTouchable(!runner.running)
    }

    private fun hideByUser() {
        service.stopRun()
        PlanStore.setControlsHidden(service, true)
        service.hideControls()
    }

    private fun toggleRun() {
        if (service.runner.running) service.stopRun() else service.startRun()
    }

    @SuppressLint("ClickableViewAccessibility") // see DragToMove
    private fun addTarget(point: TapPoint) {
        val number = targets.size + 1
        val view = TextView(context).apply {
            text = "%d".format(number) // the phone's own digits
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(context.getColor(R.color.target_text))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.getColor(R.color.target_fill))
                setStroke(dp(2), context.getColor(R.color.target_stroke))
            }
            contentDescription = service.getString(R.string.cd_target, number)
        }
        val params = overlayParams(targetSize, targetSize).apply {
            x = (point.x - targetSize / 2f).roundToInt()
            y = (point.y - targetSize / 2f).roundToInt()
            if (!targetsTouchable) flags = flags or LayoutParams.FLAG_NOT_TOUCHABLE
        }
        view.setOnTouchListener(DragToMove(view, params, overhang = targetSize / 2, ::saveTargets))
        wm.addView(view, params)
        targets += view to params
    }

    private fun addTargetByUser() {
        if (targets.size >= TapPlan.MAX_POINTS) {
            say(service.resources.getQuantityString(R.plurals.too_many_points, TapPlan.MAX_POINTS, TapPlan.MAX_POINTS))
            return
        }
        addTarget(newTargetPoint())
        saveTargets()
    }

    private fun removeLastTarget() {
        val (view, _) = targets.removeLastOrNull() ?: return
        runCatching { wm.removeView(view) }
        saveTargets()
    }

    /** A target's tap point is the center of its window. */
    private fun saveTargets() = PlanStore.update(service) { plan ->
        plan.copy(points = targets.map { (_, p) -> TapPoint(p.x + targetSize / 2f, p.y + targetSize / 2f) })
    }

    private fun newTargetPoint(): TapPoint {
        val (width, height) = screenSize()
        val offset = dp(28) * (targets.size % 6)
        return TapPoint(width / 2f + offset, height / 2f + offset)
    }

    /** After a rotation, pulls the panel back on screen. Tap points stay put: they are screen positions. */
    fun keepPanelOnScreen() {
        val (width, height) = screenSize()
        panelParams.x = panelParams.x.coerceIn(0, max(0, width - panel.width))
        panelParams.y = panelParams.y.coerceIn(0, max(0, height - panel.height))
        runCatching { wm.updateViewLayout(panel, panelParams) }
    }

    /** The whole display in pixels — the space overlay windows and taps are positioned in. */
    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        Point().also { wm.defaultDisplay.getRealSize(it) }.let { it.x to it.y }
    }

    private fun setTargetsTouchable(touchable: Boolean) {
        if (touchable == targetsTouchable) return
        targetsTouchable = touchable
        targets.forEach { (view, params) ->
            params.flags = if (touchable) {
                params.flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or LayoutParams.FLAG_NOT_TOUCHABLE
            }
            view.alpha = if (touchable) 1f else 0.6f
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    private fun panelButton(label: String, description: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 22f
        setTextColor(context.getColor(R.color.panel_text))
        contentDescription = service.getString(description)
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        setBackgroundResource(ripple.resourceId)
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(PANEL_BUTTON_DP), dp(PANEL_BUTTON_DP))
    }

    private fun overlayParams(width: Int, height: Int) = LayoutParams(
        width, height,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    /**
     * Drags a whole overlay window, keeping all but [overhang] pixels of it on screen; a touch that
     * doesn't move counts as a click.
     */
    private inner class DragToMove(
        private val window: View,
        private val params: LayoutParams,
        private val overhang: Int,
        private val onDrop: () -> Unit,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(4)) moved = true
                    val (width, height) = screenSize()
                    params.x = (startX + dx).roundToInt()
                        .coerceIn(-overhang, max(-overhang, width - window.width + overhang))
                    params.y = (startY + dy).roundToInt()
                        .coerceIn(-overhang, max(-overhang, height - window.height + overhang))
                    runCatching { wm.updateViewLayout(window, params) }
                }
                MotionEvent.ACTION_UP -> if (moved) onDrop() else v.performClick()
            }
            return true
        }
    }

    private companion object {
        const val TARGET_DP = 48
        const val PANEL_BUTTON_DP = 52
        const val MESSAGE_MS = 3_000L
    }
}
