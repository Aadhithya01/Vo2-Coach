package com.vo2coach.history

import com.vo2coach.data.IntervalStat
import com.vo2coach.data.SessionRecord
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON (de)serialization for [SessionRecord] / [IntervalStat] using org.json (built into Android).
 * Null fields are simply omitted. Unknown keys are ignored, so the format can grow.
 *
 * File layout: {"v":1,"sessions":[ {record}, ... ]}  (newest first)
 */
object HistoryJson {

    const val VERSION = 1

    fun encodeAll(records: List<SessionRecord>): String {
        val arr = JSONArray()
        records.forEach { arr.put(encode(it)) }
        return JSONObject()
            .put("v", VERSION)
            .put("sessions", arr)
            .toString()
    }

    /** Throws [JSONException] if the document itself is malformed. Bad individual records are skipped. */
    fun decodeAll(text: String): List<SessionRecord> {
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        val arr = root.optJSONArray("sessions") ?: return emptyList()
        val out = ArrayList<SessionRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(decode(o))
            } catch (e: JSONException) {
                // skip a single corrupt record rather than losing the whole history
            }
        }
        return out
    }

    fun encode(r: SessionRecord): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("startedAtMs", r.startedAtMs)
        o.put("modeId", r.modeId)
        o.put("modeTitle", r.modeTitle)
        o.put("completed", r.completed)
        o.put("elapsedSec", r.elapsedSec)
        o.putOptInt("avgHr", r.avgHr)
        o.putOptInt("peakHr", r.peakHr)
        o.put("workSec", r.workSec)
        o.put("workInZoneSec", r.workInZoneSec)
        o.put("maxHrSetting", r.maxHrSetting)
        o.putOptDouble("distanceM", r.distanceM)
        o.putOptInt("avgPaceSecPerKm", r.avgPaceSecPerKm)
        o.putOptInt("avgHrDrop60s", r.avgHrDrop60s)
        val arr = JSONArray()
        r.intervals.forEach { arr.put(encodeInterval(it)) }
        o.put("intervals", arr)
        return o
    }

    fun decode(o: JSONObject): SessionRecord {
        val arr = o.optJSONArray("intervals")
        val intervals = buildList<IntervalStat> {
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(decodeInterval(it)) }
            }
        }
        return SessionRecord(
            id = o.getString("id"),
            startedAtMs = o.getLong("startedAtMs"),
            modeId = o.optString("modeId", ""),
            modeTitle = o.optString("modeTitle", ""),
            completed = o.optBoolean("completed", false),
            elapsedSec = o.optInt("elapsedSec", 0),
            avgHr = o.optIntOrNull("avgHr"),
            peakHr = o.optIntOrNull("peakHr"),
            workSec = o.optInt("workSec", 0),
            workInZoneSec = o.optInt("workInZoneSec", 0),
            maxHrSetting = o.optInt("maxHrSetting", 0),
            distanceM = o.optDoubleOrNull("distanceM"),
            avgPaceSecPerKm = o.optIntOrNull("avgPaceSecPerKm"),
            avgHrDrop60s = o.optIntOrNull("avgHrDrop60s"),
            intervals = intervals,
        )
    }

    private fun encodeInterval(s: IntervalStat): JSONObject {
        val o = JSONObject()
        o.put("index", s.index)
        o.put("label", s.label)
        o.put("durationSec", s.durationSec)
        o.putOptInt("avgHr", s.avgHr)
        o.putOptInt("peakHr", s.peakHr)
        o.put("inZoneSec", s.inZoneSec)
        o.putOptDouble("distanceM", s.distanceM)
        o.putOptInt("avgPaceSecPerKm", s.avgPaceSecPerKm)
        o.putOptInt("hrDrop60s", s.hrDrop60s)
        return o
    }

    private fun decodeInterval(o: JSONObject): IntervalStat = IntervalStat(
        index = o.optInt("index", 0),
        label = o.optString("label", ""),
        durationSec = o.optInt("durationSec", 0),
        avgHr = o.optIntOrNull("avgHr"),
        peakHr = o.optIntOrNull("peakHr"),
        inZoneSec = o.optInt("inZoneSec", 0),
        distanceM = o.optDoubleOrNull("distanceM"),
        avgPaceSecPerKm = o.optIntOrNull("avgPaceSecPerKm"),
        hrDrop60s = o.optIntOrNull("hrDrop60s"),
    )

    // ---- small helpers (org.json has no nullable getters) ----

    private fun JSONObject.putOptInt(key: String, v: Int?) {
        if (v != null) put(key, v)
    }

    private fun JSONObject.putOptDouble(key: String, v: Double?) {
        // JSONObject.put(String, double) throws on NaN/Infinity
        if (v != null && !v.isNaN() && !v.isInfinite()) put(key, v)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
