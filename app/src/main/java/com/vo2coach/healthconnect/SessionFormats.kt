package com.vo2coach.healthconnect

import com.vo2coach.data.IntervalStat
import com.vo2coach.data.SessionRecord
import java.time.Instant

/**
 * Pure-Kotlin encoders for [SessionRecord] (no Android / org.json dependency, so they run in
 * plain JVM unit tests). Key names are identical to HistoryJson (feature 1) so a phone app
 * can share one decoder.
 *
 * JSON export document:  {"v":1,"source":"vo2coach-watch","exportedAtMs":..,"sessions":[ {record}, ... ]}
 * Single record (Data Layer payload): {record}  plus "v" and "source" keys.
 */
object SessionJson {

    const val VERSION = 1
    const val SOURCE = "vo2coach-watch"

    fun encodeAll(records: List<SessionRecord>, exportedAtMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder(256 + records.size * 512)
        sb.append('{')
        sb.append("\"v\":").append(VERSION).append(',')
        sb.append("\"source\":").appendJsonString(SOURCE).append(',')
        sb.append("\"exportedAtMs\":").append(exportedAtMs).append(',')
        sb.append("\"sessions\":[")
        records.forEachIndexed { i, r ->
            if (i > 0) sb.append(',')
            appendRecord(sb, r, envelope = false)
        }
        sb.append("]}")
        return sb.toString()
    }

    /** One self-describing record, suitable for sharing or sending over the Data Layer. */
    fun encodeRecord(r: SessionRecord): String {
        val sb = StringBuilder(512 + r.intervals.size * 160)
        appendRecord(sb, r, envelope = true)
        return sb.toString()
    }

    private fun appendRecord(sb: StringBuilder, r: SessionRecord, envelope: Boolean) {
        val o = JsonObj(sb)
        if (envelope) {
            o.num("v", VERSION)
            o.str("source", SOURCE)
        }
        o.str("id", r.id)
        o.num("startedAtMs", r.startedAtMs)
        o.str("startedAtIso", Instant.ofEpochMilli(r.startedAtMs).toString())
        o.str("modeId", r.modeId)
        o.str("modeTitle", r.modeTitle)
        o.bool("completed", r.completed)
        o.num("elapsedSec", r.elapsedSec)
        o.optNum("avgHr", r.avgHr)
        o.optNum("peakHr", r.peakHr)
        o.num("workSec", r.workSec)
        o.num("workInZoneSec", r.workInZoneSec)
        o.num("inZonePct", r.inZonePct)
        o.num("maxHrSetting", r.maxHrSetting)
        o.optDouble("distanceM", r.distanceM)
        o.optNum("avgPaceSecPerKm", r.avgPaceSecPerKm)
        o.optNum("avgHrDrop60s", r.avgHrDrop60s)
        o.key("intervals")
        sb.append('[')
        r.intervals.forEachIndexed { i, it ->
            if (i > 0) sb.append(',')
            appendInterval(sb, it)
        }
        sb.append(']')
        o.close()
    }

    private fun appendInterval(sb: StringBuilder, s: IntervalStat) {
        val o = JsonObj(sb)
        o.num("index", s.index)
        o.str("label", s.label)
        o.num("durationSec", s.durationSec)
        o.optNum("avgHr", s.avgHr)
        o.optNum("peakHr", s.peakHr)
        o.num("inZoneSec", s.inZoneSec)
        o.optDouble("distanceM", s.distanceM)
        o.optNum("avgPaceSecPerKm", s.avgPaceSecPerKm)
        o.optNum("hrDrop60s", s.hrDrop60s)
        o.close()
    }

    /** Minimal streaming object writer. Null optional fields are omitted (same as HistoryJson). */
    private class JsonObj(private val sb: StringBuilder) {
        private var first = true

        init { sb.append('{') }

        fun key(k: String) {
            if (!first) sb.append(',')
            first = false
            sb.appendJsonString(k).append(':')
        }

        fun str(k: String, v: String) { key(k); sb.appendJsonString(v) }
        fun num(k: String, v: Int) { key(k); sb.append(v) }
        fun num(k: String, v: Long) { key(k); sb.append(v) }
        fun bool(k: String, v: Boolean) { key(k); sb.append(if (v) "true" else "false") }
        fun optNum(k: String, v: Int?) { if (v != null) num(k, v) }
        fun optDouble(k: String, v: Double?) {
            if (v == null || v.isNaN() || v.isInfinite()) return
            key(k); sb.append(round2(v))
        }
        fun close() { sb.append('}') }
    }
}

/**
 * CSV encoders (RFC 4180 quoting, "\n" line endings, UTF-8). Two tables:
 *  - sessions: one row per [SessionRecord]
 *  - intervals: one row per [IntervalStat], keyed by session_id
 * Empty cell = value not available.
 */
object SessionCsv {

    val SESSION_HEADER = listOf(
        "session_id", "started_at_utc", "started_at_ms", "mode_id", "mode_title", "completed",
        "elapsed_sec", "avg_hr", "peak_hr", "work_sec", "work_in_zone_sec", "in_zone_pct",
        "max_hr_setting", "distance_m", "avg_pace_sec_per_km", "avg_hr_drop_60s", "interval_count",
    )

    val INTERVAL_HEADER = listOf(
        "session_id", "index", "label", "duration_sec", "avg_hr", "peak_hr", "in_zone_sec",
        "distance_m", "avg_pace_sec_per_km", "hr_drop_60s",
    )

    fun sessions(records: List<SessionRecord>): String = buildString {
        appendRow(SESSION_HEADER)
        records.forEach { r ->
            appendRow(
                listOf(
                    r.id,
                    Instant.ofEpochMilli(r.startedAtMs).toString(),
                    r.startedAtMs.toString(),
                    r.modeId,
                    r.modeTitle,
                    r.completed.toString(),
                    r.elapsedSec.toString(),
                    r.avgHr.cell(),
                    r.peakHr.cell(),
                    r.workSec.toString(),
                    r.workInZoneSec.toString(),
                    r.inZonePct.toString(),
                    r.maxHrSetting.toString(),
                    r.distanceM.cell(),
                    r.avgPaceSecPerKm.cell(),
                    r.avgHrDrop60s.cell(),
                    r.intervals.size.toString(),
                )
            )
        }
    }

    fun intervals(records: List<SessionRecord>): String = buildString {
        appendRow(INTERVAL_HEADER)
        records.forEach { r ->
            r.intervals.forEach { s ->
                appendRow(
                    listOf(
                        r.id,
                        s.index.toString(),
                        s.label,
                        s.durationSec.toString(),
                        s.avgHr.cell(),
                        s.peakHr.cell(),
                        s.inZoneSec.toString(),
                        s.distanceM.cell(),
                        s.avgPaceSecPerKm.cell(),
                        s.hrDrop60s.cell(),
                    )
                )
            }
        }
    }

    fun escape(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append(escape(c))
        }
        append('\n')
    }

    private fun Int?.cell(): String = this?.toString() ?: ""
    private fun Double?.cell(): String =
        if (this == null || isNaN() || isInfinite()) "" else round2(this).toString()
}

// ---------------------------------------------------------------- shared helpers

internal fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

internal fun StringBuilder.appendJsonString(s: String): StringBuilder {
    append('"')
    for (c in s) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
    return this
}
