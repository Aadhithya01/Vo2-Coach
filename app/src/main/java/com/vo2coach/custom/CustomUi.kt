package com.vo2coach.custom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.wear.compose.material.Icon
import com.vo2coach.ui.CardHeader
import com.vo2coach.ui.CardLabel
import com.vo2coach.ui.CardValue
import com.vo2coach.ui.WidgetCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Text
import com.vo2coach.Neon
import com.vo2coach.WorkoutMode
import com.vo2coach.phaseColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Navigation state for feature #6, held by MainActivity.App as `var customNav by remember { mutableStateOf<CustomNav?>(null) }`. */
sealed class CustomNav {
    object Browse : CustomNav()
    /** [editId] null = new workout. */
    data class Builder(val editId: String?) : CustomNav()
}

fun intensityColor(i: WorkIntensity): Color = when (i) {
    WorkIntensity.MODERATE -> Neon.Yellow
    WorkIntensity.HARD -> Neon.Orange
    WorkIntensity.VO2 -> Neon.Hot
    WorkIntensity.MAX -> Neon.Magenta
}

fun recoveryColor(r: RecoveryType): Color = when (r) {
    RecoveryType.JOG -> Neon.Cyan
    RecoveryType.EASY -> Neon.Blue
    RecoveryType.WALK -> Neon.Lime
}

private val Tnum = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)
private fun fmt(sec: Int) = CustomWorkout.fmtDuration(sec)
private fun fmtMin(min: Int) = if (min == 0) "OFF" else "$min MIN"

// ---------------------------------------------------------------- home entry

/** Card for the home ModeList, styled like ModeCard. Self-loads the saved count. */
@Composable
fun BuildYourOwnCard(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val rev by CustomWorkoutStore.revision.collectAsState()
    val count = remember(rev) { CustomWorkoutStore(ctx).count() }
    val c = Neon.Magenta
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(shape)
            .background(Neon.Card)
            .border(2.dp, c.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Build your own", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (count == 0) "CUSTOM · YOUR RULES" else "CUSTOM · $count SAVED",
                fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

// ---------------------------------------------------------------- list

@Composable
fun CustomListScreen(
    onBack: () -> Unit,
    onPick: (WorkoutMode) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { CustomWorkoutStore(ctx) }
    val rev by CustomWorkoutStore.revision.collectAsState()
    val list = remember(rev) { store.all() }
    val full = list.size >= CustomBounds.MAX_SAVED

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Your builds", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        item {
            NewCard(enabled = !full, onClick = onNew)
        }
        if (full) item {
            Text(
                "Limit reached.",
                fontSize = 12.sp, color = Neon.Dim, textAlign = TextAlign.Center,
                modifier = Modifier.width(150.dp),
            )
        }
        if (list.isEmpty()) item {
            Text(
                "No builds yet.",
                fontSize = 13.sp, color = Neon.Dim, textAlign = TextAlign.Center,
                modifier = Modifier.width(150.dp).padding(vertical = 6.dp),
            )
        }
        items(list, key = { it.id }) { w ->
            CustomCard(w, onClick = { onPick(w.toWorkoutMode()) }, onEdit = { onEdit(w.id) })
        }
        item {
            Text("back", fontSize = 13.sp, color = Neon.Dim, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
    }
}

@Composable
private fun NewCard(enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) Neon.Lime else Neon.Dim
    WidgetCard(c, dim = !enabled, onClick = if (enabled) onClick else null) {
        CardHeader(Icons.Filled.Add, "New workout", c)
    }
}

@Composable
private fun CustomCard(w: CustomWorkout, onClick: () -> Unit, onEdit: () -> Unit) {
    val c = intensityColor(w.workIntensity)
    WidgetCard(c, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardHeader(Icons.Filled.Tune, w.name, c) }
            Icon(
                Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onEdit).padding(7.dp),
            )
        }
        CardLabel("${w.reps} x ${w.workSec}s")
        CardValue("${w.totalSeconds / 60}", unit = "min")
        MiniStrip(w, Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp))
    }
}

// ---------------------------------------------------------------- builder

/**
 * Builder/editor. Persists through [CustomWorkoutStore] itself; [onSave] gets the saved workout afterwards.
 * [onDelete] (shown only when [existing] != null) is called after the workout was deleted from the store.
 */
