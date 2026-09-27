package io.github.godman4242.macromaker

import kotlin.random.Random

/** A spot to tap, in screen pixels. */
data class TapPoint(val x: Float, val y: Float)

enum class StopReason { TAP_LIMIT, TIME_LIMIT, USER, SERVICE_OFF, NO_POINTS, DISPATCH_FAILED }

/**
 * What one run does: tap [points] in order (looping), one tap every [intervalMs] ± [jitterMs].
 * A limit of 0 means "no limit". Anything loaded or typed in goes through [clamped] before a run.
 */
data class TapPlan(
    val points: List<TapPoint>,
    val intervalMs: Long,
    val jitterMs: Long,
    val maxTaps: Int,
    val maxSeconds: Int,
) {
    fun clamped(): TapPlan {
        val interval = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        return copy(
            points = points.filter { it.x.isFinite() && it.y.isFinite() && it.x >= 0f && it.y >= 0f }
                .take(MAX_POINTS),
            intervalMs = interval,
            jitterMs = jitterMs.coerceIn(0L, interval),
            maxTaps = maxTaps.coerceAtLeast(0),
            maxSeconds = maxSeconds.coerceIn(0, MAX_SECONDS),
        )
    }

    /** Wait before the next tap: interval ± jitter, never below [MIN_INTERVAL_MS]. */
    fun nextDelayMs(random: Random): Long {
        val offset = if (jitterMs == 0L) 0L else random.nextLong(-jitterMs, jitterMs + 1)
        return (intervalMs + offset).coerceAtLeast(MIN_INTERVAL_MS)
    }

    fun limitReached(tapsDone: Int, elapsedMs: Long): StopReason? = when {
        maxTaps > 0 && tapsDone >= maxTaps -> StopReason.TAP_LIMIT
        maxSeconds > 0 && elapsedMs >= maxSeconds * 1000L -> StopReason.TIME_LIMIT
        else -> null
    }

    fun pointFor(tapIndex: Int): TapPoint = points[tapIndex % points.size]

    companion object {
        const val MIN_INTERVAL_MS = 20L
        const val MAX_INTERVAL_MS = 3_600_000L
        const val MAX_POINTS = 20
        const val MAX_SECONDS = 7 * 24 * 3600
        val DEFAULT = TapPlan(emptyList(), intervalMs = 100, jitterMs = 0, maxTaps = 0, maxSeconds = 0)
    }
}

/**
 * Saves a plan as one line: `1|interval|jitter|maxTaps|maxSeconds|x,y;x,y`.
 * Decoding is all-or-nothing: any malformed field returns null, never a half-loaded plan.
 */
object PlanCodec {
    private const val VERSION = "1"

    fun encode(plan: TapPlan): String = listOf(
        VERSION, plan.intervalMs, plan.jitterMs, plan.maxTaps, plan.maxSeconds,
        plan.points.joinToString(";") { "${it.x},${it.y}" },
    ).joinToString("|")

    fun decode(text: String?): TapPlan? {
        val f = text?.split('|') ?: return null
        if (f.size != 6 || f[0] != VERSION) return null
        val points = if (f[5].isEmpty()) emptyList() else f[5].split(';').map { pair ->
            val xy = pair.split(',')
            if (xy.size != 2) return null
            TapPoint(xy[0].toFloatOrNull() ?: return null, xy[1].toFloatOrNull() ?: return null)
        }
        return TapPlan(
            points = points,
            intervalMs = f[1].toLongOrNull() ?: return null,
            jitterMs = f[2].toLongOrNull() ?: return null,
            maxTaps = f[3].toIntOrNull() ?: return null,
            maxSeconds = f[4].toIntOrNull() ?: return null,
        ).clamped()
    }
}
