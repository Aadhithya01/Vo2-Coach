package com.vo2coach.history

import com.vo2coach.Phase
import com.vo2coach.PhaseType
import com.vo2coach.WorkoutMode
import com.vo2coach.Zone
import com.vo2coach.data.IntervalStat
import com.vo2coach.data.SessionRecord
import java.util.UUID

/**
 * Accumulates per-second session data inside WorkoutService and produces a [SessionRecord].
 * Pure Kotlin (no Android APIs). Not thread-safe: call from the session loop's coroutine only
 * (WorkoutService uses Dispatchers.Main for both the loop and ACTION_STOP, so that holds).
 *
 * Counting rules mirror WorkoutService exactly:
 *  - [tick] is called once per *counted* (non-paused) second, after `elapsed++`.
 *  - A WORK second counts as "in zone" when zone is IN or HIGH (same as `workIn` in the service).
 *
 * Other features (GPS distance, HR-drop) can enrich the result with `record.copy(...)` /
 * `intervals.map { it.copy(...) }` before saving.
 */
class SessionRecordBuilder(
    val mode: WorkoutMode,
    val maxHrSetting: Int,
    val startedAtMs: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
    /** Override for custom modes (feature 6), e.g. "custom:<id>". */
    val modeId: String = mode.id,
) {
    companion object {
        /** Sessions shorter than this are not worth saving. */
        const val MIN_SAVE_SEC = 60
    }

    private class Acc(val index: Int, val label: String) {
        var sec = 0
        var sumHr = 0L
        var nHr = 0
        var peak = 0
        var inZone = 0
    }

    var elapsedSec = 0
        private set
    private var sumHr = 0L
    private var nHr = 0
    private var peak = 0
    private var workSec = 0
    private var workIn = 0

    /** keyed by phase index in mode.phases (only WORK phases) */
    private val work = LinkedHashMap<Int, Acc>()

    /** Maps phase index -> 0-based index among WORK phases. */
    private val workOrdinal: Map<Int, Int> = buildMap<Int, Int> {
        var n = 0
        mode.phases.forEachIndexed { i, p -> if (p.type == PhaseType.WORK) put(i, n++) }
    }

    /** Call once per counted second. [phaseIndex] is the index into mode.phases. */
    fun tick(phaseIndex: Int, phase: Phase, hr: Int?, zone: Zone) {
        elapsedSec++
        if (hr != null) {
            sumHr += hr; nHr++
            if (hr > peak) peak = hr
        }
        if (phase.type != PhaseType.WORK) return
        workSec++
        val inZone = zone == Zone.IN || zone == Zone.HIGH
        if (inZone) workIn++
        val acc = work.getOrPut(phaseIndex) { Acc(workOrdinal[phaseIndex] ?: work.size, phase.label) }
        acc.sec++
        if (inZone) acc.inZone++
        if (hr != null) {
            acc.sumHr += hr; acc.nHr++
            if (hr > acc.peak) acc.peak = hr
        }
    }

    val shouldSave: Boolean get() = elapsedSec >= MIN_SAVE_SEC

    fun build(completed: Boolean): SessionRecord = SessionRecord(
        id = id,
        startedAtMs = startedAtMs,
        modeId = modeId,
        modeTitle = mode.title,
        completed = completed,
        elapsedSec = elapsedSec,
        avgHr = if (nHr > 0) (sumHr / nHr).toInt() else null,
        peakHr = peak.takeIf { it > 0 },
        workSec = workSec,
        workInZoneSec = workIn,
        maxHrSetting = maxHrSetting,
        intervals = work.values
            .filter { it.sec > 0 }
            .sortedBy { it.index }
            .map {
                IntervalStat(
                    index = it.index,
                    label = it.label,
                    durationSec = it.sec,
                    avgHr = if (it.nHr > 0) (it.sumHr / it.nHr).toInt() else null,
                    peakHr = it.peak.takeIf { p -> p > 0 },
                    inZoneSec = it.inZone,
                )
            },
    )
}
