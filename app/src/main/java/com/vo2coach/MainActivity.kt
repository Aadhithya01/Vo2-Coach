@file:OptIn(ExperimentalAnimationApi::class)

package com.vo2coach

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.ui.geometry.CornerRadius
import com.vo2coach.ui.LiveBackground
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.wear.compose.material.Icon
import com.vo2coach.ui.CountUpText
import com.vo2coach.ui.IconCircleButton
import com.vo2coach.ui.PillButton
import com.vo2coach.ui.CardBar
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.CardLabel
import com.vo2coach.ui.CardSegments
import com.vo2coach.ui.CardValue
import com.vo2coach.ui.PrimaryButton
import com.vo2coach.ui.SquareCard
import com.vo2coach.ui.WidgetCard
import com.vo2coach.ui.modeIcon
import com.vo2coach.custom.CustomBuilderScreen
import com.vo2coach.custom.CustomListScreen
import com.vo2coach.custom.CustomWorkoutStore
import com.vo2coach.custom.ModeResolver
import com.vo2coach.data.SessionRecord
import com.vo2coach.gps.GpsPermissions
import com.vo2coach.gps.GpsStatus
import com.vo2coach.gps.GpsStatusIndicator
import com.vo2coach.gps.OutdoorToggle
import com.vo2coach.gps.PaceReadout
import com.vo2coach.gps.RunSettings
import com.vo2coach.gps.RunSummary
import com.vo2coach.healthconnect.ExportScreen
import com.vo2coach.history.HistoryScreen
import com.vo2coach.history.HistoryStore
import com.vo2coach.history.SessionDetailRoute
import com.vo2coach.history.StreakBadge
import com.vo2coach.history.StreakCalc
import com.vo2coach.history.StreakInfo
import com.vo2coach.progression.LockedModeCard
import com.vo2coach.progression.ProgressionPrefs
import com.vo2coach.progression.ProgressionSnapshot
import com.vo2coach.progression.StartGate
import com.vo2coach.progression.StartGateDialog
import com.vo2coach.progression.TodayPlanCard
import com.vo2coach.progression.UnlockConditionText
import com.vo2coach.progression.UnlockState
import com.vo2coach.recovery.FinishHrrRow
import com.vo2coach.recovery.HrrChip
import com.vo2coach.recovery.HrrRating
import com.vo2coach.settings.MaxHrBounds
import com.vo2coach.settings.SettingsScreen
import com.vo2coach.tile.TileIntents
import com.vo2coach.tile.TileRefresher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import java.time.Instant

