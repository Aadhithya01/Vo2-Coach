package com.vo2coach.gps

import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.LocationAvailability

/** GPS state shown on the session screen. Lives in SessionState.gpsStatus. */
enum class GpsStatus {
    /** Indoor/treadmill mode, no location permission, or GPS not supported. */
    OFF,

    /** Outdoor run, waiting for a fix. */
    ACQUIRING,

    /** Fix acquired (tethered to phone or untethered). */
    OK,

    /** Outdoor run but no GNSS / location unavailable (e.g. location disabled, lost signal). */
    LOST;

    companion object {
        /**
         * Maps a Health Services availability to a [GpsStatus].
         * Returns null when [availability] is not a [LocationAvailability] (e.g. a DataTypeAvailability
         * for heart rate), so callers can ignore it.
         */
        fun from(availability: Availability): GpsStatus? {
            if (availability !is LocationAvailability) return null
            return when (availability) {
                LocationAvailability.ACQUIRED_TETHERED,
                LocationAvailability.ACQUIRED_UNTETHERED -> OK
                LocationAvailability.ACQUIRING,
                LocationAvailability.UNKNOWN -> ACQUIRING
                LocationAvailability.NO_GNSS,
                LocationAvailability.UNAVAILABLE -> LOST
                else -> ACQUIRING
            }
        }
    }
}
