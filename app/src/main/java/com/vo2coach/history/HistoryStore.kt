package com.vo2coach.history

import android.content.Context
import com.vo2coach.data.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent session history in `filesDir/history.json`.
 *
 * - Thread-safe: all access goes through one process-wide lock, and the in-memory cache is
 *   shared, so the service and the activity (same process) can each create their own instance.
 * - Newest first (sorted by startedAtMs desc), capped at [MAX_RECORDS].
 * - Writes are atomic (tmp file + rename).
 * - The plain functions do disk I/O: call them off the main thread, or use the `*Io` suspend variants.
 */
class HistoryStore(context: Context) {

    companion object {
        const val FILE_NAME = "history.json"
        const val MAX_RECORDS = 500

        private val lock = Any()
        @Volatile private var cache: List<SessionRecord>? = null

        private val _revision = MutableStateFlow(0)

        /** Bumps on every change. Use it as a Compose key / collect it to refresh UI. */
        val revision: StateFlow<Int> = _revision.asStateFlow()
    }

    private val appContext = context.applicationContext
    private val file: File get() = File(appContext.filesDir, FILE_NAME)

    // ------------------------------------------------------------ reads

    /** All records, newest first. */
    fun all(): List<SessionRecord> = synchronized(lock) { loadLocked() }

    fun get(id: String): SessionRecord? = synchronized(lock) { loadLocked().firstOrNull { it.id == id } }

    // ------------------------------------------------------------ writes

    /** Inserts or replaces (by id) a record. */
    fun save(record: SessionRecord) = synchronized(lock) {
        val updated = (listOf(record) + loadLocked().filter { it.id != record.id })
            .sortedByDescending { it.startedAtMs }
            .take(MAX_RECORDS)
        writeLocked(updated)
    }

    /** @return true if a record was removed. */
    fun delete(id: String): Boolean = synchronized(lock) {
        val current = loadLocked()
        val updated = current.filter { it.id != id }
        if (updated.size == current.size) return@synchronized false
        writeLocked(updated)
        true
    }

    fun clear() = synchronized(lock) { writeLocked(emptyList()) }

    // ------------------------------------------------------------ coroutine helpers

    suspend fun allIo(): List<SessionRecord> = withContext(Dispatchers.IO) { all() }
    suspend fun getIo(id: String): SessionRecord? = withContext(Dispatchers.IO) { get(id) }
    suspend fun saveIo(record: SessionRecord) = withContext(Dispatchers.IO) { save(record) }
    suspend fun deleteIo(id: String): Boolean = withContext(Dispatchers.IO) { delete(id) }
    suspend fun clearIo() = withContext(Dispatchers.IO) { clear() }

    // ------------------------------------------------------------ internals (hold lock)

    private fun loadLocked(): List<SessionRecord> {
        cache?.let { return it }
        val f = file
        val loaded: List<SessionRecord> = if (!f.exists()) {
            emptyList()
        } else {
            try {
                HistoryJson.decodeAll(f.readText(Charsets.UTF_8))
                    .sortedByDescending { it.startedAtMs }
                    .take(MAX_RECORDS)
            } catch (e: Exception) {
                // Corrupt file: keep a copy for forensics instead of silently overwriting it later.
                runCatching { f.copyTo(File(appContext.filesDir, "$FILE_NAME.corrupt"), overwrite = true) }
                emptyList()
            }
        }
        cache = loaded
        return loaded
    }

    private fun writeLocked(records: List<SessionRecord>) {
        val f = file
        val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(HistoryJson.encodeAll(records), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            // renameTo can fail on some filesystems if the target exists; fall back to copy.
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
        cache = records
        _revision.value = _revision.value + 1
    }
}
