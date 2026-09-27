package io.github.godman4242.macromaker

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout

/** A big button that counts the clicks it receives. Debug builds only — the tap tests aim at it. */
@SuppressLint("SetTextI18n") // test fixture, never translated
class CounterActivity : Activity() {
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        button = Button(this).apply {
            text = count.toString()
            setOnClickListener { count++; text = count.toString() }
        }
        setContentView(FrameLayout(this).apply {
            addView(button, FrameLayout.LayoutParams((240 * density).toInt(), (160 * density).toInt(), Gravity.CENTER))
        })
    }

    fun buttonCenterOnScreen(): TapPoint? {
        if (button.width == 0) return null
        val loc = IntArray(2)
        button.getLocationOnScreen(loc)
        return TapPoint(loc[0] + button.width / 2f, loc[1] + button.height / 2f)
    }

    companion object {
        @Volatile var count = 0
    }
}
