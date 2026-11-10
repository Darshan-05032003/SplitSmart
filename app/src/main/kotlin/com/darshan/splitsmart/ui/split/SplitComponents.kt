package com.darshan.splitsmart.ui.split

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.AvatarLogic
import com.darshan.splitsmart.ui.designsystem.components.ButtonSize
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.CountBadge
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squash
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/** Test tags of the split screen that are not shared with other screens (the shared ones stay in `SplitSmartTestTags`). */
object SplitTags {
    const val LIST = "split_list"
    const val DONUT = "split_donut"
    const val MODE_PERCENT = "split_mode_percent"
    const val DOCK = "split_dock"
    const val DOCK_FACE = "split_dock_face"
    const val CLEAR_PICKS = "split_clear_picks"
    const val ALL_ASSIGNED = "split_all_assigned"
    const val CHECK = "split_check"
    const val GHOST = "split_drag_ghost"

    fun handle(itemId: String) = "split_handle_$itemId"
}

/** Degrees between two donut slices: enough to read as separate arcs, small enough that a 1 % share still shows. */
internal const val DONUT_GAP_DEGREES = 3f

/** How much a face grows while a dragged line hovers over it, and how far a gulp squashes it. */
private const val HOVER_SCALE = 1.18f
private const val GULP_WIDEN = 0.22f
private const val GULP_FLATTEN = 0.16f

/** A dimmed face: someone left out of the split. */
private const val LEFT_OUT_ALPHA = 0.4f

/**
 * Each slice's colour, also used by the legend and the rows: the avatar pastels in turn by place on the ring, so neighbouring
 * slices never match (avatars hash by name and often do). Everyone folded into "others" shares one muted colour.
 */
@Composable
fun rememberSliceColors(slices: List<DonutSlice>): SliceColors {
    val tones = SplitSmartTheme.colors.avatars
    val others = SplitSmartTheme.colors.textSecondary.copy(alpha = OTHERS_ALPHA)
    val keys = remember(slices) { slices.map { it.key } }
    return remember(keys, tones, others) {
        val people = keys.filter { it != DonutLogic.OTHERS }
        SliceColors(people.mapIndexed { i, id -> id to tones[DonutLogic.colorIndex(i, people.size, tones.size)].background }.toMap(), others)
    }
}

/** Slice colour per member; anyone not on the ring (folded, or not in the split) gets [others]. */
@androidx.compose.runtime.Immutable
class SliceColors(private val byMember: Map<String, Color>, val others: Color) {
    operator fun get(memberId: String): Color = byMember[memberId] ?: others
}

private const val OTHERS_ALPHA = 0.35f

/**
 * The share donut: one arc per person in their avatar colour. When the split changes (another mode, a line given to someone) every
 * arc morphs from where it is to where it goes on the Settle spring, people who join grow in and people who leave shrink away. The
 * morph runs in draw: no recomposition per frame. Under reduced motion it is a short tween.
 */