class MainActivity : ComponentActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /** #3: location + activity recognition are asked once per activity instance; a denial never blocks a run. */
    private var askedOptional = false

    /** #8: mode id handed over by the tile's START button ("startModeId" extra). */
    private val launchModeId: MutableState<String?> = mutableStateOf(null)

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Optional pace/distance permissions still missing (activity recognition; location when outdoor). */
    private fun missingOptional(): Array<String> = GpsPermissions.missingOptional(this, RunSettings(this).outdoorGps)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchModeId.value = TileIntents.consumeStartModeId(intent)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val optional = missingOptional()
        if (!hasPermissions() || optional.isNotEmpty()) {
            askedOptional = optional.isNotEmpty()
            permLauncher.launch(requiredPermissions() + optional)
        }

        setContent {
            AppTheme {
                App(
                    launchModeId = launchModeId.value,
                    onLaunchConsumed = { launchModeId.value = null },
                    onStart = { mode, maxHr -> startWorkout(mode, maxHr) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TileIntents.consumeStartModeId(intent)?.let { launchModeId.value = it }
    }

    /** @return true if the service was started. */
    private fun startWorkout(mode: WorkoutMode, maxHr: Int): Boolean = when {
        !hasPermissions() -> {
            permLauncher.launch(requiredPermissions())
            false
        }
        !askedOptional && missingOptional().isNotEmpty() -> {
            askedOptional = true
            permLauncher.launch(missingOptional())
            false // user taps GO again -> starts with or without pace/GPS
        }
        else -> {
            WorkoutService.start(this, mode.id, maxHr)
            true
        }
    }
}

// ---------------------------------------------------------------- navigation

sealed class Screen {
    object Home : Screen()
    data class Detail(val mode: WorkoutMode) : Screen()
    data class Countdown(val mode: WorkoutMode) : Screen()
    object Session : Screen()
    object Finish : Screen()
    object History : Screen()                                   // #1
    data class HistoryDetail(val id: String) : Screen()         // #1
    object Export : Screen()                                    // #5
    object Settings : Screen()
    object CustomList : Screen()                                // #6
    data class CustomBuilder(val editId: String?) : Screen()    // #6
}

@Composable
fun App(
    launchModeId: String? = null,
    onLaunchConsumed: () -> Unit = {},
    onStart: (WorkoutMode, Int) -> Boolean,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("vo2", Context.MODE_PRIVATE) }
    var maxHr by remember { mutableIntStateOf(MaxHrBounds.clamp(prefs.getInt("maxHr", MaxHrBounds.DEFAULT))) }
    var selected by remember { mutableStateOf<WorkoutMode?>(null) }
    var countdown by remember { mutableStateOf<WorkoutMode?>(null) }
    /** Secondary screens (history, export, settings, custom builder); Detail/Countdown sit on top. */
    val stack = remember { mutableStateListOf<Screen>() }
    var gate by remember { mutableStateOf<StartGate?>(null) }
    var engineKey by remember { mutableIntStateOf(0) }
    val s by WorkoutService.state.collectAsState()

    // #1 history (reloads on every save/delete) -> #2 progression snapshot + streak
    val store = remember { HistoryStore(ctx) }
    val historyRev by HistoryStore.revision.collectAsState()
    val history by produceState<List<SessionRecord>?>(null, historyRev) {
        value = runCatching { store.allIo() }.getOrDefault(emptyList())
    }
    val engine = remember(engineKey) { ProgressionPrefs.engine(ctx) }
    val progression = remember(history, engine, s.active) {
        history?.let { engine.snapshot(it, Instant.now()) }
    }
    val streak = remember(history) { history?.let { StreakCalc.compute(it) } }

    LaunchedEffect(s.active) { if (s.active) countdown = null }

    // #8 tile START -> open that mode's Detail screen. Never auto-starts: GO, permissions and the
    // progression lock / recovery guard still apply.
    LaunchedEffect(launchModeId) {
        val id = launchModeId ?: return@LaunchedEffect
        if (!s.active && !s.finished) {
            ModeResolver.resolve(ctx, id)?.let { m ->
                stack.clear()
                gate = null
                countdown = null
                selected = m
            }
        }
        onLaunchConsumed()
    }
    // #8 keep tile + complication in sync with any history change (save, delete).
    LaunchedEffect(Unit) {
        HistoryStore.revision.drop(1).collect { TileRefresher.request(ctx) }
    }

    fun setMaxHr(v: Int) {
        maxHr = MaxHrBounds.clamp(v)
        prefs.edit().putInt("maxHr", maxHr).apply()
    }

    fun push(sc: Screen) {
        stack.add(sc)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    val screen: Screen = when {
        s.finished -> Screen.Finish
        s.active -> Screen.Session
        countdown != null -> Screen.Countdown(countdown!!)
        selected != null -> Screen.Detail(selected!!)
        stack.isNotEmpty() -> stack.last()
        else -> Screen.Home
    }

    fun goBack() {
        when {
            gate != null -> gate = null
            selected != null -> selected = null
            else -> pop()
        }
    }

    // Hardware back button: close dialog -> detail -> secondary screens.
    BackHandler(
        enabled = !s.active && !s.finished && countdown == null &&
            (gate != null || selected != null || stack.isNotEmpty()),
    ) { goBack() }

    val bgColor = when (screen) {
        is Screen.Detail -> Modes.all.indexOf(screen.mode).let { if (it >= 0) levelColor(it) else Neon.Magenta }
        is Screen.Countdown -> Neon.Orange
        is Screen.Session -> s.phase?.let { p ->
            if (p.type == PhaseType.WORK && s.zone != Zone.UNKNOWN) zoneColor(s.zone) else phaseColors(p.type).first
        } ?: Neon.Orange
        is Screen.Finish -> Neon.Lime
        is Screen.History, is Screen.HistoryDetail -> Neon.Cyan
        is Screen.Export -> Neon.Violet
        is Screen.Settings -> Neon.Yellow
        is Screen.CustomList, is Screen.CustomBuilder -> Neon.Magenta
        else -> Neon.Orange
    }

    Box(Modifier.fillMaxSize()) {
        LiveBackground(bgColor)
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(380)) + scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.88f)) togetherWith
                    (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.12f))
            },
            label = "screen",
        ) { sc ->
            when (sc) {
                is Screen.Home -> ModeList(
                    progression = progression,
                    streak = streak,
                    onPick = { selected = it },
                    onHistory = { push(Screen.History) },
                    onCustom = { push(Screen.CustomList) },
                    onExport = { push(Screen.Export) },
                    onSettings = { push(Screen.Settings) },
                )
                is Screen.Detail -> DetailScreen(
                    mode = sc.mode,
                    maxHr = maxHr,
                    unlock = progression?.unlockOf(sc.mode.id),
                    onGo = {
                        // #2: lock check, then the 48 h / 2-per-7-days hard-session guard
                        val h = history ?: runCatching { store.all() }.getOrDefault(emptyList())
                        val g = engine.canStart(sc.mode.id, h, Instant.now())
                        if (g is StartGate.Allowed) countdown = sc.mode else gate = g
                    },
                    onBack = { selected = null },
                )
                is Screen.Countdown -> CountdownScreen(sc.mode) {
                    if (!onStart(sc.mode, maxHr)) countdown = null
                }
                is Screen.Session -> SessionScreen(s)
                is Screen.Finish -> FinishScreen(s) {
                    WorkoutService.clear()
                    selected = null
                    stack.clear()
                }
                is Screen.History -> HistoryScreen(
                    onBack = { pop() },
                    onOpen = { id -> push(Screen.HistoryDetail(id)) },
                )
                is Screen.HistoryDetail -> SessionDetailRoute(id = sc.id, onBack = { pop() })
                is Screen.Export -> ExportScreen(
                    loadSessions = { store.allIo() },
                    onBack = { pop() },
                )
                is Screen.Settings -> SettingsScreen(
                    maxHr = maxHr,
                    onMaxHr = { setMaxHr(it) },
                    onBack = { pop() },
                    onForceUnlockChanged = { engineKey++ },
                )
                is Screen.CustomList -> CustomListScreen(
                    onBack = { pop() },
                    onPick = { selected = it },
                    onNew = { push(Screen.CustomBuilder(null)) },
                    onEdit = { id -> push(Screen.CustomBuilder(id)) },
                )
                is Screen.CustomBuilder -> CustomBuilderScreen(
                    existing = remember(sc.editId) { sc.editId?.let { CustomWorkoutStore(ctx).get(it) } },
                    onSave = { pop() },
                    onCancel = { pop() },
                    onDelete = { pop() },
                )
            }
        }

        // #2 locked / recovery-guard dialog, drawn on top of everything.
        gate?.let { g -> StartGateDialog(g) { gate = null } }
    }
}

