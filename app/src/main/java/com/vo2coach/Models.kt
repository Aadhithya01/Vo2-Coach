package com.vo2coach

import com.vo2coach.gps.GpsStatus

enum class PhaseType { WARMUP, WORK, RECOVERY, COOLDOWN }

/** lowPct/highPct = target heart rate range as a fraction of max HR. */
data class Phase(
    val label: String,
    val type: PhaseType,
    val seconds: Int,
    val lowPct: Double,
    val highPct: Double,
)

data class WorkoutMode(
    val id: String,
    val title: String,
    val level: String,
    val summary: String,
    val phases: List<Phase>,
) {
    val totalSeconds: Int get() = phases.sumOf { it.seconds }
}

object Modes {
    private fun warm(min: Int) = Phase("Warm-up", PhaseType.WARMUP, min * 60, 0.50, 0.65)
    private fun cool(min: Int) = Phase("Cool-down", PhaseType.COOLDOWN, min * 60, 0.50, 0.60)

    private fun intervals(
        reps: Int,
        workSec: Int, workLow: Double, workHigh: Double,
        recSec: Int, recLow: Double, recHigh: Double,
    ): List<Phase> = buildList {
        for (i in 1..reps) {
            add(Phase("Work $i/$reps", PhaseType.WORK, workSec, workLow, workHigh))
            if (i < reps) add(Phase("Recover", PhaseType.RECOVERY, recSec, recLow, recHigh))
        }
    }

    val all: List<WorkoutMode> = listOf(
        WorkoutMode(
            "base", "1. Base", "Easy",
            "40 min steady zone 2 run. Builds aerobic base.",
            listOf(
                warm(5),
                Phase("Steady", PhaseType.WORK, 30 * 60, 0.60, 0.70),
                cool(5),
            ),
        ),
        WorkoutMode(
            "starter", "2. Starter", "Moderate",
            "5 x 2 min hard, 2 min jog.",
            listOf(warm(10)) + intervals(5, 120, 0.80, 0.90, 120, 0.60, 0.70) + cool(5),
        ),
        WorkoutMode(
            "builder", "3. Builder", "Hard",
            "4 x 3 min hard, 2 min jog.",
            listOf(warm(10)) + intervals(4, 180, 0.85, 0.92, 120, 0.60, 0.70) + cool(5),
        ),
        WorkoutMode(
            "norwegian", "4. Norwegian 4x4", "Very hard",
            "4 x 4 min at 90-95% max HR, 3 min easy jog.",
            listOf(warm(10)) + intervals(4, 240, 0.90, 0.95, 180, 0.65, 0.75) + cool(5),
        ),
        WorkoutMode(
            "max", "5. Max 30/15", "Max",
            "2 sets of 10 x (30 s hard, 15 s easy), 4 min rest between sets.",
            buildList {
                add(warm(10))
                for (set in 1..2) {
                    for (rep in 1..10) {
                        add(Phase("Set $set  $rep/10", PhaseType.WORK, 30, 0.90, 1.00))
                        if (rep < 10) add(Phase("Easy", PhaseType.RECOVERY, 15, 0.60, 0.80))
                    }
                    if (set == 1) add(Phase("Set rest", PhaseType.RECOVERY, 240, 0.55, 0.70))
                }
                add(cool(5))
            },
        ),
    )

    fun byId(id: String?): WorkoutMode? = all.firstOrNull { it.id == id }
}

enum class Zone { UNKNOWN, LOW, IN, HIGH }

data class SessionState(
    val active: Boolean = false,
    val finished: Boolean = false,
    val paused: Boolean = false,
    val modeTitle: String = "",
    val phase: Phase? = null,
    val phaseIndex: Int = 0,
    val phaseCount: Int = 0,
    val remainingSec: Int = 0,
    val totalRemainingSec: Int = 0,
    val heartRate: Int? = null,
    val targetLow: Int = 0,
    val targetHigh: Int = 0,
    val zone: Zone = Zone.UNKNOWN,
    // stats
    val totalSec: Int = 0,
    val elapsedSec: Int = 0,
    val avgHr: Int? = null,
    val peakHr: Int? = null,
    val workSec: Int = 0,
    val workInZoneSec: Int = 0,
    // #3 GPS: live = smoothed current pace; finished state = session average
    val distanceM: Double? = null,
    val paceSecPerKm: Int? = null,
    val gpsStatus: GpsStatus = GpsStatus.OFF,
    // #4 HR recovery
    val liveHrDrop: Int? = null,      // bpm dropped since the last WORK ended (live chip); null = hide
    val hrrSecondsLeft: Int? = null,  // 60..1 while measuring, 0 = 60 s value locked
    val avgHrDrop60s: Int? = null,    // finish screen: average HRR60 over measured intervals
)
