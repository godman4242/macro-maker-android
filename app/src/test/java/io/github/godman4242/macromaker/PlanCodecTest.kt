package io.github.godman4242.macromaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanCodecTest {
    @Test fun `a plan survives a save and load`() {
        val p = TapPlan(listOf(TapPoint(10.5f, 20f), TapPoint(30f, 40.25f)), 250, 30, 100, 60)
        assertEquals(p, PlanCodec.decode(PlanCodec.encode(p)))
    }

    @Test fun `a plan with no points survives a save and load`() {
        val p = TapPlan(emptyList(), 100, 0, 0, 0)
        assertEquals(p, PlanCodec.decode(PlanCodec.encode(p)))
    }

    @Test fun `loading clamps out-of-range values`() =
        assertEquals(TapPlan.MIN_INTERVAL_MS, PlanCodec.decode("1|5|0|0|0|")?.intervalMs)

    @Test fun `garbage is rejected instead of half-loaded`() {
        val garbage = listOf(
            null, "", "junk", "1|100|0|0", "2|100|0|0|0|", "1|abc|0|0|0|",
            "1|99999999999999999999|0|0|0|", "1|100|0|0|0|1,2;3", "1|100|0|0|0|1,2,3",
            "1|100|0|0|0|x,y", "1|100|0|0|0|1,2;", "1|100|0|0|0||",
        )
        garbage.forEach { assertNull("should reject: $it", PlanCodec.decode(it)) }
    }
}