// ---------------------------------------------------------------- helpers

private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)
private val Tnum = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)

private fun Context.tick(ms: Long = 60) {
    getSystemService(Vibrator::class.java)
        ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}

private fun glowBg(c: Color, alpha: Float = 0.14f) =
    Brush.radialGradient(listOf(c.copy(alpha = alpha), Neon.Black), radius = 520f)

@Composable
private fun CircleBtn(
    label: String,
    color: Color,
    size: Int = 44,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Neon.Black else color,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 2.6).sp,
        )
    }
}

/** Small pill for the home screen's secondary row. */
@Composable
private fun SecondaryChip(label: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(76.dp)
            .height(30.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(2.dp, color.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- mode list (home)

/** Home: today card, mode cards, then 2x2 square tiles. Widget-card format, minimal text. */
@Composable
fun ModeList(
    progression: ProgressionSnapshot?,
    streak: StreakInfo?,
    onPick: (WorkoutMode) -> Unit,
    onHistory: () -> Unit,
    onCustom: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("VO2 Coach", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Dim)
        }
        if (progression != null) item {
            val plan = progression.plan
            val m = Modes.byId(plan.recommendedModeId)
            val c = levelColor(m?.let { Modes.all.indexOf(it) } ?: 0)
            WidgetCard(c, index = 0, onClick = { m?.let(onPick) }) {
                CardHeader(
                    Icons.Filled.CalendarToday, "Today", c,
                    badge = streak?.currentWeekStreak?.toString(),
                    badgeIcon = Icons.Filled.LocalFireDepartment,
                )
                CardLabel("Week ${plan.weekNumber}")
                CardValue(
                    m?.title?.substringAfter(". ") ?: "Rest",
                    size = 22.sp,
                    metaIcon = Icons.Filled.Flag,
                    meta = "${plan.targetThisWeek}",
                )
                CardSegments(plan.doneThisWeek, plan.targetThisWeek, c)
            }
        }
        items(Modes.all.size) { i ->
            val mode = Modes.all[i]
            ModeCard(mode, progression?.unlockOf(mode.id), index = i + 1) { onPick(mode) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                SquareCard(Icons.Filled.History, "History", Neon.Cyan, index = 6, onClick = onHistory)
                SquareCard(Icons.Filled.Tune, "Build", Neon.Magenta, index = 7, onClick = onCustom)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareCard(Icons.Filled.Sync, "Sync", Neon.Violet, index = 8, onClick = onExport)
                SquareCard(Icons.Filled.Settings, "Settings", Neon.Yellow, index = 8, onClick = onSettings)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun ModeCard(mode: WorkoutMode, unlock: UnlockState? = null, index: Int = 0, onClick: () -> Unit) {
    val idx = Modes.all.indexOf(mode)
    val c = levelColor(idx)
    val locked = unlock != null && !unlock.unlocked
    WidgetCard(c, index = index, dim = locked, onClick = onClick) {
        CardHeader(
            if (locked) Icons.Filled.Lock else modeIcon(mode.id),
            mode.title.substringAfter(". "),
            if (locked) Neon.Dim else c,
        )
        if (locked && unlock != null) {
            CardLabel("Unlock")
            CardValue("${unlock.qualifying}/${unlock.required}", size = 26.sp)
            CardSegments(unlock.qualifying, unlock.required, c)
        } else {
            CardLabel("Time")
            CardValue("${mode.totalSeconds / 60}", unit = "min", metaIcon = Icons.Filled.Bolt, meta = "${idx + 1}/5")
            CardBar((idx + 1) / 5f, c)
        }
    }
}

// ---------------------------------------------------------------- detail

@Composable
fun DetailScreen(
    mode: WorkoutMode,
    maxHr: Int,
    unlock: UnlockState? = null,
    onGo: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var outdoor by remember { mutableStateOf(RunSettings(ctx).outdoorGps) }
    val idx = Modes.all.indexOf(mode)
    val c = if (idx >= 0) levelColor(idx) else Neon.Magenta
    val work = mode.phases.firstOrNull { it.type == PhaseType.WORK }
    val locked = unlock != null && !unlock.unlocked

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); shown = true }
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "strip")

    // Everything fits one round screen: header, big target, strip, chips, action row.
    Column(
        Modifier.fillMaxSize().padding(top = 26.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(modeIcon(mode.id), contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(mode.title.substringAfter(". "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (work != null) {
                    CountUpText((work.lowPct * maxHr).toInt(), 34.sp)
                    Text("–", fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    CountUpText((work.highPct * maxHr).toInt(), 34.sp)
                } else Text("--", fontSize = 34.sp, color = Color.White)
            }
            Text("target bpm", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Neon.Dim)
            PhaseStrip(mode, Modifier.padding(top = 8.dp).width(140.dp).height(18.dp), reveal)
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Neon.Dim, modifier = Modifier.size(14.dp))
                Text("${mode.totalSeconds / 60} min", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Neon.Dim)
                Icon(if (outdoor) Icons.Filled.GpsFixed else Icons.Filled.FitnessCenter, contentDescription = null,
                    tint = if (outdoor) Neon.Lime else Neon.Cyan, modifier = Modifier.size(14.dp))
                Text(if (outdoor) "Outdoor" else "Treadmill", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Neon.Dim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Icons.Filled.ArrowBack, Neon.Dim, 38.dp, onClick = onBack)
            if (locked && unlock != null) {
                PillButton("${unlock.qualifying}/${unlock.required}", Neon.Dim, filled = false, icon = Icons.Filled.Lock, height = 48.dp, onClick = onGo)
            } else {
                IconCircleButton(Icons.Filled.PlayArrow, c, 56.dp, filled = true, onClick = onGo)
            }
            IconCircleButton(
                if (outdoor) Icons.Filled.GpsFixed else Icons.Filled.FitnessCenter,
                if (outdoor) Neon.Lime else Neon.Cyan, 38.dp,
            ) {
                outdoor = !outdoor
                RunSettings(ctx).outdoorGps = outdoor
            }
        }
    }
}

@Composable
fun PhaseStrip(mode: WorkoutMode, modifier: Modifier, reveal: Float = 1f) {
    Canvas(modifier) {
        val total = mode.totalSeconds.toFloat()
        var x = 0f
        val limit = size.width * reveal
        mode.phases.forEach { p ->
            val w = size.width * p.seconds / total
            if (x < limit) {
                val h = size.height * ((p.highPct - 0.4) / 0.6).toFloat().coerceIn(0.15f, 1f)
                drawRoundRect(
                    color = phaseColors(p.type).first,
                    topLeft = Offset(x, size.height - h),
                    size = Size((minOf(w, limit - x) - 2f).coerceAtLeast(1f), h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            x += w
        }
    }
}

/** Live HR line for the last ~90 s with the target band shaded behind it. */
@Composable
fun HrSparkline(data: List<Int>, low: Int, high: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val lo = minOf(data.min(), low) - 4
        val hi = maxOf(data.max(), high) + 4
        val span = (hi - lo).coerceAtLeast(1).toFloat()
        fun y(v: Int) = size.height - (v - lo) / span * size.height
        drawRect(Neon.Lime.copy(alpha = 0.14f), topLeft = Offset(0f, y(high)), size = Size(size.width, y(low) - y(high)))
        val step = size.width / (data.size - 1).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path()
        data.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.5.dp.toPx(), Offset((data.size - 1) * step, y(data.last())))
    }
}

/** Whole-session timeline: each phase a segment, done = solid, current = bright, rest = dim. */
@Composable
fun PhaseProgressBar(s: SessionState, modifier: Modifier) {
    val total = s.totalSec.toFloat().coerceAtLeast(1f)
    val done = 1f - s.totalRemainingSec / total
    val anim by animateFloatAsState(done, tween(1000, easing = LinearEasing), label = "pb")
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(h / 2, h / 2))
        drawRoundRect(Color.White.copy(alpha = 0.85f), size = Size(size.width * anim, h), cornerRadius = CornerRadius(h / 2, h / 2))
    }
}

// ---------------------------------------------------------------- countdown

@Composable
fun CountdownScreen(mode: WorkoutMode, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var n by remember { mutableIntStateOf(3) }
    val line = remember { Hype.pick(Hype.countdown, (System.currentTimeMillis() / 1000).toInt()) }
    val sweep = remember { Animatable(1f) }
    val ripple = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            n = i; ctx.tick(70)
            sweep.snapTo(1f); ripple.snapTo(0f)
            launch { ripple.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            sweep.animateTo(0f, tween(900, easing = LinearEasing))
        }
        n = 0; ctx.tick(350)
        ripple.snapTo(0f)
        launch { ripple.animateTo(1f, tween(550)) }
        delay(550)
        onDone()
    }
    val c by animateColorAsState(
        when (n) { 3 -> Neon.Yellow; 2 -> Neon.Orange; 1 -> Neon.Hot; else -> Neon.Lime },
        tween(300), label = "cdc",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val sw = 10.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arc, style = Stroke(sw))
            drawArc(c, -90f, 360f * sweep.value, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            // ripple from the centre
            val rr = size.minDimension * 0.18f + size.minDimension * 0.32f * ripple.value
            drawCircle(c.copy(alpha = 0.5f * (1f - ripple.value)), rr, style = Stroke(3.dp.toPx()))
        }
        AnimatedContent(
            targetState = n,
            transitionSpec = {
                (scaleIn(tween(320), initialScale = 2.2f) + fadeIn(tween(200))) togetherWith
                    (scaleOut(tween(250), targetScale = 0.4f) + fadeOut(tween(250)))
            },
            label = "cd",
        ) { v ->
            Text(if (v == 0) "GO" else "$v", fontSize = 80.sp, fontWeight = FontWeight.Bold, color = if (v == 0) Neon.Lime else Color.White)
        }
        Text(
            mode.title.substringAfter(". "),
            fontSize = 14.sp, color = c, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
        )
        Text(
            line,
            fontSize = 13.sp, color = Neon.Dim, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}

// ---------------------------------------------------------------- session

@Composable
fun SessionScreen(s: SessionState) {
    val ctx = LocalContext.current
    val phase = s.phase ?: return
    val pc by animateColorAsState(phaseColors(phase.type).first, tween(700), label = "pc")
    val zc by animateColorAsState(zoneColor(s.zone), tween(500), label = "zc")

    val phaseProgress by animateFloatAsState(
        if (phase.seconds > 0) s.remainingSec / phase.seconds.toFloat() else 0f,
        tween(1000, easing = LinearEasing), label = "pp",
    )
    val bpm = (s.heartRate ?: 60).coerceIn(40, 220)
    val inf = rememberInfiniteTransition(label = "inf")
    val beat by inf.animateFloat(1f, 1.25f, infiniteRepeatable(tween(60000 / bpm / 2), RepeatMode.Reverse), label = "beat")
    val glowA by inf.animateFloat(0.10f, 0.28f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val punch by animateFloatAsState(
        if (s.remainingSec in 1..3) 1.15f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "punch",
    )
    val hrShown by animateIntAsState(s.heartRate ?: 0, tween(600), label = "hr")

    // full-screen phase flash
    val flash = remember { Animatable(0f) }
    LaunchedEffect(s.phaseIndex) {
        flash.snapTo(1f)
        flash.animateTo(0f, tween(1300, easing = FastOutSlowInEasing))
    }

    // controls hidden by default: tap the screen to show them for 4 s (shown at start too)
    var controls by remember { mutableStateOf(true) }
    var tapKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapKey, controls) { if (controls) { delay(4000); controls = false } }

    // motivation appears as a short toast whenever the line changes
    val msg = Hype.forSession(s)
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { toast = msg; delay(4500); toast = null }

    var confirmQuit by remember { mutableStateOf(false) }
    val showPace = s.distanceM != null || s.gpsStatus != GpsStatus.OFF
    val showHrr = phase.type != PhaseType.WORK && s.liveHrDrop != null && s.zone != Zone.HIGH

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = true; tapKey++
            },
        contentAlignment = Alignment.Center,
    ) {
        // single clean ring = time left in this phase
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val sw = 7.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arc, style = Stroke(sw))
            drawArc(pc.copy(alpha = glowA), -90f, 360f * phaseProgress, false, tl, arc, style = Stroke(sw * 2.6f, cap = StrokeCap.Round))
            drawArc(pc, -90f, 360f * phaseProgress, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // phase pill
            AnimatedContent(
                targetState = phase.label,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "phase",
            ) { label ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(pc.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(pc))
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pc, maxLines = 1)
                }
            }
            Text(
                fmt(s.remainingSec),
                fontSize = if (showPace) 46.sp else 52.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (s.remainingSec in 1..3) pc else Color.White,
                style = Tnum,
                modifier = Modifier.scale(punch),
            )
            // heart rate + zone gauge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Favorite, contentDescription = null, tint = zc,
                    modifier = Modifier.size(16.dp).scale(if (s.heartRate != null) beat else 1f),
                )
                Spacer(Modifier.width(5.dp))
                Text(if (s.heartRate != null) "$hrShown" else "--", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = Tnum)
            }
            ZoneGauge(s.heartRate, s.targetLow, s.targetHigh, Modifier.padding(top = 4.dp).width(110.dp).height(10.dp))
            if (showPace) PaceReadout(s.paceSecPerKm, s.distanceM, Modifier.padding(top = 3.dp))

            // bottom slot: controls (after a tap) / HR recovery / motivation toast
            Box(Modifier.padding(top = 6.dp).height(44.dp).width(150.dp), contentAlignment = Alignment.Center) {
                val slot = when {
                    controls -> "controls"
                    showHrr -> "hrr"
                    toast != null -> "toast"
                    else -> "none"
                }
                AnimatedContent(
                    targetState = slot,
                    transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.9f)) togetherWith fadeOut(tween(150)) },
                    label = "slot",
                ) { k ->
                    when (k) {
                        "controls" -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconCircleButton(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, Color.White, 42.dp) {
                                WorkoutService.togglePause(ctx); tapKey++
                            }
                            IconCircleButton(Icons.Filled.Stop, Neon.Hot, 42.dp) { confirmQuit = true }
                        }
                        "hrr" -> HrrChip(s.liveHrDrop, s.hrrSecondsLeft)
                        "toast" -> Text(
                            toast ?: "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            textAlign = TextAlign.Center, maxLines = 2,
                        )
                        else -> Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        // phase flash overlay
        if (flash.value > 0.01f) {
            Box(Modifier.fillMaxSize().background(pc.copy(alpha = 0.65f * flash.value)), contentAlignment = Alignment.Center) {
                Text(
                    phaseWord(phase.type), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Neon.Black,
                    modifier = Modifier.graphicsLayer {
                        val sc = 1f + (1f - flash.value) * 0.6f
                        scaleX = sc; scaleY = sc; alpha = flash.value
                    },
                )
            }
        }

        // paused
        AnimatedVisibility(
            visible = s.paused && !confirmQuit,
            enter = fadeIn(tween(200)) + scaleIn(tween(250), initialScale = 0.92f),
            exit = fadeOut(tween(150)),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paused", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(fmt(s.remainingSec) + " left in " + phase.label.lowercase(), fontSize = 13.sp, color = Neon.Dim, style = Tnum)
                    Spacer(Modifier.height(14.dp))
                    PillButton("Resume", Neon.Lime, filled = true, icon = Icons.Filled.PlayArrow, modifier = Modifier.width(150.dp)) {
                        WorkoutService.togglePause(ctx)
                    }
                    Spacer(Modifier.height(8.dp))
                    PillButton("End", Neon.Hot, filled = false, icon = Icons.Filled.Stop, height = 38.dp, modifier = Modifier.width(150.dp)) {
                        confirmQuit = true
                    }
                }
            }
        }

        // end-workout confirmation
        AnimatedVisibility(
            visible = confirmQuit,
            enter = fadeIn(tween(200)) + scaleIn(tween(280), initialScale = 0.9f),
            exit = fadeOut(tween(150)),
        ) {
            val q = remember { Hype.pick(Hype.quit, (System.currentTimeMillis() / 1000).toInt()) }
            Box(Modifier.fillMaxSize().background(Color(0xFA0B0B0E)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("End workout?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(q, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Neon.Hot)
                    Text("${fmt(s.elapsedSec)} done", fontSize = 13.sp, color = Neon.Dim, style = Tnum, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(14.dp))
                    PillButton("Keep going", Neon.Lime, filled = true, modifier = Modifier.width(150.dp)) { confirmQuit = false }
                    Spacer(Modifier.height(8.dp))
                    PillButton("End", Neon.Hot, filled = false, height = 38.dp, modifier = Modifier.width(150.dp)) {
                        WorkoutService.stop(ctx)
                    }
                }
            }
        }
    }
}