@Composable
fun CustomBuilderScreen(
    existing: CustomWorkout?,
    onSave: (CustomWorkout) -> Unit,
    onCancel: () -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val store = remember { CustomWorkoutStore(ctx) }
    var w by remember { mutableStateOf(existing?.clamped() ?: CustomWorkout.template()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val full = remember { existing == null && store.isFull() }

    val issue = CustomValidator.firstMessage(w)
    val canSave = issue == null && !full

    fun update(next: CustomWorkout) {
        w = next.copy(name = next.autoName())
        error = null
    }

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            Text(
                if (existing == null) "BUILD IT" else "EDIT",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Neon.Magenta, letterSpacing = 1.sp,
            )
        }
        // live preview
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(w.totalSeconds), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, style = Tnum)
                Text(
                    "TOTAL · ${w.name}", fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(150.dp), textAlign = TextAlign.Center,
                )
            }
        }
        item { MiniStrip(w, Modifier.fillMaxWidth(0.78f).height(26.dp)) }

        item {
            Stepper("WARM-UP", fmtMin(w.warmupMin), Neon.Yellow,
                onMinus = { update(w.copy(warmupMin = CustomBounds.step(w.warmupMin, -1, CustomBounds.WARMUP_MIN, CustomBounds.WARMUP_MAX, CustomBounds.MIN_STEP))) },
                onPlus = { update(w.copy(warmupMin = CustomBounds.step(w.warmupMin, 1, CustomBounds.WARMUP_MIN, CustomBounds.WARMUP_MAX, CustomBounds.MIN_STEP))) },
            )
        }
        item {
            Stepper("REPS", "${w.reps}", Color.White,
                onMinus = { update(w.copy(reps = CustomBounds.step(w.reps, -1, CustomBounds.REPS_MIN, CustomBounds.REPS_MAX, CustomBounds.REPS_STEP))) },
                onPlus = { update(w.copy(reps = CustomBounds.step(w.reps, 1, CustomBounds.REPS_MIN, CustomBounds.REPS_MAX, CustomBounds.REPS_STEP))) },
            )
        }
        item {
            Stepper("WORK", fmt(w.workSec), Neon.Hot,
                onMinus = { update(w.copy(workSec = CustomBounds.step(w.workSec, -1, CustomBounds.WORK_MIN_SEC, CustomBounds.WORK_MAX_SEC, CustomBounds.WORK_STEP_SEC))) },
                onPlus = { update(w.copy(workSec = CustomBounds.step(w.workSec, 1, CustomBounds.WORK_MIN_SEC, CustomBounds.WORK_MAX_SEC, CustomBounds.WORK_STEP_SEC))) },
            )
        }
        item {
            val values = WorkIntensity.values()
            val i = w.workIntensity.ordinal
            Stepper("INTENSITY", w.workIntensity.label, intensityColor(w.workIntensity),
                sub = "${(w.workIntensity.lowPct * 100).toInt()}–${(w.workIntensity.highPct * 100).toInt()}% MAX HR",
                onMinus = { update(w.copy(workIntensity = values[(i - 1).coerceAtLeast(0)])) },
                onPlus = { update(w.copy(workIntensity = values[(i + 1).coerceAtMost(values.size - 1)])) },
            )
        }
        item {
            Stepper("RECOVERY", if (w.reps > 1) fmt(w.recoverySec) else "—", Neon.Cyan,
                onMinus = { update(w.copy(recoverySec = CustomBounds.step(w.recoverySec, -1, CustomBounds.REC_MIN_SEC, CustomBounds.REC_MAX_SEC, CustomBounds.REC_STEP_SEC))) },
                onPlus = { update(w.copy(recoverySec = CustomBounds.step(w.recoverySec, 1, CustomBounds.REC_MIN_SEC, CustomBounds.REC_MAX_SEC, CustomBounds.REC_STEP_SEC))) },
            )
        }
        item {
            val values = RecoveryType.values()
            val i = w.recoveryType.ordinal
            Stepper("REC TYPE", w.recoveryType.label, recoveryColor(w.recoveryType),
                sub = "${(w.recoveryType.lowPct * 100).toInt()}–${(w.recoveryType.highPct * 100).toInt()}% MAX HR",
                onMinus = { update(w.copy(recoveryType = values[(i - 1 + values.size) % values.size])) },
                onPlus = { update(w.copy(recoveryType = values[(i + 1) % values.size])) },
            )
        }
        item {
            Stepper("COOL-DOWN", fmtMin(w.cooldownMin), Neon.Violet,
                onMinus = { update(w.copy(cooldownMin = CustomBounds.step(w.cooldownMin, -1, CustomBounds.COOLDOWN_MIN, CustomBounds.COOLDOWN_MAX, CustomBounds.MIN_STEP))) },
                onPlus = { update(w.copy(cooldownMin = CustomBounds.step(w.cooldownMin, 1, CustomBounds.COOLDOWN_MIN, CustomBounds.COOLDOWN_MAX, CustomBounds.MIN_STEP))) },
            )
        }

        // firm-but-safe validation
        if (issue != null) item {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .width(160.dp)
                    .clip(shape)
                    .background(Neon.Hot.copy(alpha = 0.12f))
                    .border(2.dp, Neon.Hot, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NOT SAFE. NOT SAVED.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Neon.Hot, letterSpacing = 1.sp)
                Text(issue, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                Text(
                    "FIX IT",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neon.Black, letterSpacing = 1.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Neon.Hot)
                        .clickable { update(CustomValidator.autoFix(w)) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        if (full) item {
            Text(
                "20 saved. Delete one first.",
                fontSize = 12.sp, color = Neon.Hot, textAlign = TextAlign.Center, modifier = Modifier.width(150.dp),
            )
        }
        if (error != null) item {
            Text(error!!, fontSize = 12.sp, color = Neon.Hot, textAlign = TextAlign.Center, modifier = Modifier.width(150.dp))
        }

        // save
        item {
            val c = if (canSave) Neon.Lime else Neon.Dim
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (canSave) c else c.copy(alpha = 0.15f))
                    .border(2.dp, c, CircleShape)
                    .then(
                        if (canSave) Modifier.clickable {
                            when (val r = store.save(w)) {
                                is SaveResult.Saved -> onSave(r.workout)
                                is SaveResult.Full -> error = "20 saved. Delete one first."
                                is SaveResult.Invalid -> error = r.issues.firstOrNull()?.let { CustomValidator.message(it, w) }
                                is SaveResult.Error -> error = r.message
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("SAVE", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (canSave) Neon.Black else c)
            }
        }

        // delete (edit mode only)
        if (existing != null && onDelete != null) item {
            if (!confirmDelete) {
                Text(
                    "delete", fontSize = 13.sp, color = Neon.Hot,
                    modifier = Modifier.clickable { confirmDelete = true }.padding(6.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DELETE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neon.Hot,
                        modifier = Modifier.clickable {
                            store.delete(existing.id)
                            onDelete(existing.id)
                        }.padding(6.dp),
                    )
                    Text(
                        "KEEP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neon.Dim,
                        modifier = Modifier.clickable { confirmDelete = false }.padding(6.dp),
                    )
                }
            }
        }
        item {
            Text("cancel", fontSize = 13.sp, color = Neon.Dim, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

/** One big −/+ row: [label] small, value large. Long-press on a button auto-repeats. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    color: Color,
    sub: String? = null,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier.width(170.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepBtn("−", color, onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Neon.Dim, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = color, style = Tnum, maxLines = 1)
            if (sub != null) Text(sub, fontSize = 11.sp, color = Neon.Dim, maxLines = 1)
        }
        StepBtn("+", color, onPlus)
    }
}

@Composable
private fun StepBtn(label: String, color: Color, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    var repeat by remember { mutableStateOf<Job?>(null) }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(2.dp, color, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        repeat?.cancel()
                        repeat = null
                    },
                    onLongPress = {
                        repeat?.cancel()
                        repeat = scope.launch {
                            while (true) {
                                step()
                                delay(110)
                            }
                        }
                    },
                    onTap = { step() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

/** Private PhaseStrip clone: width ∝ duration, height ∝ target intensity. */
@Composable
private fun MiniStrip(w: CustomWorkout, modifier: Modifier) {
    val phases = w.phases()
    Canvas(modifier) {
        val total = phases.sumOf { it.seconds }.toFloat()
        if (total <= 0f) return@Canvas
        var x = 0f
        phases.forEach { p ->
            val pw = size.width * p.seconds / total
            val h = size.height * ((p.highPct - 0.4) / 0.6).toFloat().coerceIn(0.15f, 1f)
            drawRect(
                color = phaseColors(p.type).first,
                topLeft = Offset(x, size.height - h),
                size = Size((pw - 1.5f).coerceAtLeast(1f), h),
            )
            x += pw
        }
    }
}
