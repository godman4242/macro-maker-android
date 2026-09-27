package io.github.godman4242.macromaker

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapPlanTest {
    private val a = TapPoint(100f, 200f)
    private val b = TapPoint(300f, 400f)

    private fun plan(
        interval: Long = 100,
        jitter: Long = 0,
        maxTaps: Int = 0,
        maxSeconds: Int = 0,
        points: List<TapPoint> = listOf(a),
    ) = TapPlan(points, interval, jitter, maxTaps, maxSeconds)

    @Test fun `an interval below the floor is raised to it`() =
        assertEquals(TapPlan.MIN_INTERVAL_MS, plan(interval = 1).clamped().intervalMs)

    @Test fun `an absurd interval is capped`() =
        assertEquals(TapPlan.MAX_INTERVAL_MS, plan(interval = Long.MAX_VALUE).clamped().intervalMs)

    @Test fun `jitter can never exceed the interval`() =
        assertEquals(50L, plan(interval = 50, jitter = 500).clamped().jitterMs)

    @Test fun `negative jitter and limits become zero`() {
        val p = plan(jitter = -5, maxTaps = -3, maxSeconds = -1).clamped()
        assertEquals(0L, p.jitterMs)
        assertEquals(0, p.maxTaps)
        assertEquals(0, p.maxSeconds)
    }

    @Test fun `off-screen and non-number points are dropped`() {
        val junk = listOf(TapPoint(-1f, 5f), TapPoint(Float.NaN, 1f), TapPoint(1f, Float.POSITIVE_INFINITY))
        assertEquals(listOf(a, b), plan(points = listOf(a) + junk + b).clamped().points)
    }

    @Test fun `at most MAX_POINTS points are kept`() {
        val many = List(50) { TapPoint(it.toFloat(), 1f) }
        assertEquals(TapPlan.MAX_POINTS, plan(points = many).clamped().points.size)
    }

    @Test fun `no jitter means the exact interval every time`() {
        val p = plan(interval = 120).clamped()
        repeat(100) { assertEquals(120L, p.nextDelayMs(Random(it))) }
    }

    @Test fun `jitter covers interval plus or minus jitter and nothing else`() {
        val p = plan(interval = 100, jitter = 5).clamped()
        val rnd = Random(42)
        assertEquals((95L..105L).toSet(), List(2000) { p.nextDelayMs(rnd) }.toSet())
    }

    @Test fun `jitter never pushes the delay below the floor`() {
        val p = plan(interval = TapPlan.MIN_INTERVAL_MS, jitter = TapPlan.MIN_INTERVAL_MS).clamped()
        val rnd = Random(7)
        repeat(2000) { assertTrue(p.nextDelayMs(rnd) >= TapPlan.MIN_INTERVAL_MS) }
    }

    @Test fun `tap limit stops after exactly maxTaps`() {
        val p = plan(maxTaps = 3)
        assertNull(p.limitReached(tapsDone = 2, elapsedMs = 0))
        assertEquals(StopReason.TAP_LIMIT, p.limitReached(tapsDone = 3, elapsedMs = 0))
    }

    @Test fun `time limit stops once the seconds have passed`() {
        val p = plan(maxSeconds = 2)
        assertNull(p.limitReached(tapsDone = 0, elapsedMs = 1_999))
        assertEquals(StopReason.TIME_LIMIT, p.limitReached(tapsDone = 0, elapsedMs = 2_000))
    }

    @Test fun `zero limits run until stopped`() =
        assertNull(plan().limitReached(tapsDone = 1_000_000, elapsedMs = 1_000_000_000))

    @Test fun `points are tapped in order and loop`() {
        val p = plan(points = listOf(a, b))
        assertEquals(listOf(a, b, a, b, a), (0..4).map(p::pointFor))
    }
}
