package com.vo2coach.custom

import android.content.Context
import android.util.AtomicFile
import com.vo2coach.Modes
import com.vo2coach.WorkoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** Result of [CustomWorkoutStore.save]. */
sealed class SaveResult {
    data class Saved(val workout: CustomWorkout) : SaveResult()
    /** Already [CustomBounds.MAX_SAVED] workouts and this is a new one. */
    object Full : SaveResult()
    /** Failed [CustomValidator]; nothing was written. */
    data class Invalid(val issues: List<CustomIssue>) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

/**
 * CRUD for user-built workouts, persisted as JSON in `filesDir/custom.json` (max [CustomBounds.MAX_SAVED]).
 * Process-wide lock + cache, atomic writes via [AtomicFile]. File is tiny (<= 20 entries) so the plain
 * (non-suspend) methods are fine on the main thread; I/O happens only on first read and on writes.
 */
class CustomWorkoutStore(context: Context) {

    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    /** Insertion order (oldest first). */
    fun all(): List<CustomWorkout> = synchronized(LOCK) { loadLocked() }

    fun get(id: String): CustomWorkout? = all().firstOrNull { it.id == id }

    fun count(): Int = all().size

    fun isFull(): Boolean = count() >= CustomBounds.MAX_SAVED

    /** Insert (new id) or replace (existing id). Validates and clamps first. */
    fun save(workout: CustomWorkout): SaveResult = synchronized(LOCK) {
        val w = workout.clamped()
        val issues = CustomValidator.validate(w)
        if (issues.isNotEmpty()) return SaveResult.Invalid(issues)
        val list = loadLocked().toMutableList()
        val idx = list.indexOfFirst { it.id == w.id }
        if (idx >= 0) {
            list[idx] = w
        } else {
            if (list.size >= CustomBounds.MAX_SAVED) return SaveResult.Full
            list.add(w)
        }
        return if (writeLocked(list)) SaveResult.Saved(w) else SaveResult.Error("Could not save. Try again.")
    }

    fun delete(id: String): Boolean = synchronized(LOCK) {
        val list = loadLocked()
        val next = list.filterNot { it.id == id }
        if (next.size == list.size) return false
        writeLocked(next)
    }

    // ---------------------------------------------------------------- internals

    private fun loadLocked(): List<CustomWorkout> {
        cache?.let { return it }
        val list = try {
            if (file.baseFile.exists()) CustomWorkoutJson.decode(String(file.readFully(), Charsets.UTF_8)) else emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        cache = list
        return list
    }

    private fun writeLocked(list: List<CustomWorkout>): Boolean {
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            return false
        }
        return try {
            out.write(CustomWorkoutJson.encode(list).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            cache = list
            _revision.value = _revision.value + 1
            true
        } catch (e: IOException) {
            file.failWrite(out)
            false
        }
    }

    companion object {
        const val FILE_NAME = "custom.json"
        private val LOCK = Any()

        @Volatile
        private var cache: List<CustomWorkout>? = null

        private val _revision = MutableStateFlow(0)

        /** Bumps on every successful write; collect in UI to refresh lists. */
        val revision: StateFlow<Int> = _revision.asStateFlow()
    }
}

/**
 * Single place that turns a mode id into a [WorkoutMode]: built-ins first ([Modes.byId]), then
 * `"custom:<id>"` from [CustomWorkoutStore]. WorkoutService and MainActivity should use this instead of Modes.byId.
 */
object ModeResolver {
    fun resolve(context: Context, id: String?): WorkoutMode? {
        if (id.isNullOrBlank()) return null
        Modes.byId(id)?.let { return it }
        val customId = CustomWorkout.customIdOf(id) ?: return null
        return CustomWorkoutStore(context).get(customId)?.toWorkoutMode()
    }

    fun isCustom(id: String?): Boolean = CustomWorkout.isCustomModeId(id)

    /**
     * True for a saved custom workout whose WORK reps are at least HARD intensity (top of range
     * >= 92 % max HR, same as the built-in Builder). Used by the progression safety guard.
     * Deleted / unknown custom ids return false.
     */
    fun isHardCustom(context: Context, id: String?): Boolean {
        val customId = CustomWorkout.customIdOf(id) ?: return false
        val w = runCatching { CustomWorkoutStore(context).get(customId) }.getOrNull() ?: return false
        return w.workIntensity.highPct >= HARD_HIGH_PCT
    }

    private const val HARD_HIGH_PCT = 0.92
}
