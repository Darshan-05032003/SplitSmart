package com.darshan.splitsmart.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.relativeDate
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.ButtonSize
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.IconButtonSize
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.light
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.navigation.TransformAnchor
import com.darshan.splitsmart.ui.navigation.containerTransform
import com.darshan.splitsmart.ui.navigation.listContainerTransform
import com.darshan.splitsmart.ui.navigation.groupContainerKey
import com.darshan.splitsmart.ui.theme.LocalMarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tags of the groups home. */
object GroupsTags {
    const val LIST = "groups_list"
    const val HERO_AMOUNT = "groups_hero_amount"
    const val MENU_SCAN = "groups_menu_scan"
    const val MENU_EXPENSE = "groups_menu_expense"
    const val MENU_NEW_GROUP = "groups_menu_new_group"
    const val MENU_SCRIM = "groups_menu_scrim"
    const val ARCHIVED_TOGGLE = "groups_archived_toggle"
    const val ARCHIVED_ITEM = "groups_archived_item"
    const val ALL_ARCHIVED = "groups_all_archived"
    const val CREATE_SHEET = "groups_create_sheet"
    const val EXPENSE_PICKER = "groups_expense_picker"
    fun unarchive(groupId: String) = "groups_unarchive_$groupId"
}

/**
 * The hero sits on the bar's aubergine in both themes, so it reads the night palette ([MarigoldDark]) for its ink: every text and
 * money colour on it is one of the night palette's contrast-tested pairs (see `GroupsHeroContrastTest`).
 */
val HeroPalette: MarigoldColors get() = MarigoldDark

/** The hero's amount colour for each mood, from the night palette. */
fun heroTint(mood: HeroMood, palette: MarigoldColors = HeroPalette): Color = when (mood) {
    HeroMood.Owed, HeroMood.Square -> palette.success.content
    HeroMood.Owe -> palette.failed.content
    HeroMood.ToSettle -> palette.primary
    HeroMood.Nothing -> palette.textSecondary
}

/**
 * "Where do I stand?" in one glance: your net balance over every group as a rolling odometer whose colour morphs between mint
 * (owed), chilli (you owe) and marigold (still to settle). The colour is applied in the draw phase, so the morph never recomposes.
 * Without your name it shows what is left to settle and offers to add it.
 */
@Composable
fun BalanceHero(overview: HomeOverview, onEditName: () -> Unit, modifier: Modifier = Modifier) {
    val hero = remember(overview) { GroupsHomeLogic.hero(overview) }
    val surface = SplitSmartTheme.colors.bar
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.HOME_BALANCE), color = surface, shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        CompositionLocalProvider(LocalMarigoldColors provides HeroPalette) { HeroContent(hero, overview, onEditName) }
    }
}

@Composable
private fun ColumnScope.HeroContent(hero: HeroBalance, overview: HomeOverview, onEditName: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val greeting = if (!hero.askForName && overview.yourName != null) {
        stringResource(R.string.home_hi, DisplayText.safe(overview.yourName))
    } else {
        pluralStringResource(R.plurals.home_across_groups, overview.groupCount, overview.groupCount)
    }
    val moodLabel = stringResource(
        when (hero.mood) {
            HeroMood.Owed -> R.string.home_overall_owed
            HeroMood.Owe -> R.string.home_overall_owe
            HeroMood.Square -> R.string.home_all_square
            HeroMood.ToSettle -> R.string.home_still_to_settle
            HeroMood.Nothing -> R.string.home_nothing_to_settle
        },
    )
    val tint = animateColorAsState(heroTint(hero.mood), motion.spec(MotionToken.Settle), label = "heroMood")
    // The breakdown comes and goes with the balance: the hero eases to its new height (layout phase, no recomposition).
    Column(
        Modifier.fillMaxWidth().animateContentSize(motion.spec(MotionToken.Settle)).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        BasicText(greeting, style = type.labelStrong.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(moodLabel, style = type.bodyStrong.copy(color = colors.text))
        RollingAmount(
            hero.paise,
            Modifier
                .testTag(GroupsTags.HERO_AMOUNT)
                // Drawn in the text colour, then recoloured in the draw phase: the morph redraws, it never recomposes.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(tint.value, blendMode = BlendMode.SrcIn)
                },
            style = type.hero,
            color = colors.text,
        )
        if (hero.showBreakdown) {
            FlowRow(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatusPill(StatusKind.Owed, stringResource(R.string.home_you_get, AmountFormat.format(overview.youAreOwedPaise)), animate = false)
                StatusPill(StatusKind.Owe, stringResource(R.string.home_you_pay, AmountFormat.format(overview.youOwePaise)), animate = false)
            }
        }
    }
    if (hero.askForName) {
        val name = overview.yourName
        TextAction(
            if (name == null || !hero.nameNotFound) stringResource(R.string.home_set_name) else stringResource(R.string.home_name_not_found, DisplayText.safe(name)),
            onEditName,
            Modifier.padding(top = Spacing.xs).testTag(SplitSmartTestTags.SET_NAME_PROMPT),
        )
    }
}

