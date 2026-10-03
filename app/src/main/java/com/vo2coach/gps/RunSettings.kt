package com.vo2coach.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.data.ExerciseType

/**
 * Outdoor (GPS) vs indoor (treadmill) setting. App-wide "vo2" SharedPreferences, key "outdoorGps",
 * default true. Indoor => ExerciseType.RUNNING_TREADMILL, GPS off.
 */
class RunSettings(context: Context) {
    companion object {
        const val PREFS = "vo2"
        const val KEY_OUTDOOR_GPS = "outdoorGps"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var outdoorGps: Boolean
        get() = prefs.getBoolean(KEY_OUTDOOR_GPS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OUTDOOR_GPS, value).apply()
        }

    val exerciseType: ExerciseType
        get() = if (outdoorGps) ExerciseType.RUNNING else ExerciseType.RUNNING_TREADMILL
}

/**
 * Permission helpers for #3.
 *  - Location (ACCESS_FINE_LOCATION; COARSE must be requested together on API 31+) -> DataType.LOCATION / isGpsEnabled.
 *  - ACTIVITY_RECOGNITION -> Health Services DISTANCE / PACE / SPEED (developer.android.com/health-and-fitness/guides/health-services/permissions).
 * Both are optional: without them the run is HR-only.
 */
object GpsPermissions {
    val required: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    const val ACTIVITY: String = Manifest.permission.ACTIVITY_RECOGNITION

    fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasActivityRecognition(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, ACTIVITY) == PackageManager.PERMISSION_GRANTED

    /** Optional permissions still missing for pace/distance, given the outdoor setting. */
    fun missingOptional(context: Context, outdoor: Boolean): Array<String> = buildList {
        if (!hasActivityRecognition(context)) add(ACTIVITY)
        if (outdoor && !hasFine(context)) addAll(required)
    }.toTypedArray()
}
