package com.vo2coach.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vo2coach.MainActivity

/** Intent contract between the tile / complication and MainActivity. */
object TileIntents {

    /** String extra: Modes id to open directly on its detail screen (never auto-starts). */
    const val EXTRA_START_MODE_ID = "startModeId"

    /** Clickable ids used in the tile layout. */
    const val CLICK_START = "vo2_start"
    const val CLICK_OPEN = "vo2_open"

    fun mainActivityIntent(context: Context, startModeId: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (startModeId != null) putExtra(EXTRA_START_MODE_ID, startModeId)
        }

    /** Tap action for the complication. Immutable, as required on API 31+. */
    fun openAppPendingIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            mainActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Reads the start-mode extra and removes it from the intent, so a configuration change /
     * recreation does not re-open the detail screen. Call from onCreate and onNewIntent.
     */
    fun consumeStartModeId(intent: Intent?): String? {
        val id = intent?.getStringExtra(EXTRA_START_MODE_ID) ?: return null
        intent.removeExtra(EXTRA_START_MODE_ID)
        return id.takeIf { it.isNotBlank() }
    }
}