/** Where your HR sits: below (blue) / in target (green) / above (red), with a moving marker. */
@Composable
fun ZoneGauge(hr: Int?, low: Int, high: Int, modifier: Modifier) {
    val pad = 25
    val lo = (low - pad).toFloat()
    val hi = (high + pad).toFloat()
    val target = if (hr != null) ((hr - lo) / (hi - lo)).coerceIn(0f, 1f) else -1f
    val pos by animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = "gauge")
    Canvas(modifier) {
        val h = size.height * 0.5f
        val y = (size.height - h) / 2
        val w = size.width
        val a = w * (low - lo) / (hi - lo)
        val b = w * (high - lo) / (hi - lo)
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Neon.Cyan.copy(alpha = 0.45f), topLeft = Offset(0f, y), size = Size(a, h), cornerRadius = r)
        drawRect(Neon.Lime, topLeft = Offset(a + 2f, y), size = Size(b - a - 4f, h))
        drawRoundRect(Neon.Hot.copy(alpha = 0.45f), topLeft = Offset(b, y), size = Size(w - b, h), cornerRadius = r)
        if (pos >= 0f) {
            val x = (w * pos).coerceIn(size.height / 2, w - size.height / 2)
            drawCircle(Color.White, radius = size.height / 2, center = Offset(x, size.height / 2))
            drawCircle(Neon.Black, radius = size.height / 4, center = Offset(x, size.height / 2))
        }
    }
}

