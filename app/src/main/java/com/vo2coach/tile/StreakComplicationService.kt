package com.vo2coach.tile

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.vo2coach.history.StreakCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streak complication.
 *  - SHORT_TEXT:   text "3W" (week streak), title "2/3" (sessions this week vs 3).
 *  - RANGED_VALUE: value = sessions this week (clamped), min 0, max 3, text "2/3", title "3W".
 * Tap opens MainActivity. Updated every 30 min by the system plus TileRefresher.request() pushes.
 */
class StreakComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val (streak, week) = withContext(Dispatchers.IO) {
            val records = TilePlanSource.records(applicationContext)
            val now = System.currentTimeMillis()
            StreakCalc.currentWeekStreak(records, now) to StreakCalc.sessionsThisWeek(records, now)
        }
        return build(request.complicationType, streak, week)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, streak = 4, week = 2)

    private fun build(type: ComplicationType, streak: Int, week: Int): ComplicationData? {
        val target = TileModel.DEFAULT_WEEKLY_TARGET
        val t = TileModel.complicationTexts(streak, week, target)
        val tap = TileIntents.openAppPendingIntent(this)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(t.text), plain(t.description))
                    .setTitle(plain(t.title))
                    .setTapAction(tap)
                    .build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    TileModel.rangedValue(week, target),
                    0f,
                    target.toFloat(),
                    plain(t.description),
                )
                    .setText(plain(t.title))
                    .setTitle(plain(t.text))
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private fun plain(s: String): ComplicationText = PlainComplicationText.Builder(s).build()
}
