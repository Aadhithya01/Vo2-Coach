package com.vo2coach.progression

import android.content.Context
import com.vo2coach.custom.ModeResolver

/**
 * The only persisted state of feature #2: a manual "force unlock all modes" override.
 * Stored in the app-wide "vo2" SharedPreferences, key "forceUnlockAll".
 * Note: it bypasses unlocks only, never the hard-session guard.
 */
object ProgressionPrefs {
    const val PREFS = "vo2"
    const val KEY_FORCE_UNLOCK_ALL = "forceUnlockAll"

    fun forceUnlockAll(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FORCE_UNLOCK_ALL, false)

    fun setForceUnlockAll(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FORCE_UNLOCK_ALL, value).apply()
    }

    /**
     * Convenience: engine configured with the stored override. Custom workouts at HARD/VO2/MAX
     * intensity count as HARD for the 48 h / 2-per-7-days safety guard.
     */
    fun engine(ctx: Context): ProgressionEngine {
        val app = ctx.applicationContext
        return ProgressionEngine(
            forceUnlockAll = forceUnlockAll(app),
            extraHard = { id -> ModeResolver.isHardCustom(app, id) },
        )
    }
}
