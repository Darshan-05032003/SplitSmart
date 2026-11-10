package com.darshan.splitsmart.ui.split

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.ConfettiBurst
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.effects.CollectUiEffects
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplitRoute(viewModel: SplitViewModel = hiltViewModel(), onBack: () -> Unit, onSaved: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.error_unknown)
    CollectUiEffects(viewModel.effects)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SplitEvent.Saved -> onSaved(event.billId)
                SplitEvent.Failed -> scope.launch { snackbar.show(failedText, emoji = "😕") }
            }
        }
    }
    SplitScreen(
        state = state,
        snackbar = snackbar,
        callbacks = SplitCallbacks(
            onBack = onBack, onRetry = viewModel::retry, onMode = viewModel::setModeOption, onToggleParticipant = viewModel::toggleParticipant,
            onToggleItem = viewModel::toggleItemAssignment, onShareText = viewModel::setShareText, onPayer = viewModel::setPayer, onSave = viewModel::save,
            onToggleSelect = viewModel::toggleSelected, onClearSelection = viewModel::clearSelection, onAssign = viewModel::assignItems,
            onAssignSelected = viewModel::assignSelected, onStepShare = viewModel::stepShare,
        ),
    )
}

data class SplitCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onMode: (SplitModeOption) -> Unit,
    val onToggleParticipant: (String) -> Unit,
    val onToggleItem: (String, String) -> Unit,
    val onShareText: (String, String) -> Unit,
    val onPayer: (String?) -> Unit,
    val onSave: () -> Unit,
    val onToggleSelect: (String) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onAssign: (Collection<String>, String) -> Unit = { _, _ -> },
    val onAssignSelected: (String) -> Unit = {},
    val onStepShare: (String, Int) -> Unit = { _, _ -> },
)

/**
 * Split a bill on Marigold. The donut on top is the split at a glance: each person's arc in their avatar colour, morphing when the
 * mode or an assignment changes. Four modes as radio chips; who paid and who is in as rows of faces. "By item" pins the faces to
 * the top while the receipt scrolls under them: drag a line onto a face (it gulps and its count bumps), or long-press lines and tap
 * a face. Every person's amount rolls; a check below says the shares add up to the bill to the paisa. One primary action: Save.
 * Every line getting an owner is the one celebration.
 */