// ---------------------------------------------------------------- finish

@Composable
fun FinishScreen(s: SessionState, onOk: () -> Unit) {
    val pct = if (s.workSec > 0) s.workInZoneSec * 100 / s.workSec else 0
    val pc = when {
        pct >= 70 -> Neon.Lime
        pct >= 40 -> Neon.Yellow
        else -> Neon.Hot
    }
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Done", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(s.modeTitle.substringAfter(". "), fontSize = 13.sp, color = Neon.Dim, maxLines = 1)
            }
        }
        item {
            WidgetCard(pc, index = 1) {
                CardHeader(Icons.Filled.TrackChanges, "In zone", pc)
                CardValue("$pct", unit = "%", metaIcon = Icons.Filled.Timer, meta = fmt(s.elapsedSec))
                CardBar(pct / 100f, pc)
            }
        }
        item {
            WidgetCard(Neon.Hot, index = 2) {
                CardHeader(Icons.Filled.Favorite, "Heart rate", Neon.Hot)
                CardLabel("Average")
                CardValue("${s.avgHr ?: "--"}", unit = "bpm", metaIcon = Icons.Filled.ArrowUpward, meta = "${s.peakHr ?: "--"}")
                val a = s.avgHr; val pk = s.peakHr
                CardBar(if (a != null && pk != null && pk > 0) a / pk.toFloat() else 0f, Neon.Hot)
            }
        }
        s.avgHrDrop60s?.let { drop ->
            item {
                WidgetCard(Neon.Cyan, index = 3) {
                    CardHeader(Icons.Filled.FavoriteBorder, "Recovery", Neon.Cyan, badge = HrrRating.of(drop).short)
                    CardLabel("Drop in 60 s")
                    CardValue("$drop", unit = "bpm")
                    CardBar(drop / 40f, Neon.Cyan)
                }
            }
        }
        s.distanceM?.let { m ->
            item {
                WidgetCard(Neon.Lime, index = 4) {
                    CardHeader(Icons.Filled.Place, "Distance", Neon.Lime)
                    CardValue(
                        "%.2f".format(m / 1000.0), unit = "km",
                        metaIcon = Icons.Filled.Speed,
                        meta = s.paceSecPerKm?.let { "%d:%02d".format(it / 60, it % 60) },
                    )
                }
            }
        }
        item {
            Text(
                Hype.verdict(pct), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.width(160.dp),
            )
        }
        item { PrimaryButton("OK", pc, onClick = onOk) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
