package io.github.godman4242.macromaker

import android.content.Context

/** The saved plan and panel visibility, shared by the settings screen and the floating controls. */
object PlanStore {
    private const val PREFS = "macro_maker"
    private const val KEY_PLAN = "plan"
    private const val KEY_CONTROLS_HIDDEN = "controls_hidden"

    fun load(context: Context): TapPlan =
        PlanCodec.decode(prefs(context).getString(KEY_PLAN, null)) ?: TapPlan.DEFAULT

    fun update(context: Context, change: (TapPlan) -> TapPlan) {
        val next = change(load(context)).clamped()
        prefs(context).edit().putString(KEY_PLAN, PlanCodec.encode(next)).apply()
    }

    fun controlsHidden(context: Context) = prefs(context).getBoolean(KEY_CONTROLS_HIDDEN, false)

    fun setControlsHidden(context: Context, hidden: Boolean) =
        prefs(context).edit().putBoolean(KEY_CONTROLS_HIDDEN, hidden).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
