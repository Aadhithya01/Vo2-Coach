# VO2 Coach (Wear OS)

An interval coach for Galaxy Watch4 and newer (Wear OS 3+). It has a neon OLED UI, live heart-rate zones from Health Services, haptic and voice cues, an 8-week progression plan, and a tile plus a complication.

## Features

| # | Feature | Where |
|---|---|---|
| core | 5 modes: Base, Starter, Builder, Norwegian 4x4, Max 30/15. Haptic phase cues, live HR zone alerts, pause and stop | `Models.kt`, `WorkoutService.kt`, `MainActivity.kt` |
| 1 | **Session history + weekly streak.** Every session of 60 s or more is saved, including early quits (marked "quit early"). It has a history list and a detail view with per-interval HR, zone %, distance, pace and HR drop. ISO-week streak badge on Home | `history/` |
| 2 | **8-week auto-progression.** A "Today" plan card (Base block, then Build block, then VO2 block). Modes unlock after 3 sessions of the previous mode at 60 % or more in zone. **Hard-session guard:** at least 48 h between hard sessions and no more than 2 in 7 days. This also applies to custom workouts at HARD intensity or above | `progression/` |
| 3 | **Pace + distance.** Outdoor GPS or treadmill estimate, a 10 s rolling pace, GPS status dot, per-interval distance and pace, and a distance/pace summary at the finish | `gps/` |
| 4 | **HR recovery (HRR60).** A live 60 s recovery chip after each work interval, an average with a rating on the finish screen, and the values stored per interval | `recovery/` |
| 5 | **Export / sync.** CSV and JSON export on the watch, plus a Data Layer hand-off to a future phone companion that writes to Health Connect. Health Connect does not run on Wear OS | `healthconnect/` |
| 6 | **Build your own** intervals: reps, work, intensity, recovery, warm-up and cool-down. VO2/MAX safety validation, up to 20 saved | `custom/` |
| 7 | **Voice coach.** TTS phase calls, halfway and 10 s cues, and throttled hype. Modes: OFF, HEADPHONES (default) or ALWAYS | `voice/` |
| 8 | **Tile + complication.** A weekly ring, the streak, today's mode and a START button that opens that mode's detail screen. A SHORT_TEXT / RANGED_VALUE streak complication | `tile/` |
| — | **Settings:** Max HR, Outdoor GPS / treadmill, voice mode, unlock-all. Unlock-all never bypasses the recovery guard | `settings/` |

Home layout: title, Today plan card, streak badge, mode cards (locked modes are greyed and show their unlock condition), then secondary chips: HISTORY · BUILD · SYNC · SETTINGS.

## Run it
1. In Android Studio, choose Open and select this folder. Let Gradle sync (it generates the wrapper).
   Toolchain: AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, compileSdk/targetSdk 34, minSdk 30.
2. On the watch: Settings > About watch > Software, tap Software version 5x to enable developer mode. Then in Developer options, turn on ADB debugging and Wireless debugging.
3. In the Android Studio Device Manager, choose Pair Devices Using Wi-Fi (or run `adb connect <ip>:<port>`), then Run.
4. On first launch, grant **Body sensors** and **Notifications** (both required), plus **Physical activity** and, for outdoor runs, **Location (while using the app)**.
   The last two are optional. Without them, pace and distance are off and the run is HR-only.
5. Open **Settings** on the home list and set your Max HR. 220 − age is a rough guess; a measured max is better.

## Permissions (manifest)
- `BODY_SENSORS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`: heart rate during the workout.
- `ACTIVITY_RECOGNITION`: Health Services requires it for DISTANCE, PACE and SPEED. It is optional; if it is denied, distance and pace are not requested.
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`: GPS pace and distance, while in use only.
  The service adds the location foreground-service type only when Outdoor is on **and** fine location is granted.
- `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`.
- `<queries>` for `android.intent.action.TTS_SERVICE` makes the TTS engine visible to the voice coach.
- `MainActivity` is `singleTask`, so the tile's START reaches a running app through `onNewIntent`.

## Add the tile and complication
- **Tile:** long-press the watch face (or swipe to the end of the tiles), choose **+ Add tile**, then **VO2 Coach**.
  START opens the recommended mode's detail screen. It never auto-starts a run, so GO, the permission checks and the guard still apply.
  The tile refreshes hourly and after every saved or deleted session.
- **Complication:** long-press the watch face, choose **Customize**, then pick a complication slot and **VO2 Streak**.
  SHORT_TEXT shows `3W` (week streak) and `2/3` (sessions this week). RANGED_VALUE shows a 0–3 progress arc. Tapping it opens the app.
- On Galaxy Watch, tiles from sideloaded debug builds appear in the tile list like any other.
  If the tile is missing after reinstalling, remove it and add it again.

## Getting data off the watch
Exports are written to app-private `files/exports/`. On a debuggable build:
```
adb shell run-as com.vo2coach ls files/exports
adb exec-out run-as com.vo2coach cat files/exports/<file>.csv > sessions.csv
```
"SEND TO PHONE" reports *phone app not installed* until a companion app exists. That app needs the same applicationId and signing key, and must declare the `vo2coach_phone_receiver` capability. The companion plan is in `integration/5-health-connect.md`.

## Notes
- HR, distance and pace come from Health Services `ExerciseClient`. If the exercise cannot start, the timer still runs without HR.
- Haptics: 3 short buzzes mean speed up, 1 long buzz means ease off. On a phase change, 3 pulses mean work and 2 mean recover.
- HRR ratings and zone targets are training guidance, not medical advice. If you feel unwell, stop.
- No Samsung Sensor SDK is used, so no Samsung partner approval is needed.
- **Not compiled on a real toolchain yet** (this environment has no Android SDK). Every Kotlin file type-checks with kotlinc 1.9.24 against hand-written API stubs.
  See `integration/0-integrator.md` for what to watch on the first Gradle build.
