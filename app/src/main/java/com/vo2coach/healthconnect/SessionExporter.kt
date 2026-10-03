package com.vo2coach.healthconnect

import android.content.Context
import com.vo2coach.data.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why this exists instead of a Health Connect writer:
 * Health Connect is a phone-only platform (it does not run on Wear OS / Galaxy Watch), and
 * Health Services' ExerciseClient does not persist a third-party app's workout anywhere.
 * So on the watch we (1) export files and (2) hand records to a future phone companion
 * via the Data Layer ([DataLayerSender]); the phone app writes them to Health Connect.
 *
 * Files are written to `filesDir/exports` (app-private). Writes are atomic (tmp + rename).
 * Pull them with:  adb shell run-as com.vo2coach ls files/exports   (debuggable builds)
 */
class SessionExporter(
    context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    private val appContext = context.applicationContext

    data class ExportResult(val files: List<File>, val sessionCount: Int) {
        val fileNames: List<String> get() = files.map { it.name }
    }

    fun exportsDir(): File = File(appContext.filesDir, EXPORT_DIR).apply { mkdirs() }

    /** Writes `vo2coach_sessions_<stamp>.csv` and `vo2coach_intervals_<stamp>.csv`. Blocking IO. */
    fun exportCsv(records: List<SessionRecord>): ExportResult {
        val stamp = stamp()
        val sessions = writeAtomic("vo2coach_sessions_$stamp.csv", SessionCsv.sessions(records))
        val intervals = writeAtomic("vo2coach_intervals_$stamp.csv", SessionCsv.intervals(records))
        prune()
        return ExportResult(listOf(sessions, intervals), records.size)
    }

    /** Writes `vo2coach_sessions_<stamp>.json` (envelope + all sessions). Blocking IO. */
    fun exportJson(records: List<SessionRecord>): ExportResult {
        val file = writeAtomic("vo2coach_sessions_${stamp()}.json", SessionJson.encodeAll(records, nowMs()))
        prune()
        return ExportResult(listOf(file), records.size)
    }

    /** Self-contained JSON for one session (same payload [DataLayerSender] sends). */
    fun shareableJson(record: SessionRecord): String = SessionJson.encodeRecord(record)

    fun listExports(): List<File> =
        exportsDir().listFiles { f -> f.isFile && !f.name.endsWith(TMP_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    suspend fun exportCsvIo(records: List<SessionRecord>): ExportResult =
        withContext(Dispatchers.IO) { exportCsv(records) }

    suspend fun exportJsonIo(records: List<SessionRecord>): ExportResult =
        withContext(Dispatchers.IO) { exportJson(records) }

    // ---------------------------------------------------------------- internals

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs()))

    private fun writeAtomic(name: String, text: String): File {
        val dir = exportsDir()
        val target = uniqueFile(dir, name)
        val tmp = File(dir, target.name + TMP_SUFFIX)
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
        return target
    }

    /** Avoids clobbering when two exports land in the same second. */
    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "${base}_$n" else "${base}_$n.$ext")
            n++
        }
        return f
    }

    /** Watch storage is small: keep only the newest [MAX_FILES] export files. */
    private fun prune() {
        listExports().drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val EXPORT_DIR = "exports"
        const val MAX_FILES = 12
        private const val TMP_SUFFIX = ".tmp"
    }
}
