package com.vo2coach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseUpdate
import com.vo2coach.custom.ModeResolver
import com.vo2coach.data.SessionRecord
import com.vo2coach.gps.GpsExerciseConfig
import com.vo2coach.gps.GpsPermissions
import com.vo2coach.gps.GpsRecordEnricher
import com.vo2coach.gps.LiveRunMetrics
import com.vo2coach.gps.PaceMath
import com.vo2coach.gps.RunSettings
import com.vo2coach.healthconnect.DataLayerSender
import com.vo2coach.history.HistoryStore
import com.vo2coach.history.SessionRecordBuilder
import com.vo2coach.recovery.HrRecoveryTracker
import com.vo2coach.recovery.withHrRecovery
import com.vo2coach.tile.TileRefresher
import com.vo2coach.voice.VoiceCoach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Runs the interval timer as a foreground service so it keeps going with the screen off.
 * Health Services ExerciseClient supplies live heart rate and (#3) distance / pace / GPS status.
 *
 * Feature hooks: #1 history record, #3 GPS metrics, #4 HR recovery, #5 Data Layer hand-off,
 * #6 custom modes (ModeResolver), #7 voice coach, #8 tile/complication refresh.
 * Everything runs on Dispatchers.Main, so the per-session objects are never touched concurrently
 * (LiveRunMetrics is additionally thread-safe because Health Services callbacks may use a binder thread).
 */
class WorkoutService : Service() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_MAX_HR = "maxHr"
        private const val ACTION_STOP = "stop"
        private const val ACTION_PAUSE = "pause"
        private const val CHANNEL_ID = "workout"
        private const val NOTIF_ID = 1
        private const val PHONE_SEND_TIMEOUT_MS = 4_000L

        private val _state = MutableStateFlow(SessionState())
        val state: StateFlow<SessionState> = _state.asStateFlow()

        fun start(ctx: Context, modeId: String, maxHr: Int) {
            ctx.startForegroundService(
                Intent(ctx, WorkoutService::class.java)
                    .putExtra(EXTRA_MODE, modeId)
                    .putExtra(EXTRA_MAX_HR, maxHr)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WorkoutService::class.java).setAction(ACTION_STOP))
        }

        fun togglePause(ctx: Context) {
            ctx.startService(Intent(ctx, WorkoutService::class.java).setAction(ACTION_PAUSE))
        }

        /** Called by the UI after the "complete" screen is dismissed. */
        fun clear() {
            _state.value = SessionState()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    /** True once startExerciseAsync was *issued* (endExercise() then always tries to end it). */
    private var exerciseStarted = false

    @Volatile private var latestHr: Int? = null

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    // #1 session history
    private var recorder: SessionRecordBuilder? = null
    private val history by lazy { HistoryStore(this) }

    // #3 GPS pace + distance
    private val runMetrics = LiveRunMetrics()

    // #4 HR recovery
    private var hrr: HrRecoveryTracker? = null

    // #5 hand-off to the phone companion (no-op without one)
    private val phoneSender by lazy { DataLayerSender(this) }

    // #7 voice coach
    private var voice: VoiceCoach? = null

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let {
                latestHr = it.value.roundToInt()
            }
            runMetrics.onUpdate(update)
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            runMetrics.onAvailabilityChanged(dataType, availability)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // #7: planner gets (old, new) state on every change; it decides what (if anything) to say.
        voice = runCatching { VoiceCoach(this) }.getOrNull()
        scope.launch {
            var prev = _state.value
            _state.collect { cur ->
                voice?.onState(prev, cur)
                prev = cur
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { stopEarly() }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                _state.value = _state.value.copy(paused = !_state.value.paused)
                return START_NOT_STICKY
            }
        }

        if (loop?.isActive == true) return START_NOT_STICKY

        // #6: built-in ids first, then "custom:<id>" from the custom store.
        val mode = ModeResolver.resolve(this, intent?.getStringExtra(EXTRA_MODE))
        val maxHr = intent?.getIntExtra(EXTRA_MAX_HR, 190) ?: 190

        createChannel()
        if (!goForeground(buildNotification(mode?.title ?: "VO2 Coach")) || mode == null) {
            // startForegroundService() demands startForeground(); bail out cleanly if the mode is gone.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        loop = scope.launch { runSession(mode, maxHr) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voice?.shutdown() // lets an in-flight "Done..." finish (max 6 s), then releases TTS + audio focus
        voice = null
        if (exerciseStarted) {
            // Service torn down without a clean end: never leave a Health Services exercise running.
            exerciseStarted = false
            runCatching { exerciseClient.endExerciseAsync() }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ---------- session engine ----------

    private suspend fun runSession(mode: WorkoutMode, maxHr: Int) {
        recorder = SessionRecordBuilder(mode = mode, maxHrSetting = maxHr) // #1 (modeId = mode.id, custom-aware)
        hrr = HrRecoveryTracker()                                         // #4
        runMetrics.reset(mode.phases)                                     // #3
        startExercise()

        val phases = mode.phases
        // suffix[i] = seconds in phases after index i
        val suffix = IntArray(phases.size)
        var acc = 0
        for (i in phases.indices.reversed()) {
            suffix[i] = acc
            acc += phases[i].seconds
        }

        var idx = 0
        var remaining = phases[0].seconds
        var lastZone = Zone.UNKNOWN
        var lastAlertAt = 0L
        var elapsed = 0
        var sumHr = 0L
        var nHr = 0
        var peak = 0
        var workSec = 0
        var workIn = 0
        buzzPhase(phases[0])

        while (scope.isActive) {
            val phase = phases[idx]
            runMetrics.onPhase(idx)                     // #3 opens/closes per-WORK interval distance
            runMetrics.setPaused(_state.value.paused)   // #3 excludes distance covered while paused
            val elapsedInPhase = phase.seconds - remaining
            val low = (phase.lowPct * maxHr).roundToInt()
            val high = (phase.highPct * maxHr).roundToInt()
            val hr = latestHr
            val zone = when {
                hr == null -> Zone.UNKNOWN
                hr < low -> Zone.LOW
                hr > high -> Zone.HIGH
                else -> Zone.IN
            }

            // Zone alerts: only after a grace period (HR lags behind effort), throttled to every 20 s.
            val grace = if (phase.type == PhaseType.WORK) 30 else 45
            val now = System.currentTimeMillis()
            val alertable = phase.type == PhaseType.WORK || phase.type == PhaseType.RECOVERY
            if (alertable && elapsedInPhase > grace && zone != Zone.IN && zone != Zone.UNKNOWN &&
                (zone != lastZone || now - lastAlertAt > 20_000) && now - lastAlertAt > 20_000
            ) {
                if (zone == Zone.LOW && phase.type == PhaseType.WORK) buzz(0, 200, 100, 200, 100, 200) // speed up
                if (zone == Zone.HIGH) buzz(0, 700) // ease off
                lastAlertAt = now
            }
            lastZone = zone

            _state.value = _state.value.copy(
                active = true,
                finished = false,
                modeTitle = mode.title,
                phase = phase,
                phaseIndex = idx,
                phaseCount = phases.size,
                remainingSec = remaining,
                totalRemainingSec = suffix[idx] + remaining,
                heartRate = hr,
                targetLow = low,
                targetHigh = high,
                zone = zone,
                totalSec = mode.totalSeconds,
                elapsedSec = elapsed,
                peakHr = peak.takeIf { it > 0 },
                // #3
                distanceM = runMetrics.distanceMOrNull,
                paceSecPerKm = runMetrics.currentPaceSecPerKm,
                gpsStatus = runMetrics.gpsStatus,
                // #4
                liveHrDrop = hrr?.liveHrDrop,
                hrrSecondsLeft = hrr?.hrrSecondsLeft,
            )

            delay(1000)
            if (_state.value.paused) {
                hrr?.onPausedTick(hr) // #4: the body keeps recovering in wall time
                continue
            }

            elapsed++
            if (hr != null) {
                sumHr += hr; nHr++
                if (hr > peak) peak = hr
            }
            if (phase.type == PhaseType.WORK) {
                workSec++
                if (zone == Zone.IN || zone == Zone.HIGH) workIn++
            }
            recorder?.tick(idx, phase, hr, zone) // #1 (same locals, before idx++)
            hrr?.onTick(idx, phase.type, hr)     // #4

            remaining--
            if (remaining in 1..3) buzz(0, 120)
            if (remaining <= 0) {
                idx++
                if (idx >= phases.size) break
                remaining = phases[idx].seconds
                buzzPhase(phases[idx])
            }
        }

        // ---- natural finish (a cancelled loop never gets here: delay() throws)
        hrr?.finish()
        runMetrics.finish()
        buzz(0, 1000)
        val record = finalizeRecord(completed = true)
        val distance = runMetrics.distanceMOrNull
        _state.value = SessionState(
            finished = true,
            modeTitle = mode.title,
            totalSec = mode.totalSeconds,
            elapsedSec = elapsed,
            avgHr = if (nHr > 0) (sumHr / nHr).toInt() else null,
            peakHr = peak.takeIf { it > 0 },
            workSec = workSec,
            workInZoneSec = workIn,
            distanceM = distance,                                  // #3
            paceSecPerKm = PaceMath.avgSecPerKm(elapsed, distance), // #3 session average
            avgHrDrop60s = hrr?.average,                           // #4
        )
        hrr = null
        endExercise()
        persist(record)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** User pressed quit. Saves the partial session (if >= 60 s) as completed = false. */
    private suspend fun stopEarly() {
        voice?.stop()            // #7 silence immediately
        loop?.cancel()
        hrr?.finish()            // #4 open recovery window -> null
        runMetrics.finish()      // #3 close the open interval
        val record = finalizeRecord(completed = false)
        hrr = null
        if (!_state.value.finished) _state.value = SessionState() // never wipe a finish screen
        endExercise()
        persist(record)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- history (#1) + enrichment (#3, #4) + sync (#5, #8) ----------

    /** Builds the record once (nulls the recorder, so a second call is a no-op). Null if < 60 s. */
    private fun finalizeRecord(completed: Boolean): SessionRecord? {
        val rec = recorder ?: return null
        recorder = null
        if (!rec.shouldSave) return null
        var record = rec.build(completed)
        record = GpsRecordEnricher.enrich(record, runMetrics)  // #3 distance + pace
        hrr?.let { record = record.withHrRecovery(it) }        // #4 avgHrDrop60s + per-interval drops
        return record
    }

    /** Disk save, tile/complication refresh, best-effort phone hand-off. Never throws (except cancellation). */
    private suspend fun persist(record: SessionRecord?) {
        if (record == null) return
        val saved = try {
            history.saveIo(record)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false // never let a disk error crash the service
        }
        if (saved) TileRefresher.request(this)                                  // #8
        withTimeoutOrNull(PHONE_SEND_TIMEOUT_MS) { phoneSender.send(record) }   // #5
    }

    // ---------- Health Services (heart rate, #3 distance/pace/GPS) ----------

    private suspend fun startExercise() {
        val settings = RunSettings(this)
        val plan = try {
            GpsExerciseConfig.build(
                exerciseClient,
                settings.outdoorGps,
                GpsPermissions.hasFine(this),
                GpsPermissions.hasActivityRecognition(this),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GpsExerciseConfig.hrOnly()
        }
        // GpsExerciseConfig swallows errors internally; make sure a quit during setup does not
        // start an exercise nobody will end.
        coroutineContext.ensureActive()
        try {
            exerciseClient.setUpdateCallback(callback)
            exerciseStarted = true
            exerciseClient.startExerciseAsync(plan.config).await()
            runMetrics.setGpsEnabled(plan.gpsEnabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // GPS/distance config rejected -> retry with the original HR-only config.
            // If that fails too (e.g. permission denied) the timer still runs, just without HR.
            exerciseStarted = false
            if (plan.gpsEnabled || plan.tracksDistance) {
                try {
                    coroutineContext.ensureActive()
                    exerciseStarted = true
                    exerciseClient.startExerciseAsync(GpsExerciseConfig.hrOnly().config).await()
                    runMetrics.setGpsEnabled(false)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    exerciseStarted = false
                }
            }
        }
    }

    private suspend fun endExercise() {
        if (!exerciseStarted) return
        exerciseStarted = false
        runCatching { exerciseClient.endExerciseAsync().await() }
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
    }

    // ---------- haptics / notification ----------

    private fun buzz(vararg timings: Long) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private fun buzzPhase(p: Phase) = when (p.type) {
        PhaseType.WORK -> buzz(0, 500, 150, 500, 150, 500) // go hard: 3 pulses
        PhaseType.RECOVERY -> buzz(0, 400, 150, 400)       // ease: 2 pulses
        else -> buzz(0, 400)
    }

    /**
     * startForeground with the right types. Location is added only when the outdoor setting is on
     * AND fine location is granted (API 34 throws SecurityException otherwise). Falls back to
     * health-only, then to the plain call. Returns false if the service could not go foreground.
     */
    private fun goForeground(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            val wantLocation = RunSettings(this).outdoorGps && GpsPermissions.hasFine(this)
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                (if (wantLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            if (runCatching { startForeground(NOTIF_ID, notification, types) }.isSuccess) return true
            if (runCatching {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                }.isSuccess
            ) return true
            return false
        }
        return runCatching { startForeground(NOTIF_ID, notification) }.isSuccess
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workout", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(title: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VO2 Coach")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
}