/** A group's emoji on a rounded tile ([container] defaults to the raised surface). */
@Composable
fun GroupTile(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Sizes.avatarLarge,
    container: Color = SplitSmartTheme.colors.raised,
) {
    val colors = SplitSmartTheme.colors
    Box(modifier.size(size).background(container, Radii.smallShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        BasicText(groupEmoji(name), style = SplitSmartTheme.type.display.copy(color = colors.text))
    }
}

/** The pill text and its status for a card. */
@Composable
fun CardBalance.label(): Pair<StatusKind, String> = when (kind) {
    CardBalanceKind.Owed -> StatusKind.Owed to stringResource(R.string.balance_you_are_owed, AmountFormat.format(paise))
    CardBalanceKind.Owe -> StatusKind.Owe to stringResource(R.string.balance_you_owe, AmountFormat.format(paise))
    CardBalanceKind.YouSquare -> StatusKind.Settled to stringResource(R.string.balance_you_square)
    CardBalanceKind.ToSettle -> StatusKind.Neutral to stringResource(R.string.balance_to_settle, AmountFormat.format(paise))
    CardBalanceKind.NoBills -> StatusKind.Neutral to stringResource(R.string.balance_no_bills)
    CardBalanceKind.Settled -> StatusKind.Settled to stringResource(R.string.balance_settled)
}

/**
 * A group: emoji tile, name, who and how much, and where you stand. One TalkBack sentence ("Goa trip, 3 members, 4 bills, you're
 * owed ₹1,118.70") with Open as the tap and Archive as an action, and the card is one end of the card-to-screen transform (in a
 * long list only while [anchor] points at it).
 */
@Composable
fun GroupCardView(card: GroupCard, onClick: () -> Unit, onArchive: (() -> Unit)?, modifier: Modifier = Modifier, anchor: TransformAnchor? = null) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val members = pluralStringResource(R.plurals.member_count, card.summary.memberCount, card.summary.memberCount)
    val bills = pluralStringResource(R.plurals.bill_count, card.summary.billCount, card.summary.billCount)
    val (kind, balance) = GroupsHomeLogic.cardBalance(card).label()
    val name = DisplayText.safe(card.group.name)
    val description = stringResource(R.string.group_card_description_balance, name, members, bills, balance)
    val archiveLabel = stringResource(R.string.groups_archive)
    ChunkyCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (anchor != null) Modifier.listContainerTransform(anchor, groupContainerKey(card.group.id)) else Modifier.containerTransform(groupContainerKey(card.group.id)),
            )
            .testTag(SplitSmartTestTags.GROUP_ITEM)
            .semantics {
                contentDescription = description
                if (onArchive != null) customActions = listOf(CustomAccessibilityAction(archiveLabel) { onArchive(); true })
            },
        onClick = onClick,
        onClickLabel = stringResource(R.string.group_card_open),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GroupTile(card.group.name)
            Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(name, style = type.title.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    stringResource(R.string.group_card_meta, members, bills, relativeDate(card.lastActivity)),
                    style = type.label.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (card.memberNames.isNotEmpty()) AvatarStack(card.memberNames, size = Sizes.avatarSmall)
            Spacer(Modifier.width(Spacing.xs))
            Box(Modifier.weight(1f).clearAndSetSemantics {}, contentAlignment = Alignment.CenterEnd) { StatusPill(kind, balance, animate = false) }
        }
    }
}

