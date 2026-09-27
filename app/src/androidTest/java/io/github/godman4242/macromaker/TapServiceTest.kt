package io.github.godman4242.macromaker

import android.app.UiAutomation
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real taps on a real button: CounterActivity (debug build only) counts every click it receives,
 * so these tests prove taps LAND — not just that the service thinks it sent them.
 */
@RunWith(AndroidJUnit4::class)
class TapServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var service: TapService

    @Before fun turnTheServiceOn() {
        shell("settings put global window_animation_scale 0")
        shell("settings put global transition_animation_scale 0")
        PlanStore.setControlsHidden(context, false)
        reconnect()
        onMain { service.hideControls(); CounterActivity.count = 0 }
    }

    /**
     * Off, then on: the runner force-stops the app at launch, and Android only re-attaches a
     * force-stopped app's service when this setting CHANGES — re-putting the same value does nothing.
     */
    private fun reconnect() {
        shell("settings delete secure enabled_accessibility_services")
        waitFor("the tap service to disconnect") { if (TapService.instance == null) true else null }
        val name = ComponentName(context, TapService::class.java).flattenToString()
        shell("settings put secure enabled_accessibility_services $name")
        shell("settings put secure accessibility_enabled 1")
        service = waitFor("the tap service to connect") { TapService.instance }
    }

    @Test fun fiftyTapsLandExactlyFifty() = withButton { target ->
        onMain { service.startRun(TapPlan(listOf(target), 40, 0, 50, 0)) }
        waitForRunToEnd()
        assertEquals(50, onMain { CounterActivity.count })
        assertEquals(50, onMain { service.runner.tapsDone })
        assertEquals(StopReason.TAP_LIMIT, onMain { service.runner.lastStop })
    }

    // Three stops at the fastest interval: a tap is in flight most of the time, so at least one
    // stop lands mid-gesture — the case where a stopped run's finished tap could restart it.
    @Test fun stopMidRunStopsTheTapping() = withButton { target ->
        repeat(3) {
            val before = onMain { CounterActivity.count }
            onMain { service.startRun(TapPlan(listOf(target), TapPlan.MIN_INTERVAL_MS, 0, 0, 0)) }
            waitFor("3 more taps") { onMain { CounterActivity.count }.takeIf { it >= before + 3 } }
            onMain { service.stopRun() }
            assertNoMoreTaps()
            assertFalse(onMain { service.runner.running })
            assertEquals(StopReason.USER, onMain { service.runner.lastStop })
        }
    }

    @Test fun switchingTheServiceOffMidRunStopsTheTapping() = withButton { target ->
        onMain { service.startRun(TapPlan(listOf(target), 40, 0, 0, 0)) }
        waitFor("5 taps") { onMain { CounterActivity.count }.takeIf { it >= 5 } }
        val runner = service.runner
        shell("settings delete secure enabled_accessibility_services")
        waitFor("the service to switch off") { if (TapService.instance == null) true else null }
        assertNoMoreTaps()
        assertEquals(StopReason.SERVICE_OFF, onMain { runner.lastStop })
    }

    @Test fun spammingStartAndStopLeavesNoGhostTaps() = withButton { target ->
        onMain {
            repeat(25) {
                service.startRun(TapPlan(listOf(target), 40, 0, 0, 0))
                service.stopRun()
            }
        }
        Thread.sleep(1_000)
        assertEquals("a stopped run still tapped", 0, onMain { CounterActivity.count })
        onMain { service.startRun(TapPlan(listOf(target), 40, 0, 10, 0)) }
        waitForRunToEnd()
        assertEquals(10, onMain { CounterActivity.count })
    }

    @Test fun aTapTargetSittingOnTheButtonLetsTheTapsThrough() = withButton { target ->
        onMain {
            PlanStore.update(context) { TapPlan(listOf(target), 40, 0, 5, 0) }
            service.showControls()
        }
        Thread.sleep(500)
        onMain { service.startRun() }
        waitForRunToEnd()
        assertEquals(5, onMain { CounterActivity.count })
        onMain { service.hideControls() }
    }

    // Reboots and app updates reconnect the service; a panel the user closed must stay closed.
    @Test fun aPanelTheUserClosedStaysClosedWhenAndroidReconnects() {
        PlanStore.setControlsHidden(context, true)
        reconnect()
        Thread.sleep(500)
        assertEquals(0, overlayWindows())
        PlanStore.setControlsHidden(context, false)
        reconnect()
        waitFor("the panel to reappear") { overlayWindows().takeIf { it > 0 } }
    }

    @Test fun startingWithNoPointsRefusesToRun() {
        assertEquals(StopReason.NO_POINTS, onMain { service.startRun(TapPlan.DEFAULT) })
        assertFalse(onMain { service.runner.running })
    }

    private fun withButton(test: (TapPoint) -> Unit) =
        ActivityScenario.launch(CounterActivity::class.java).use { scenario ->
            val target = waitFor("the button to lay out") {
                var p: TapPoint? = null
                scenario.onActivity { p = it.buttonCenterOnScreen() }
                p
            }
            test(target)
        }

    private fun waitForRunToEnd() {
        waitFor("the run to end", timeoutMs = 20_000) { if (onMain { service.runner.running }) null else true }
        Thread.sleep(300) // the last tap's click handler runs just after the gesture completes
    }

    private fun assertNoMoreTaps() {
        Thread.sleep(300) // a gesture already in flight may still land
        val settled = onMain { CounterActivity.count }
        Thread.sleep(1_000)
        assertEquals("taps kept landing after the run stopped", settled, onMain { CounterActivity.count })
        assertTrue(settled >= 3)
    }

    /** Macro Maker's floating windows, as Android's window list reports them. */
    private fun overlayWindows() =
        shell("dumpsys window windows").lines().count { "CREATE_ACCESSIBILITY_OVERLAY" in it }

    private fun shell(command: String): String {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().decodeToString()
        }
    }

    private fun <T : Any> waitFor(what: String, timeoutMs: Long = 10_000, probe: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            probe()?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
