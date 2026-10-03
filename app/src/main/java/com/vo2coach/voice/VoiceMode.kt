package com.vo2coach.voice

import android.content.Context
import android.content.SharedPreferences

/** When the voice coach is allowed to talk. Persisted as [name] in prefs "vo2" / key "voiceMode". */
enum class VoiceMode(val label: String) {
    OFF("OFF"),
    HEADPHONES_ONLY("HEADPHONES"),
    ALWAYS("ALWAYS");

    /** OFF -> HEADPHONES_ONLY -> ALWAYS -> OFF */
    fun next(): VoiceMode = when (this) {
        OFF -> HEADPHONES_ONLY
        HEADPHONES_ONLY -> ALWAYS
        ALWAYS -> OFF
    }

    companion object {
        val DEFAULT = HEADPHONES_ONLY

        fun fromKey(key: String?): VoiceMode =
            values().firstOrNull { it.name == key } ?: DEFAULT
    }
}

/** Pure gate: should we speak given the setting and whether a headset is connected? */
object VoiceGate {
    fun shouldSpeak(mode: VoiceMode, headphonesConnected: Boolean): Boolean = when (mode) {
        VoiceMode.OFF -> false
        VoiceMode.HEADPHONES_ONLY -> headphonesConnected
        VoiceMode.ALWAYS -> true
    }
}

/** Thin SharedPreferences wrapper. Shares the app-wide "vo2" prefs file used by MainActivity. */
class VoicePrefs(private val sp: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    )

    var mode: VoiceMode
        get() = read(sp)
        set(value) = write(sp, value)

    companion object {
        const val PREFS = "vo2"
        const val KEY = "voiceMode"

        fun read(sp: SharedPreferences): VoiceMode = VoiceMode.fromKey(sp.getString(KEY, null))

        fun write(sp: SharedPreferences, mode: VoiceMode) {
            sp.edit().putString(KEY, mode.name).apply()
        }
    }
}