/**
 * Drag the card sideways to archive it. It follows the finger and leans a little; past 40 % of the width an archive mark is fully
 * shown and a tick confirms the point of no return (and again if you pull back). Release past it, or fling, and the card flies
 * off and [onArchive] runs; otherwise it springs home on the Bouncy spring. TalkBack users get the same thing as an action on
 * the card. If the archive does not take (the card is still here a beat later), it comes back.
 */
@Composable
fun SwipeToArchive(onArchive: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnArchive by rememberUpdatedState(onArchive)
    var width by remember { mutableFloatStateOf(0f) }
    // A plain state written straight from the finger; animations and drags take turns through the mutex (as in the sheet).
    var offset by remember { mutableFloatStateOf(0f) }
    var gone by remember { mutableStateOf(false) }
    val mutex = remember { MutatorMutex() }
    val flingVelocity = SwipeToArchiveLogic.FLING_DP_PER_SECOND * density.density

    suspend fun animateTo(target: Float, token: MotionToken, velocity: Float = 0f) {
        mutex.mutate { animate(offset, target, velocity, motion.spec(token)) { value, _ -> offset = value } }
    }

    LaunchedEffect(gone) {
        if (!gone) return@LaunchedEffect
        // Still composed a beat after archiving: the write failed, so the card comes home.
        delay(MotionDurations.ScreenSlow.toLong())
        gone = false
        animateTo(0f, MotionToken.Bounce)
    }

    val dragState = rememberDraggableState { delta ->
        val next = SwipeToArchiveLogic.clamp(offset + delta, width)
        if (SwipeToArchiveLogic.crossed(offset, next, width)) haptics.tick()
        offset = next
    }
    val archiveColors = SplitSmartTheme.colors
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        ArchiveMark(Modifier.matchParentSize(), progress = { SwipeToArchiveLogic.reveal(offset, width) }, fromStart = { offset > 0f }, colors = archiveColors)
        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset
                    rotationZ = if (width > 0f && !motion.reduced) offset / width * SwipeToArchiveLogic.TILT_DEGREES else 0f
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !gone,
                    onDragStarted = { mutex.mutate(MutatePriority.UserInput) {} },
                    onDragStopped = { velocity ->
                        when (SwipeToArchiveLogic.settle(offset, width, velocity, flingVelocity)) {
                            SwipeOutcome.Archive -> scope.launch {
                                gone = true
                                haptics.light()
                                animateTo(if (offset >= 0f) width else -width, MotionToken.ScreenExit, velocity)
                                currentOnArchive()
                            }
                            SwipeOutcome.Return -> animateTo(0f, MotionToken.Bounce, velocity)
                        }
                    },
                ),
        ) { content() }
    }
}

