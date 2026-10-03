package com.vo2coach.tile

import android.content.Context
import com.vo2coach.Modes
import com.vo2coach.data.SessionRecord
import com.vo2coach.history.HistoryStore
import com.vo2coach.progression.ProgressionPrefs
import java.time.Instant

/** Supplies today's pick. Swappable so the integrator can adapt if ProgressionEngine changes. */
fun interface TodayPickProvider {
    fun pick(context: Context, records: List<SessionRecord>, nowMs: Long): TodayPick?
}

/**
 * Data access for the tile + complication. Kept tiny so the services stay thin.
 * All functions do disk I/O (HistoryStore): call them off the main thread.
 */
object TilePlanSource {

    /** Default: feature #2 ProgressionEngine.todayPlan(...). Any failure -> no pick (tile still works). */
    val progressionProvider = TodayPickProvider { ctx, records, nowMs ->
        val plan = ProgressionPrefs.engine(ctx).todayPlan(records, Instant.ofEpochMilli(nowMs))
        val index = Modes.all.indexOfFirst { it.id == plan.recommendedModeId }
        val mode = Modes.byId(plan.recommendedModeId)
        TodayPick(
            modeId = plan.recommendedModeId,
            title = mode?.let { TileModel.shortTitle(it.title) } ?: plan.recommendedModeId.uppercase(),
            levelIndex = index,
            downgradedByGuard = plan.downgradedByGuard,
            weeklyTarget = plan.targetThisWeek.takeIf { it > 0 },
        )
    }

    /** Fallback when progression is not available: always Base. */
    val baseOnlyProvider = TodayPickProvider { _, _, _ ->
        Modes.all.firstOrNull()?.let { TodayPick(it.id, TileModel.shortTitle(it.title), 0) }
    }

    /** Override point (e.g. in Application.onCreate or tests). */
    @Volatile
    var provider: TodayPickProvider = progressionProvider

    fun records(context: Context): List<SessionRecord> =
        runCatching { HistoryStore(context).all() }.getOrDefault(emptyList())

    fun pick(context: Context, records: List<SessionRecord>, nowMs: Long): TodayPick? =
        runCatching { provider.pick(context, records, nowMs) }.getOrNull()
            ?: runCatching { baseOnlyProvider.pick(context, records, nowMs) }.getOrNull()

    /** Full tile snapshot. */
    fun snapshot(context: Context, nowMs: Long = System.currentTimeMillis()): TileSnapshot {
        val records = records(context)
        return TileModel.fromRecords(records, nowMs, pick(context, records, nowMs))
    }
}
