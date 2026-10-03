package com.vo2coach.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.em
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import com.vo2coach.MainActivity

/**
 * Neon palette as ARGB ints (ProtoLayout cannot use Compose Color). Mirrors com.vo2coach.Neon.
 */
internal object TileNeon {
    const val Black = 0xFF000000.toInt()
    const val Card = 0xFF111114.toInt()
    const val Dim = 0xFF8A8A93.toInt()
    const val Hot = 0xFFFF2D55.toInt()
    const val Orange = 0xFFFF9100.toInt()
    const val Yellow = 0xFFFFD600.toInt()
    const val Lime = 0xFF00E676.toInt()
    const val Cyan = 0xFF00E5FF.toInt()
    const val Magenta = 0xFFD500F9.toInt()

    /** Same mapping as Theme.kt levelColor(index). */
    fun level(index: Int): Int = when (index) {
        0 -> Lime
        1 -> Yellow
        2 -> Orange
        3 -> Hot
        else -> Magenta
    }
}

/**
 * Builds the tile layout:
 *  - weekly progress ring around the bezel (sessions this week / target)
 *  - "VO2 COACH" title
 *  - streak + week stats
 *  - big circular START button (level color of today's mode) -> MainActivity + "startModeId"
 *  - today's mode name + one headline line
 * The whole tile background opens the app without a mode.
 */
object TileLayout {

    fun root(packageName: String, snap: TileSnapshot, device: DeviceParameters?): LayoutElement {
        val screenW = device?.screenWidthDp?.takeIf { it > 0 } ?: 192
        val btn = (screenW * 0.30f).coerceIn(52f, 68f)
        val accent = snap.pick?.let { TileNeon.level(it.levelIndex) } ?: TileNeon.Lime

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("VO2 COACH", 11f, TileNeon.Hot, bold = true, spacing = 0.12f))
            .addContent(spacer(4f))
            .addContent(statsRow(snap))
            .addContent(spacer(6f))
            .addContent(startButton(packageName, snap, btn, accent))
            .addContent(spacer(5f))
            .addContent(text(snap.pick?.title ?: "OPEN APP", 11f, accent, bold = true, spacing = 0.06f))
            .addContent(spacer(1f))
            .addContent(text(snap.headline, 9f, if (snap.atRisk) TileNeon.Orange else TileNeon.Dim, bold = true, spacing = 0.04f))
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(TileNeon.Black)).build())
                    .setClickable(clickable(TileIntents.CLICK_OPEN, packageName, null))
                    .build(),
            )
            .addContent(progressRing(snap.weekFraction))
            .addContent(column)
            .build()
    }

    // ------------------------------------------------------------ pieces

    private fun statsRow(snap: TileSnapshot): LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(stat(TileModel.streakLabel(snap.streakWeeks), "STREAK", if (snap.atRisk) TileNeon.Orange else TileNeon.Yellow))
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(14f)).build())
            .addContent(stat(TileModel.weekLabel(snap.sessionsThisWeek, snap.weeklyTarget), "WEEK", TileNeon.Lime))
            .build()

    private fun stat(value: String, label: String, color: Int): LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(value, 18f, color, bold = true, spacing = 0f))
            .addContent(text(label, 9f, TileNeon.Dim, bold = true, spacing = 0.1f))
            .build()

    private fun startButton(packageName: String, snap: TileSnapshot, sizeDp: Float, color: Int): LayoutElement {
        val label = text("START", (sizeDp / 4.6f).coerceIn(11f, 15f), TileNeon.Black, bold = true, spacing = 0.06f)
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(color))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(sizeDp / 2f)).build())
                            .build(),
                    )
                    .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(2f)).setColor(argb(TileNeon.Card)).build())
                    .setClickable(clickable(TileIntents.CLICK_START, packageName, snap.startModeId))
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription("Start ${snap.pick?.title ?: "workout"}")
                            .build(),
                    )
                    .build(),
            )
            .addContent(label)
            .build()
    }

    /** Full dim ring + lime arc for this week's progress, starting at 12 o'clock. */
    private fun progressRing(fraction: Float): LayoutElement {
        val arc = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(degrees(360f))
                    .setThickness(dp(4f))
                    .setColor(argb(TileNeon.Card))
                    .build(),
            )
            .build()
        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(arc)
        if (fraction > 0f) {
            box.addContent(
                LayoutElementBuilders.Arc.Builder()
                    .setAnchorAngle(degrees(0f))
                    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(degrees(360f * fraction.coerceIn(0f, 1f)))
                            .setThickness(dp(4f))
                            .setColor(argb(if (fraction >= 1f) TileNeon.Cyan else TileNeon.Lime))
                            .build(),
                    )
                    .build(),
            )
        }
        return box.build()
    }

    private fun clickable(id: String, packageName: String, startModeId: String?): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (startModeId != null) {
            activity.addKeyToExtraMapping(
                TileIntents.EXTRA_START_MODE_ID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(startModeId).build(),
            )
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private fun spacer(heightDp: Float): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean, spacing: Float): LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                    .setLetterSpacing(em(spacing))
                    .build(),
            )
            .build()
}