/** Behind a dragged card: a raised strip whose archive mark grows on the side being uncovered. Read only in draw and layer. */
@Composable
private fun ArchiveMark(modifier: Modifier, progress: () -> Float, fromStart: () -> Boolean, colors: MarigoldColors) {
    val type = SplitSmartTheme.type
    Box(
        modifier
            .padding(bottom = com.darshan.splitsmart.ui.designsystem.tokens.Depth.card)
            .drawBehind { if (progress() > 0f) drawRoundRect(colors.raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(Radii.medium.toPx()), alpha = progress()) }
            .clearAndSetSemantics {},
    ) {
        for (start in listOf(true, false)) {
            Row(
                Modifier
                    .align(if (start) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = Spacing.xl)
                    .graphicsLayer {
                        val shown = if (fromStart() == start) progress() else 0f
                        alpha = shown
                        scaleX = ARCHIVE_MARK_FROM + (1f - ARCHIVE_MARK_FROM) * shown
                        scaleY = scaleX
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                BasicText("📦", style = type.title.copy(color = colors.text))
                BasicText(stringResource(R.string.groups_archive), style = type.bodyStrong.copy(color = colors.text))
            }
        }
    }
}

private const val ARCHIVE_MARK_FROM = 0.6f

/** One action in the FAB menu. */
@Stable
class FabAction(val label: String, val emoji: String, val tag: String, val onClick: () -> Unit)

/**
 * The FAB and the menu it bursts into. The plus turns into a cross on the Bouncy spring while the actions pop out above it from
 * the button outwards ([FabMenuLogic]); a scrim dims the page and closes it on tap, as does back. Everything animated is read in
 * layer or draw lambdas. [actions] are listed nearest the button first.
 */
@Composable
fun GroupsFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, motion) { progress.animateTo(if (expanded) 1f else 0f, motion.spec(MotionToken.Bounce)) }
    val showing by remember { derivedStateOf { progress.value > 0f || progress.targetValue > 0f } }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    // One action per opening: items stay on screen while the menu closes, and a tap on a fading item must do nothing.
    val gate = remember { booleanArrayOf(expanded) }
    androidx.compose.runtime.SideEffect { gate[0] = expanded }
    val closeLabel = stringResource(R.string.groups_fab_close)
    BackHandler(enabled = expanded) { currentOnExpandedChange(false) }
    Box(modifier.fillMaxSize()) {
        if (showing || expanded) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .testTag(GroupsTags.MENU_SCRIM)
                    .drawBehind { drawRect(colors.scrim, alpha = progress.value.coerceIn(0f, 1f)) }
                    .pointerInput(Unit) { detectTapGestures { currentOnExpandedChange(false) } }
                    .clearAndSetSemantics {
                        onClick(closeLabel) {
                            currentOnExpandedChange(false)
                            true
                        }
                    },
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(contentPadding).padding(end = Spacing.screen, bottom = Spacing.md),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (showing || expanded) {
                for (index in actions.indices.reversed()) {
                    val action = actions[index]
                    ChunkyButton(
                        action.label,
                        onClick = {
                            if (gate[0]) {
                                gate[0] = false
                                currentOnExpandedChange(false)
                                action.onClick()
                            }
                        },
                        modifier = Modifier
                            .testTag(action.tag)
                            .graphicsLayer {
                                val p = FabMenuLogic.itemProgress(progress.value, index, actions.size)
                                alpha = p.coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(1f, 1f)
                                if (!motion.reduced) {
                                    val scale = MENU_ITEM_FROM + (1f - MENU_ITEM_FROM) * p
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = (1f - p) * Spacing.md.toPx()
                                }
                            },
                        style = ButtonStyle.Secondary,
                        size = ButtonSize.Compact,
                        emoji = action.emoji,
                    )
                }
            }
            val openLabel = stringResource(R.string.groups_fab_open)
            val state = stringResource(if (expanded) R.string.groups_fab_state_open else R.string.groups_fab_state_closed)
            ChunkyIconButton(
                Icons.Filled.Add,
                contentDescription = if (expanded) closeLabel else openLabel,
                onClick = {
                    gate[0] = !expanded
                    currentOnExpandedChange(!expanded)
                },
                modifier = Modifier.testTag(SplitSmartTestTags.FAB_ADD_GROUP).semantics { stateDescription = state },
                style = ButtonStyle.Primary,
                size = IconButtonSize.Fab,
                iconModifier = Modifier.graphicsLayer { rotationZ = FAB_TURN_DEGREES * progress.value },
            )
        }
    }
}

private const val MENU_ITEM_FROM = 0.6f
private const val FAB_TURN_DEGREES = 45f