@Composable
fun SplitDonut(
    slices: List<DonutSlice>,
    colorOf: SliceColors,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.donut,
    thickness: Dp = Strokes.donut,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = LocalMotion.current
    val track = SplitSmartTheme.colors.raised
    val progress = remember { Animatable(1f) }
    val morph = remember { DonutMorph(slices) }
    LaunchedEffect(slices) {
        if (!morph.retarget(slices, progress.value)) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, motion.spec(MotionToken.Settle))
    }
    val aligned = morph.aligned
    val colors = remember(aligned, colorOf) { aligned.keys.map { colorOf[it] } }
    Box(
        modifier
            .size(size)
            .drawWithCache {
                val stroke = thickness.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val style = Stroke(width = stroke)
                val out = FloatArray(aligned.keys.size * 2)
                onDrawBehind {
                    drawArc(track, 0f, FULL_CIRCLE, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
                    DonutLogic.layout(aligned.from, aligned.to, progress.value, DONUT_GAP_DEGREES, out)
                    for (i in colors.indices) {
                        val sweep = out[i * 2 + 1]
                        if (sweep > 0f) drawArc(colors[i], out[i * 2], sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
                    }
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private const val FULL_CIRCLE = 360f

/** Remembers the morph's two ends. A new target starts from wherever the arcs are now, so an interrupted morph never jumps. */
@Stable
private class DonutMorph(initial: List<DonutSlice>) {
    var aligned by mutableStateOf(DonutLogic.align(emptyList(), initial))
        private set
    private var target = initial
    private var first = true

    /** True when there is something to animate. The very first slices grow in from nothing. */
    fun retarget(slices: List<DonutSlice>, progress: Float): Boolean {
        if (first) {
            first = false
            aligned = DonutLogic.align(emptyList(), slices)
            target = slices
            return slices.isNotEmpty()
        }
        if (slices == target) return false
        val current = aligned.keys.mapIndexed { i, key -> DonutSlice(key, aligned.from[i] + (aligned.to[i] - aligned.from[i]) * progress) }
        aligned = DonutLogic.align(current, slices)
        target = slices
        return true
    }
}

/**
 * Drag-to-assign state for one screen. Faces register where they are; a drag carries item ids and a label under the finger, and
 * the face under it is [hover]. Positions are in root coordinates. The pointer is read in a layer, so moving never recomposes.
 */
@Stable
class DragAssignState {
    var dragging by mutableStateOf<List<String>?>(null)
        private set
    var label by mutableStateOf("")
        private set
    var pointer by mutableStateOf(Offset.Unspecified)
        private set
    var hover by mutableStateOf<String?>(null)
        private set

    /** Where the overlay that draws the dragged label sits in the root. */
    var overlayOrigin: Offset = Offset.Zero

    /** How far outside a face a drop still counts. */
    var slop: Float = 0f

    private val targets = LinkedHashMap<String, Rect>()

    fun register(id: String, bounds: Rect) {
        targets[id] = bounds
    }

    fun unregister(id: String) {
        targets.remove(id)
    }

    fun start(ids: List<String>, label: String, at: Offset) {
        dragging = ids
        this.label = label
        move(at)
    }

    fun move(to: Offset) {
        pointer = to
        val over = DropTargets.hit(to, targets, slop)
        if (over != hover) hover = over
    }

    /** Ends the drag: the ids and the face they were dropped on, or null when they landed nowhere. */
    fun end(): Pair<List<String>, String>? {
        val ids = dragging
        val over = DropTargets.hit(pointer, targets, slop)
        reset()
        return if (ids != null && over != null) ids to over else null
    }

    fun cancel() = reset()

    private fun reset() {
        dragging = null
        hover = null
        pointer = Offset.Unspecified
    }
}

/** The four split modes as radio chips; the morphing donut above shows what each does. */
@Composable
fun ModeSwitcher(option: SplitModeOption, onSelect: (SplitModeOption) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SplitModeOption.entries.forEach { mode ->
            EmojiChip(
                label = stringResource(mode.labelRes()),
                selected = option == mode,
                onClick = { if (option != mode) onSelect(mode) },
                emoji = mode.emoji,
                exclusive = true,
                modifier = Modifier.testTag(mode.tag()),
            )
        }
    }
}

internal fun SplitModeOption.labelRes(): Int = when (this) {
    SplitModeOption.Equal -> R.string.split_mode_equal
    SplitModeOption.ByItem -> R.string.split_mode_by_item
    SplitModeOption.ByShare -> R.string.split_mode_by_share
    SplitModeOption.Percent -> R.string.split_mode_percent
}

private fun SplitModeOption.tag(): String = when (this) {
    SplitModeOption.Equal -> SplitSmartTestTags.SPLIT_MODE_EQUAL
    SplitModeOption.ByItem -> SplitSmartTestTags.SPLIT_MODE_BY_ITEM
    SplitModeOption.ByShare -> SplitSmartTestTags.SPLIT_MODE_BY_SHARE
    SplitModeOption.Percent -> SplitTags.MODE_PERCENT
}

/**
 * A row of faces to choose from: who paid (one, [exclusive]) or who is in (any). A chosen face wears a marigold ring and a check,
 * one left out fades. Lazy, so 200 people cost a screenful.
 */
@Composable
fun FaceChooser(
    members: List<Member>,
    isChosen: (String) -> Boolean,
    onToggle: (String) -> Unit,
    exclusive: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val inSplit = stringResource(R.string.split_in_split)
    val leftOut = stringResource(R.string.split_out_of_split)
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(members, key = { it.id }, contentType = { "face" }) { member ->
            val chosen = isChosen(member.id)
            val name = DisplayText.safe(member.name)
            ChoiceFace(
                name = member.name,
                chosen = chosen,
                dimmed = !exclusive && !chosen,
                description = if (exclusive) name else "$name, ${if (chosen) inSplit else leftOut}",
                role = if (exclusive) Role.RadioButton else Role.Checkbox,
                onClick = { onToggle(member.id) },
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

@Composable
private fun ChoiceFace(name: String, chosen: Boolean, dimmed: Boolean, description: String, role: Role, onClick: () -> Unit, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressFeedback()
    val alpha by animateFloatAsState(if (dimmed) LEFT_OUT_ALPHA else 1f, motion.spec(MotionToken.Snap), label = "faceAlpha")
    Column(
        modifier
            .width(Sizes.avatarLarge + Spacing.md)
            .trackPress(press)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = role) {
                haptics.tick()
                onClick()
            }
            .squash(press, Squash.Subtle)
            .semantics {
                contentDescription = description
                selected = chosen
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
            MemberAvatar(name, size = Sizes.avatarLarge, ring = chosen, ringColor = colors.accent, contentDescription = null)
            if (chosen) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(Sizes.counterBadge).background(colors.primary, Radii.circleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("✓", style = type.labelStrong.copy(color = colors.onPrimary))
                }
            }
        }
        BasicText(
            DisplayText.safe(name),
            Modifier.clearAndSetSemantics {},
            style = type.label.copy(color = colors.text, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The faces lines are dragged onto. Each counts the lines it was given (the count bumps) and gulps when one lands: a quick squash
 * that springs back on the Bouncy spring. A face grows while a dragged line hovers over it. With lines picked by long-press, a
 * tap hands them over (also the TalkBack path).
 */
@Composable
fun MemberDock(
    members: List<Member>,
    counts: Map<String, Int>,
    picked: Int,
    drag: DragAssignState,
    onGivePicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier.fillMaxWidth().testTag(SplitTags.DOCK),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(members, key = { it.id }, contentType = { "dockFace" }) { member ->
            DockFace(member, counts[member.id] ?: 0, picked, hovered = drag.hover == member.id, drag = drag, onGivePicked = { onGivePicked(member.id) })
        }
    }
}

@Composable
private fun DockFace(member: Member, count: Int, picked: Int, hovered: Boolean, drag: DragAssignState, onGivePicked: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val name = DisplayText.safe(member.name)
    val description = pluralStringResource(R.plurals.split_face_items, count, name, count)
    val giveLabel = stringResource(R.string.split_give_picked, name)
    val gulp = remember { Animatable(0f) }
    val last = remember { intArrayOf(count) }
    LaunchedEffect(count) {
        val grew = count > last[0]
        last[0] = count
        if (grew && !motion.reduced) {
            gulp.snapTo(1f)
            gulp.animateTo(0f, motion.spec(MotionToken.Bump))
        }
    }
    val hover = animateFloatAsState(if (hovered) HOVER_SCALE else 1f, motion.spec(MotionToken.Snap), label = "hover")
    Column(
        Modifier
            .width(Sizes.avatarLarge + Spacing.md)
            .testTag(SplitTags.DOCK_FACE)
            .onGloballyPositioned { drag.register(member.id, it.boundsInRoot()) }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = picked > 0, role = Role.Button, onClickLabel = giveLabel) { onGivePicked() }
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            Modifier.graphicsLayer {
                val g = gulp.value
                scaleX = hover.value * (1f + GULP_WIDEN * g)
                scaleY = hover.value * (1f - GULP_FLATTEN * g)
            },
        ) {
            MemberAvatar(member.name, size = Sizes.avatarLarge, ring = hovered || picked > 0, ringColor = if (hovered) colors.primary else colors.hairline, contentDescription = null)
            CountBadge(count, Modifier.align(Alignment.TopEnd))
        }
        BasicText(name, Modifier.clearAndSetSemantics {}, style = type.label.copy(color = colors.text, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    androidx.compose.runtime.DisposableEffect(member.id) { onDispose { drag.unregister(member.id) } }
}

/**
 * One receipt line in "by item": a grip to drag it onto a face, the line, who has it ("Shared by everyone" until someone does) and
 * its amount. Tap opens chips to pick owners one by one; long-press picks it to hand over with others. TalkBack gets pick and
 * choose-owners actions instead of the drag.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemAssignRow(
    item: BillItem,
    owners: List<Member>,
    participants: List<Member>,
    open: Boolean,
    picked: Boolean,
    drag: DragAssignState,
    dragIds: () -> List<String>,
    dragLabel: () -> String,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onToggleOwner: (String) -> Unit,
    onDrop: (List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val haptics = LocalHaptics.current
    val press = rememberPressFeedback()
    val pickLabel = stringResource(if (picked) R.string.split_unpick else R.string.split_pick)
    val ownersLabel = stringResource(R.string.split_edit_owners)
    val currentPick by rememberUpdatedState(onPick)
    val currentOpen by rememberUpdatedState(onOpen)
    ChunkyCard(
        modifier.fillMaxWidth(),
        color = if (picked) colors.raised else colors.surface,
        contentPadding = PaddingValues(start = Spacing.xxs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(item, drag, dragIds, dragLabel, onDrop)
            Column(
                Modifier
                    .weight(1f)
                    .trackPress(press)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = ownersLabel,
                        onLongClickLabel = pickLabel,
                        onLongClick = {
                            haptics.tick()
                            currentPick()
                        },
                        onClick = { currentOpen() },
                    )
                    .squash(press, Squash.Subtle)
                    .semantics(mergeDescendants = true) {
                        selected = picked
                        customActions = listOf(CustomAccessibilityAction(pickLabel) { currentPick(); true })
                    }
                    .padding(vertical = Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (picked) CheckDot()
                    BasicText(DisplayText.safe(item.name), Modifier.weight(1f), style = type.bodyStrong.copy(color = colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AmountText(item.totalPaise, style = type.amountSmall, color = colors.text)
                }
                val ownerText = if (owners.isEmpty()) stringResource(R.string.split_everyone) else owners.joinToString(", ") { DisplayText.safe(it.name) }
                BasicText(
                    if (item.qty > 1) "× ${item.qty} · $ownerText" else ownerText,
                    style = type.label.copy(color = if (owners.isEmpty()) colors.textSecondary else colors.accent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (open) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = Spacing.sm, top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                participants.forEach { member ->
                    EmojiChip(
                        DisplayText.safe(member.name),
                        selected = owners.any { it.id == member.id },
                        onClick = { onToggleOwner(member.id) },
                        modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_ITEM_CHIP),
                    )
                }
            }
        }
    }
}

@Composable
private fun DragHandle(item: BillItem, drag: DragAssignState, dragIds: () -> List<String>, dragLabel: () -> String, onDrop: (List<String>, String) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val haptics = LocalHaptics.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val ids by rememberUpdatedState(dragIds)
    val label by rememberUpdatedState(dragLabel)
    val drop by rememberUpdatedState(onDrop)
    val handleLabel = stringResource(R.string.split_drag_handle, DisplayText.safe(item.name))
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .testTag(SplitTags.handle(item.id))
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { local ->
                        val coords = coordinates[0] ?: return@detectDragGestures
                        haptics.tick()
                        drag.start(ids(), label(), coords.localToRoot(local))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val coords = coordinates[0] ?: return@detectDragGestures
                        drag.move(coords.localToRoot(change.position))
                    },
                    onDragEnd = { drag.end()?.let { (dropped, member) -> drop(dropped, member) } },
                    onDragCancel = { drag.cancel() },
                )
            }
            .semantics { contentDescription = handleLabel },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("⠿", style = type.title.copy(color = colors.textSecondary))
    }
}

/** What follows the finger while a line is dragged: a marigold pill with the line's name (or "3 lines"). */
@Composable
fun DragGhost(drag: DragAssignState, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    if (drag.dragging == null) return
    Box(
        modifier
            .testTag(SplitTags.GHOST)
            .graphicsLayer {
                val p = drag.pointer
                if (p.x.isNaN()) return@graphicsLayer
                // Centred above the finger, clear of the face it hovers over, so the face's growth stays visible.
                translationX = p.x - drag.overlayOrigin.x - size.width / 2f
                translationY = p.y - drag.overlayOrigin.y - size.height - Sizes.avatarLarge.toPx() / 2f - Spacing.xs.toPx()
                shadowElevation = 0f
            }
            .background(colors.primary, Radii.pillShape)
            .border(Strokes.outline, colors.primaryDepth, Radii.pillShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clearAndSetSemantics {},
    ) {
        BasicText("🍽️ ${drag.label}", style = type.bodyStrong.copy(color = colors.onPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One person in the split: their colour (the donut's legend), face, name, their amount (it rolls when it changes) and, full width
 * below, what it is made of: items, tax, tip and a discount, other charges or round-off. The payer is marked "paid". In shares and
 * percent modes the row carries its own steppers and field.
 */
@Composable
fun PersonShareRow(
    member: Member,
    share: MemberShare?,
    sliceColor: Color,
    isPayer: Boolean,
    option: SplitModeOption,
    shareText: String,
    totalShares: Int,
    onShareText: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val name = DisplayText.safe(member.name)
    val spokenAmount = share?.let { stringResource(R.string.split_person_amount, name, AmountFormat.format(it.totalPaise)) } ?: name
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW), contentPadding = PaddingValues(Spacing.md)) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spokenAmount }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box {
                    MemberAvatar(member.name, size = Sizes.avatar, contentDescription = null)
                    ColorDot(sliceColor, Modifier.align(Alignment.BottomEnd))
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    BasicText(name, Modifier.weight(1f, fill = false), style = type.bodyStrong.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isPayer) PaidTag()
                }
                if (share != null) {
                    RollingAmount(share.totalPaise, style = type.amount, color = colors.text)
                } else {
                    BasicText("—", style = type.amount.copy(color = colors.textSecondary))
                }
            }
            if (share != null) {
                BasicText(shareBreakdown(share), style = type.label.copy(color = colors.textSecondary))
            }
        }
        if (option == SplitModeOption.ByShare || option == SplitModeOption.Percent) {
            ShareStepper(name, shareText, totalShares, option == SplitModeOption.Percent, onShareText, onStep)
        }
    }
}

/** A person's donut colour, as a dot with a surface ring so it reads on any avatar. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Sizes.statusDot + Strokes.focus * 2)
            .background(SplitSmartTheme.colors.surface, Radii.circleShape)
            .padding(Strokes.focus)
            .background(color, Radii.circleShape),
    )
}

/** A marigold tick in a circle: a picked line. */
@Composable
private fun CheckDot() {
    val colors = SplitSmartTheme.colors
    Box(Modifier.size(Sizes.counterBadge).background(colors.primary, Radii.circleShape), contentAlignment = Alignment.Center) {
        BasicText("✓", style = SplitSmartTheme.type.labelStrong.copy(color = colors.onPrimary))
    }
}

/** What a share is made of, in one line: items, tax, tip, and a discount, other charges or round-off when there is one. */
@Composable
fun shareBreakdown(share: MemberShare): String {
    val base = stringResource(
        R.string.bill_share_breakdown,
        AmountFormat.format(share.itemsPaise),
        AmountFormat.format(share.taxPaise),
        AmountFormat.format(share.tipPaise),
    )
    val amount = AmountFormat.format(share.adjustmentPaise)
    val extra = when (SplitLogic.adjustmentKind(share.adjustmentPaise)) {
        null -> null
        AdjustmentKind.RoundOff -> stringResource(R.string.split_breakdown_round_off, amount)
        AdjustmentKind.Discount -> stringResource(R.string.split_breakdown_discount, amount)
        AdjustmentKind.Charges -> stringResource(R.string.split_breakdown_charges, amount)
    }
    return if (extra != null) "$base · $extra" else base
}

/** The "paid" tag on the payer's row: mint like money coming back. Plain text, so TalkBack reads it with the row. */
@Composable
fun PaidTag(modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .background(colors.success.container, Radii.pillShape)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(R.string.split_paid_badge), style = SplitSmartTheme.type.labelStrong.copy(color = colors.success.content))
    }
}

@Composable
private fun ShareStepper(name: String, text: String, totalShares: Int, percent: Boolean, onText: (String) -> Unit, onStep: (Int) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val less = stringResource(R.string.split_step_down, name)
    val more = stringResource(R.string.split_step_up, name)
    val value = text.trim().toIntOrNull() ?: 0
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BasicText(
            if (percent) stringResource(R.string.split_percent_of, value) else pluralStringResource(R.plurals.split_share_of, totalShares, value, totalShares),
            Modifier.weight(1f).padding(bottom = Spacing.md),
            style = type.label.copy(color = colors.textSecondary),
            maxLines = 2,
        )
        ChunkyButton("−", { onStep(-1) }, Modifier.semantics { contentDescription = less }, style = ButtonStyle.Secondary, size = ButtonSize.Compact, debounceMs = 0)
        SsTextField(
            value = text,
            onValueChange = onText,
            label = stringResource(if (percent) R.string.split_percent_label else R.string.split_share_label),
            emoji = if (percent) "%" else "×",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(Sizes.touchTarget * 2).testTag(SplitSmartTestTags.SPLIT_SHARE_FIELD),
        )
        ChunkyButton("+", { onStep(1) }, Modifier.semantics { contentDescription = more }, style = ButtonStyle.Secondary, size = ButtonSize.Compact, debounceMs = 0)
    }
}
