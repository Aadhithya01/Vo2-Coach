package com.vo2coach.gps

import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import kotlinx.coroutines.guava.await

/**
 * Builds the ExerciseConfig for a session, only requesting data types the device supports
 * (requesting an unsupported DataType makes startExerciseAsync fail).
 *
 * Verified against health-services-client 1.0.0 API:
 *  - ExerciseClient.getCapabilitiesAsync(): ListenableFuture<ExerciseCapabilities>
 *  - ExerciseCapabilities.supportedExerciseTypes / getExerciseTypeCapabilities(type) (throws if unsupported)
 *  - ExerciseTypeCapabilities.supportedDataTypes: Set<DataType<*, *>>
 *  - ExerciseConfig(exerciseType, dataTypes, isAutoPauseAndResumeEnabled, isGpsEnabled, ...)
 */
object GpsExerciseConfig {

    data class Plan(
        val config: ExerciseConfig,
        /** true when the exercise was configured with isGpsEnabled = true (pass to LiveRunMetrics.reset). */
        val gpsEnabled: Boolean,
        /** true when some distance data type was requested. */
        val tracksDistance: Boolean,
    )

    /** HR-only fallback, identical to the original WorkoutService config. */
    fun hrOnly(): Plan = Plan(
        ExerciseConfig(
            exerciseType = ExerciseType.RUNNING,
            dataTypes = setOf(DataType.HEART_RATE_BPM),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false,
        ),
        gpsEnabled = false,
        tracksDistance = false,
    )

    /**
     * @param outdoor RunSettings.outdoorGps
     * @param hasLocationPermission GpsPermissions.hasFine(context)
     * @param hasActivityRecognition GpsPermissions.hasActivityRecognition(context); DISTANCE / PACE
     *   require it, so without it only HR (+ LOCATION) is requested.
     */
    suspend fun build(
        client: ExerciseClient,
        outdoor: Boolean,
        hasLocationPermission: Boolean,
        hasActivityRecognition: Boolean = true,
    ): Plan {
        val caps = runCatching { client.getCapabilitiesAsync().await() }.getOrNull() ?: return hrOnly()

        val wantedType = if (outdoor) ExerciseType.RUNNING else ExerciseType.RUNNING_TREADMILL
        val type = when {
            wantedType in caps.supportedExerciseTypes -> wantedType
            ExerciseType.RUNNING in caps.supportedExerciseTypes -> ExerciseType.RUNNING
            else -> return hrOnly()
        }
        val supported: Set<DataType<*, *>> =
            runCatching { caps.getExerciseTypeCapabilities(type).supportedDataTypes }.getOrNull() ?: return hrOnly()

        val types = mutableSetOf<DataType<*, *>>()
        if (DataType.HEART_RATE_BPM in supported) types += DataType.HEART_RATE_BPM
        if (hasActivityRecognition) {
            if (DataType.DISTANCE_TOTAL in supported) types += DataType.DISTANCE_TOTAL
            else if (DataType.DISTANCE in supported) types += DataType.DISTANCE
            if (DataType.PACE in supported) types += DataType.PACE
        }

        val gps = outdoor && type == ExerciseType.RUNNING && hasLocationPermission &&
            DataType.LOCATION in supported
        if (gps) types += DataType.LOCATION

        val config = ExerciseConfig(
            exerciseType = type,
            dataTypes = types,
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = gps,
        )
        return Plan(
            config,
            gpsEnabled = gps,
            tracksDistance = DataType.DISTANCE_TOTAL in types || DataType.DISTANCE in types,
        )
    }
}
