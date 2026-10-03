package com.vo2coach.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vo2coach.SessionState
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes [Cue]s with Android TextToSpeech. All decisions about WHAT to say live in
 * [VoiceCuePlanner]; this class only decides WHETHER it may speak (setting + headset) and HOW
 * (queue mode, audio focus with ducking).
 *
 * Create in Service.onCreate, call [onState] (or [speak]) from the main thread, [shutdown] in onDestroy.
 */
class VoiceCoach(
    context: Context,
    private val prefs: VoicePrefs = VoicePrefs(context),
    private val planner: VoiceCuePlanner = VoiceCuePlanner(),
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = appContext.getSystemService(AudioManager::class.java)

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* short cues: nothing to do on loss */ }
            .build()

    private val lock = Any()
    private val outstanding = HashSet<String>() // utterance ids queued or speaking (guarded by lock)
    private var hasFocus = false                // guarded by lock
    private val ids = AtomicLong(0)

    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var shutdownRequested = false
    private var pendingPhaseCue: Cue? = null    // last flush cue requested before TTS init finished (main thread)

    // ------------------------------------------------------------------ init

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS || closed) return
        val us = tts.setLanguage(Locale.US)
        if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        }
        tts.setSpeechRate(1.1f)
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(progress)
        ready = true
        main.post {
            val cue = pendingPhaseCue
            pendingPhaseCue = null
            if (cue != null) speak(listOf(cue))
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    // Declared last so every field above is initialised before onInit can fire.
    private val tts: TextToSpeech = TextToSpeech(appContext, this)

    // ------------------------------------------------------------------ public API

    /** Convenience: plan + speak in one call. Call once per state change with old/new state. */
    fun onState(prev: SessionState?, cur: SessionState) {
        val cues = planner.plan(prev, cur)
        if (cues.isNotEmpty()) speak(cues)
    }

    /** Current setting allows speech right now (setting + headset check). */
    fun canSpeak(): Boolean = VoiceGate.shouldSpeak(prefs.mode, headphonesConnected())

    fun speak(cues: List<Cue>) {
        if (closed || shutdownRequested || cues.isEmpty()) return
        if (!canSpeak()) return
        if (!ready) {
            // Keep only the latest announcement; hype is stale by the time TTS is up.
            cues.lastOrNull { it.flush }?.let { pendingPhaseCue = it }
            return
        }
        for (cue in cues) {
            val busy = synchronized(lock) { outstanding.isNotEmpty() }
            // Hype/zone lines never overlap: drop them if something is already queued/speaking.
            if (!cue.flush && busy && (cue.kind == CueKind.HYPE || cue.kind == CueKind.ZONE)) continue
            val id = "vo2-" + ids.incrementAndGet()
            synchronized(lock) {
                if (!hasFocus) {
                    hasFocus = audio?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
                outstanding.add(id)
            }
            val mode = if (cue.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val res = tts.speak(cue.text, mode, Bundle(), id)
            if (res != TextToSpeech.SUCCESS) finished(id)
        }
    }

    /** Stop talking immediately (e.g. user stopped the workout). */
    fun stop() {
        if (!ready || closed) return
        tts.stop()
        synchronized(lock) { outstanding.clear() }
        releaseFocus()
    }

    /**
     * Release TTS. If an utterance (e.g. "Done.") is still playing, waits for it to finish,
     * at most [GRACE_MS], so the last cue is not cut off when the service is destroyed.
     */
    fun shutdown() {
        if (closed || shutdownRequested) return
        shutdownRequested = true
        pendingPhaseCue = null
        val busy = synchronized(lock) { outstanding.isNotEmpty() }
        if (!busy) {
            reallyShutdown()
        } else {
            main.postDelayed({ reallyShutdown() }, GRACE_MS)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun finished(utteranceId: String?) {
        val empty = synchronized(lock) {
            if (utteranceId != null) outstanding.remove(utteranceId)
            val e = outstanding.isEmpty()
            if (e && hasFocus) {
                audio?.abandonAudioFocusRequest(focusRequest)
                hasFocus = false
            }
            e
        }
        if (empty && shutdownRequested) main.post { reallyShutdown() }
    }

    private fun releaseFocus() {
        synchronized(lock) {
            if (hasFocus) {
                audio?.abandonAudioFocusRequest(focusRequest)
                hasFocus = false
            }
        }
    }

    private fun reallyShutdown() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        synchronized(lock) { outstanding.clear() }
        releaseFocus()
    }

    private fun headphonesConnected(): Boolean {
        val am = audio ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isHeadsetType(it.type) }
    }

    companion object {
        private const val GRACE_MS = 6_000L

        /** Pure: is this AudioDeviceInfo type a Bluetooth headset/earbuds/speaker? */
        fun isHeadsetType(type: Int, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true
            if (sdkInt >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
            ) return true
            return false
        }
    }
}
