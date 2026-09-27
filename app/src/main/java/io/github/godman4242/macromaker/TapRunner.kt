package io.github.godman4242.macromaker

import android.os.Handler
import android.os.SystemClock
import kotlin.random.Random

/**
 * Runs a [TapPlan] on the main thread, one tap at a time: the next tap is scheduled only after
 * the previous gesture finished.
 */
class TapRunner(
    private val handler: Handler,
    private val dispatch: (point: TapPoint, onDone: (landed: Boolean) -> Unit) -> Boolean,
    private val onChange: () -> Unit,
) {
    var running = false
        private set
    var tapsDone = 0
        private set
    var lastStop: StopReason? = null
        private set

    private var plan = TapPlan.DEFAULT
    private var startedAt = 0L
    private var runId = 0
    private val token = Any()

    /** Starts [newPlan], replacing any run in progress. Returns why it refused, or null. */
    fun start(newPlan: TapPlan): StopReason? {
        stop(StopReason.USER)
        val p = newPlan.clamped()
        if (p.points.isEmpty()) {
            lastStop = StopReason.NO_POINTS
            onChange()
            return StopReason.NO_POINTS
        }
        plan = p
        tapsDone = 0
        lastStop = null
        running = true
        startedAt = SystemClock.elapsedRealtime()
        val id = ++runId
        onChange()
        handler.postAtTime({ tapNext(id) }, token, SystemClock.uptimeMillis() + START_DELAY_MS)
        return null
    }

    fun stop(reason: StopReason) {
        if (!running) return
        running = false
        lastStop = reason
        runId++
        handler.removeCallbacksAndMessages(token)
        onChange()
    }

    // Stale runs are fenced twice, each pinned by a test: stop() removes every pending tapNext,
    // and a finished gesture from an older run is ignored below (never counted, never rescheduled).
    private fun tapNext(id: Int) {
        if (stopIfLimitReached()) return
        val accepted = dispatch(plan.pointFor(tapsDone)) { landed ->
            if (id != runId) return@dispatch
            if (landed) tapsDone++
            onChange()
            if (!stopIfLimitReached()) {
                handler.postAtTime({ tapNext(id) }, token, SystemClock.uptimeMillis() + plan.nextDelayMs(Random))
            }
        }
        if (!accepted) stop(StopReason.DISPATCH_FAILED)
    }

    private fun stopIfLimitReached(): Boolean {
        val reason = plan.limitReached(tapsDone, SystemClock.elapsedRealtime() - startedAt) ?: return false
        stop(reason)
        return true
    }

    companion object {
        /** Gives the tap targets time to turn click-through before the first tap lands on the spot they mark. */
        const val START_DELAY_MS = 300L
    }
}
