package io.github.godman4242.macromaker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * Settings + the Accessibility disclosure. The disclosure is shown every time the service is off,
 * and "I agree — turn it on" is the only way into Android's settings from here, so consent is
 * always an explicit tap after reading it (Google Play's AccessibilityService policy).
 */
class MainActivity : Activity() {
    private lateinit var interval: EditText
    private lateinit var jitter: EditText
    private lateinit var maxTaps: EditText
    private lateinit var maxSeconds: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        interval = findViewById(R.id.interval)
        jitter = findViewById(R.id.jitter)
        maxTaps = findViewById(R.id.max_taps)
        maxSeconds = findViewById(R.id.max_seconds)
        findViewById<Button>(R.id.agree).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.show_controls).setOnClickListener {
            PlanStore.setControlsHidden(this, false)
            TapService.instance?.showControls()
            moveTaskToBack(true)
        }
    }

    @SuppressLint("SetTextI18n") // plain 0-9 digits on purpose: onPause parses them back
    override fun onResume() {
        super.onResume()
        val on = TapService.instance != null
        findViewById<TextView>(R.id.status).setText(if (on) R.string.status_on else R.string.status_off)
        findViewById<View>(R.id.disclosure).visibility = if (on) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.show_controls).isEnabled = on
        val plan = PlanStore.load(this)
        interval.setText(plan.intervalMs.toString())
        jitter.setText(plan.jitterMs.toString())
        maxTaps.setText(plan.maxTaps.toString())
        maxSeconds.setText(plan.maxSeconds.toString())
    }

    /** Saves on leaving, so settings apply without a Save button. Tap points are left untouched. */
    override fun onPause() {
        super.onPause()
        PlanStore.update(this) { plan ->
            plan.copy(
                intervalMs = interval.text.toString().toLongOrNull() ?: plan.intervalMs,
                jitterMs = jitter.text.toString().toLongOrNull() ?: plan.jitterMs,
                maxTaps = maxTaps.text.toString().toIntOrNull() ?: plan.maxTaps,
                maxSeconds = maxSeconds.text.toString().toIntOrNull() ?: plan.maxSeconds,
            )
        }
    }
}