@Composable
fun SplitScreen(state: SplitUiState, snackbar: SsSnackbarState, callbacks: SplitCallbacks) {
    val colors = SplitSmartTheme.colors
    val editor = (state.editor as? LoadState.Content)?.data
    val drag = remember { DragAssignState() }
    drag.slop = with(LocalDensity.current) { Spacing.xs.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .onGloballyPositioned { drag.overlayOrigin = it.positionInRoot() },
    ) {
        Column(Modifier.fillMaxSize()) {
            SsTopBar(
                title = stringResource(R.string.split_title),
                subtitle = editor?.bill?.title?.let { DisplayText.safe(it) },
                onBack = callbacks.onBack,
                backLabel = stringResource(R.string.action_back),
            )
            Box(Modifier.weight(1f)) {
                when (val e = state.editor) {
                    LoadState.Loading, LoadState.Empty -> SplitLoading()
                    is LoadState.Error -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ErrorState(
                            title = stringResource(R.string.split_title),
                            message = stringResource(e.reason.messageRes()),
                            onRetry = callbacks.onRetry,
                            retryLabel = stringResource(R.string.retry_label),
                            modifier = Modifier.testTag(SplitSmartTestTags.ERROR),
                        )
                    }
                    is LoadState.Content -> SplitList(e.data, drag, callbacks)
                }
            }
            if (editor != null) {
                ChunkyButton(
                    stringResource(R.string.split_save),
                    callbacks.onSave,
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                        .testTag(SplitSmartTestTags.SPLIT_SAVE),
                    enabled = editor.preview != null,
                    loading = state.saving,
                    emoji = "✅",
                )
            }
        }
        DragGhost(drag)
        if (editor != null) Celebration(editor.celebrate)
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Confetti when the last line gets an owner, once per moment: a rotation after the burst has played does not replay it. */
@Composable
private fun Celebration(celebrate: Int) {
    var shown by rememberSaveable { mutableIntStateOf(celebrate) }
    val key = celebrate.takeIf { it > shown }
    ConfettiBurst(key, Modifier.fillMaxSize())
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        delay(MotionDurations.Confetti.toLong())
        shown = key
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitList(editor: SplitEditor, drag: DragAssignState, callbacks: SplitCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val option = editor.option
    val byId = remember(editor.members) { editor.members.associateBy { it.id } }
    val participants = remember(editor.members, editor.config.participantIds) { editor.config.participantIds.mapNotNull { byId[it] } }
    val shares = remember(editor.preview) { editor.preview?.shares?.associateBy { it.memberId }.orEmpty() }
    val slices = remember(editor.preview) { DonutLogic.slices(editor.preview?.shares?.map { it.memberId to it.totalPaise }.orEmpty()) }
    val sliceColors = rememberSliceColors(slices)
    val totalShares = remember(editor.config) { editor.config.participantIds.sumOf { editor.config.shares[it] ?: 0 } }
    var openItem by rememberSaveable { mutableStateOf<String?>(null) }
    val selected by rememberUpdatedState(editor.selected)
    val itemNames = remember(editor.bill.items) { editor.bill.items.associate { it.id to DisplayText.safe(it.name) } }
    val resources = LocalResources.current
    LazyColumn(
        Modifier.fillMaxSize().testTag(SplitTags.LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "hero", contentType = "hero") {
            SplitHero(editor, participants, shares, slices, sliceColors, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs))
        }
        item(key = "modes", contentType = "section") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SsSectionHeader(stringResource(R.string.split_mode_label), emoji = "🍕")
                ModeSwitcher(option, callbacks.onMode)
            }
        }
        item(key = "payer", contentType = "section") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SsSectionHeader(stringResource(R.string.split_paid_by), emoji = "💳")
                if (editor.payerId == null) {
                    BasicText(stringResource(R.string.split_payer_hint), Modifier.padding(horizontal = Spacing.screen), style = type.label.copy(color = colors.textSecondary))
                }
                FaceChooser(editor.members, isChosen = { it == editor.payerId }, onToggle = { id -> callbacks.onPayer(if (editor.payerId == id) null else id) }, exclusive = true, tag = SplitSmartTestTags.SPLIT_PAYER_CHIP)
            }
        }
        item(key = "people", contentType = "section") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SsSectionHeader(stringResource(R.string.split_participants), emoji = "🙋")
                FaceChooser(editor.members, isChosen = { it in editor.config.participantIds }, onToggle = callbacks.onToggleParticipant, exclusive = false, tag = SplitSmartTestTags.SPLIT_PARTICIPANT)
            }
        }
        if (option == SplitModeOption.ByItem && editor.bill.items.isNotEmpty()) {
            stickyHeader(key = "dock", contentType = "dock") {
                Dock(editor, participants, drag, callbacks)
            }
            item(key = "itemsHeader", contentType = "section") { ItemsHeader(editor) }
            items(editor.bill.items, key = { "item:" + it.id }, contentType = { "item" }) { item ->
                val owners = editor.config.itemAssignments[item.id].orEmpty().let { ids -> participants.filter { it.id in ids } }
                ItemAssignRow(
                    item = item,
                    owners = owners,
                    participants = participants,
                    open = openItem == item.id,
                    picked = item.id in editor.selected,
                    drag = drag,
                    dragIds = { if (item.id in selected) editor.bill.items.map { it.id }.filter { it in selected } else listOf(item.id) },
                    dragLabel = {
                        val count = if (item.id in selected) selected.size else 1
                        if (count > 1) resources.getQuantityString(R.plurals.split_dragging, count, count) else itemNames[item.id].orEmpty()
                    },
                    onOpen = { if (selected.isNotEmpty()) callbacks.onToggleSelect(item.id) else openItem = if (openItem == item.id) null else item.id },
                    onPick = { callbacks.onToggleSelect(item.id) },
                    onToggleOwner = { member -> callbacks.onToggleItem(item.id, member) },
                    onDrop = { ids, member -> callbacks.onAssign(ids, member) },
                    modifier = Modifier.padding(horizontal = Spacing.screen).animateItem(),
                )
            }
        }
        item(key = "peopleHeader", contentType = "section") { SsSectionHeader(stringResource(R.string.split_preview_title), emoji = "💰") }
        items(participants, key = { "person:" + it.id }, contentType = { "person" }) { member ->
            PersonShareRow(
                member = member,
                share = shares[member.id],
                sliceColor = sliceColors[member.id],
                isPayer = member.id == editor.payerId,
                option = option,
                shareText = editor.shareTexts[member.id].orEmpty(),
                totalShares = totalShares,
                onShareText = { callbacks.onShareText(member.id, it) },
                onStep = { callbacks.onStepShare(member.id, it) },
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        item(key = "check", contentType = "check") { TotalCheck(editor, Modifier.padding(horizontal = Spacing.screen)) }
        item(key = "end", contentType = "end") { Spacer(Modifier.height(Spacing.md)) }
    }
}

/**
 * The split at a glance: the donut (the mode's emoji in its hole, popping when the mode changes) beside the total and a legend of
 * the first people with their rolling amounts. Past four people the legend says how many more; the rows below have everyone.
 */
