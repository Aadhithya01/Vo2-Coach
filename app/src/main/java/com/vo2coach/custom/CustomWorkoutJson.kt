package com.vo2coach.custom

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * org.json codec for [CustomWorkout]. File format:
 * {"version":1,"workouts":[{"id":..,"name":..,"warmupMin":..,"reps":..,"workSec":..,"workIntensity":"HARD",
 *   "recoverySec":..,"recoveryType":"JOG","cooldownMin":..}, ...]}
 * Unknown keys ignored; unknown enum names fall back to defaults; a corrupt single entry is skipped;
 * every loaded entry is passed through [CustomWorkout.clamped].
 */
object CustomWorkoutJson {
    private const val VERSION = 1

    fun encode(list: List<CustomWorkout>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return JSONObject()
            .put("version", VERSION)
            .put("workouts", arr)
            .toString()
    }

    fun decode(text: String?): List<CustomWorkout> {
        if (text.isNullOrBlank()) return emptyList()
        val arr: JSONArray = try {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("workouts") ?: return emptyList()
        } catch (e: JSONException) {
            return emptyList()
        }
        val out = ArrayList<CustomWorkout>(arr.length())
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val w = fromJson(o) ?: continue
            if (seen.add(w.id)) out.add(w)
        }
        return out
    }

    fun toJson(w: CustomWorkout): JSONObject = JSONObject()
        .put("id", w.id)
        .put("name", w.name)
        .put("warmupMin", w.warmupMin)
        .put("reps", w.reps)
        .put("workSec", w.workSec)
        .put("workIntensity", w.workIntensity.name)
        .put("recoverySec", w.recoverySec)
        .put("recoveryType", w.recoveryType.name)
        .put("cooldownMin", w.cooldownMin)

    fun fromJson(o: JSONObject): CustomWorkout? {
        val id = o.optString("id", "").trim()
        if (id.isEmpty() || id.contains(':')) return null
        val d = CustomWorkout(id = id, name = "")
        return CustomWorkout(
            id = id,
            name = o.optString("name", ""),
            warmupMin = o.optInt("warmupMin", d.warmupMin),
            reps = o.optInt("reps", d.reps),
            workSec = o.optInt("workSec", d.workSec),
            workIntensity = enumOr(o.optString("workIntensity", ""), d.workIntensity),
            recoverySec = o.optInt("recoverySec", d.recoverySec),
            recoveryType = enumOr(o.optString("recoveryType", ""), d.recoveryType),
            cooldownMin = o.optInt("cooldownMin", d.cooldownMin),
        ).clamped()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
