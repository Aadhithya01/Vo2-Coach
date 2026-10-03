package com.vo2coach.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Ask the system to re-request the tile (and the streak complication).
 * Call after a session is saved / deleted. Cheap; failures are swallowed.
 */
object TileRefresher {

    fun request(context: Context) {
        val app = context.applicationContext
        runCatching {
            TileService.getUpdater(app).requestUpdate(VO2TileService::class.java)
        }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, StreakComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