@Composable
private fun SplitHero(
    editor: SplitEditor,
    participants: List<Member>,
    shares: Map<String, com.darshan.splitsmart.domain.model.MemberShare>,
    slices: List<DonutSlice>,
    sliceColors: SliceColors,
    modifier: Modifier,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val ways = pluralStringResource(R.plurals.split_ways, participants.size, participants.size)
    ChunkyCard(modifier.fillMaxWidth(), shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SplitDonut(slices, sliceColors, Modifier.testTag(SplitTags.DONUT).clearAndSetSemantics {}) {
                AnimatedContent(editor.option, transitionSpec = { ContentTransform(motion.popIn(), motion.popOut()) }, label = "modeEmoji") { option ->
                    BasicText(option.emoji, style = type.display.copy(color = colors.text))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    AmountText(editor.bill.totalPaise, style = type.title, color = colors.text)
                    BasicText(ways, style = type.label.copy(color = colors.textSecondary))
                }
                // The legend follows the ring: with a crowd, the biggest shares that have their own slice.
                val legend = remember(slices, participants) {
                    val byId = participants.associateBy { it.id }
                    slices.filter { it.key != DonutLogic.OTHERS }.mapNotNull { byId[it.key] }.take(LEGEND_ROWS).ifEmpty { participants.take(LEGEND_ROWS) }
                }
                legend.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ColorDot(sliceColors[member.id])
                        BasicText(DisplayText.safe(member.name), Modifier.weight(1f), style = type.label.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val paise = shares[member.id]?.totalPaise
                        if (paise != null) RollingAmount(paise, style = type.amountSmall, color = colors.text) else BasicText("—", style = type.amountSmall.copy(color = colors.textSecondary))
                    }
                }
                if (participants.size > legend.size) {
                    BasicText(stringResource(R.string.split_legend_more, participants.size - legend.size), style = type.label.copy(color = colors.textSecondary))
                }
            }
        }
    }
}

private const val LEGEND_ROWS = 4

@Composable
private fun Dock(editor: SplitEditor, participants: List<Member>, drag: DragAssignState, callbacks: SplitCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val picked = editor.selected.size
    Column(
        Modifier.fillMaxWidth().background(colors.bg).padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MemberDock(participants, editor.assignedCounts, picked, drag, onGivePicked = callbacks.onAssignSelected)
        if (picked > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    pluralStringResource(R.plurals.split_picked, picked, picked),
                    Modifier.weight(1f),
                    style = type.bodyStrong.copy(color = colors.text),
                )
                TextAction(stringResource(R.string.split_picked_clear), callbacks.onClearSelection, Modifier.testTag(SplitTags.CLEAR_PICKS))
            }
        } else {
            BasicText(stringResource(R.string.split_dock_hint), Modifier.padding(horizontal = Spacing.screen), style = type.label.copy(color = colors.textSecondary))
        }
    }
}

@Composable
private fun ItemsHeader(editor: SplitEditor) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val unassigned = SplitLogic.unassignedCount(editor.bill.items, editor.config)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        SsSectionHeader(stringResource(R.string.split_items_title), emoji = "🧾")
        Box(Modifier.padding(horizontal = Spacing.screen)) {
            if (editor.allAssigned) {
                StatusPill(StatusKind.Settled, stringResource(R.string.split_all_assigned), Modifier.testTag(SplitTags.ALL_ASSIGNED), animate = false)
            } else {
                BasicText(pluralStringResource(R.plurals.split_unassigned, unassigned, unassigned), style = type.label.copy(color = colors.textSecondary))
            }
        }
    }
}

/** The promise of the screen in one sentence: the shares add up to the bill to the paisa, or what is still wrong. */
@Composable
private fun TotalCheck(editor: SplitEditor, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val preview = editor.preview
    val left = editor.percentLeft
    val (tone, text) = when {
        editor.error != null -> colors.failed to stringResource(editor.error.messageRes())
        left != null && left > 0 -> colors.pending to stringResource(R.string.split_percent_left, left)
        left != null && left < 0 -> colors.failed to stringResource(R.string.split_percent_over, -left)
        preview != null && preview.totalPaise == editor.bill.totalPaise -> colors.success to stringResource(R.string.split_total_check_ok, AmountFormat.format(preview.totalPaise))
        preview != null -> colors.pending to stringResource(R.string.split_total_check_off, AmountFormat.format(preview.totalPaise), AmountFormat.format(editor.bill.totalPaise))
        else -> colors.neutral to ""
    }
    if (text.isEmpty()) return
    Row(
        modifier.fillMaxWidth().testTag(SplitTags.CHECK).background(tone.container, Radii.mediumShape).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BasicText(if (tone == colors.success) "✅" else "⚠️", Modifier.clearAndSetSemantics {}, style = type.title.copy(color = tone.content))
        BasicText(text, Modifier.weight(1f), style = type.bodyStrong.copy(color = tone.content))
    }
}

@Composable
private fun SplitLoading() {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen).testTag(SplitSmartTestTags.LOADING), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonGroup { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.donut), Radii.largeShape) }
        SkeletonList(count = LOADING_ROWS, label = stringResource(R.string.loading))
    }
}

private const val LOADING_ROWS = 3

fun SplitError.messageRes(): Int = when (this) {
    SplitError.NO_PARTICIPANTS -> R.string.split_error_no_participants
    SplitError.NO_SHARES -> R.string.split_error_no_shares
    SplitError.NEGATIVE_SHARE -> R.string.split_error_negative_share
    SplitError.DUPLICATE_PARTICIPANT -> R.string.split_error_duplicate
}
