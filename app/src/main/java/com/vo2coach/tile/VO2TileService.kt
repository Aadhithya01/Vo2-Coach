package com.vo2coach.tile

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * VO2 Coach tile: streak, sessions this week, today's mode, big START button.
 *
 * androidx.wear.tiles 1.4.x has no SuspendingTileService (that lives in Horologist), so this uses
 * the ListenableFuture API bridged with kotlinx-coroutines-guava `future {}` (already a dependency).
 * The callbacks are @MainThread; disk reads run on Dispatchers.IO.
 */
class VO2TileService : TileService() {

    companion object {
        const val RESOURCES_VERSION = "1"
        /** Tile is re-requested at least hourly (streak/week roll-over, guard expiry). */
        const val FRESHNESS_MS: Long = 60L * 60L * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future(Dispatchers.IO) {
            val snap = runCatching { TilePlanSource.snapshot(applicationContext) }
                .getOrElse { TileModel.build(0, 0, null) }
            val layout = TileLayout.root(packageName, snap, requestParams.deviceConfiguration)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        scope.future {
            // No image resources: the layout is text + shapes only.
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
