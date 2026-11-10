package com.darshan.splitsmart.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.LoadErrorState
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonSize
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonLine
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squash
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionEasing
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

object ReviewTags {
    const val READING = "review_reading"
    const val ROW_SUMMARY = "review_row_summary"
    const val ROW_AMOUNT = "review_row_amount"
    const val EDITOR = "review_row_editor"
    const val WARNINGS = "review_warnings"
    const val LIST = "review_list"
}

@Composable
fun ReviewRoute(viewModel: ReviewViewModel = hiltViewModel(), onBack: () -> Unit, onSaved: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.error_unknown)
    val defaultTitle = stringResource(R.string.review_default_title)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.Saved -> onSaved(event.billId)
                ReviewEvent.Failed -> scope.launch { snackbar.show(failedText, emoji = "😕") }
            }
        }
    }
    ReviewScreen(
        state = state,
        snackbar = snackbar,
        callbacks = ReviewCallbacks(
            onBack = onBack, onRetry = viewModel::retry, onRerun = viewModel::rerunExtraction, onSave = { viewModel.save(defaultTitle) },
            onTitle = viewModel::onTitleChange, onPayer = viewModel::onPayerChange, onItemName = viewModel::onItemNameChange,
            onItemQty = viewModel::onItemQtyChange, onItemAmount = viewModel::onItemAmountChange, onAddItem = viewModel::addItem,
            onRemoveItem = viewModel::removeItem, onTax = viewModel::onTaxChange, onTip = viewModel::onTipChange, onTotal = viewModel::onTotalChange,
            onUseComputedTotal = viewModel::useComputedTotal, onApplyAi = viewModel::applyAiResult, onDismissAi = viewModel::dismissAiResult,
            onRevealShown = viewModel::revealShown,
        ),
    )
}

data class ReviewCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRerun: () -> Unit,
    val onSave: () -> Unit,
    val onTitle: (String) -> Unit,
    val onPayer: (String?) -> Unit,
    val onItemName: (String, String) -> Unit,
    val onItemQty: (String, String) -> Unit,
    val onItemAmount: (String, String) -> Unit,
    val onAddItem: () -> Unit,
    val onRemoveItem: (String) -> Unit,
    val onTax: (String) -> Unit,
    val onTip: (String) -> Unit,
    val onTotal: (String) -> Unit,
    val onUseComputedTotal: () -> Unit,
    val onApplyAi: () -> Unit = {},
    val onDismissAi: () -> Unit = {},
    val onRevealShown: (Long) -> Unit = {},
)

/**
 * The receipt review on Marigold. While the bill is read, skeleton lines shimmer under an honest note (AI first, the built-in
 * parser if it can't answer). When a reading arrives its lines resolve one by one: each skeleton wipes into the real text with a
 * marigold caret, in draw only. A pill and a sentence say who read the bill (the AI or the built-in parser), and why the AI was
 * not used. Every line is a compact row; a tap opens it in place for editing. Totals check themselves against the printed total.
 * One primary action: Save bill.
 */
@Composable
fun ReviewScreen(state: ReviewUiState, snackbar: SsSnackbarState, callbacks: ReviewCallbacks) {
    val colors = SplitSmartTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            SsTopBar(title = stringResource(R.string.review_title), onBack = callbacks.onBack, backLabel = stringResource(R.string.action_back))
            val form = state.form
            Box(Modifier.weight(1f)) {
                when {
                    state.phase == ReviewPhase.ERROR ->
                        LoadErrorState(stringResource(R.string.review_error_title), state.error ?: ErrorReason.UNKNOWN, callbacks.onRetry, callbacks.onBack)
                    form == null || state.phase == ReviewPhase.LOADING || state.phase == ReviewPhase.EXTRACTING -> ReadingList()
                    else -> ReviewList(state, form, callbacks)
                }
            }
            if (form != null && (state.phase == ReviewPhase.EDITING || state.phase == ReviewPhase.SAVING)) {
                ChunkyButton(
                    stringResource(R.string.review_save),
                    callbacks.onSave,
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                        .testTag(SplitSmartTestTags.REVIEW_SAVE),
                    loading = state.phase == ReviewPhase.SAVING,
                )
            }
        }
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** The bill being read: an honest note and skeleton lines in the shape of the rows to come. TalkBack hears one progress node. */
@Composable
private fun ReadingList() {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val label = stringResource(R.string.review_reading_items)
    LazyColumn(
        Modifier.fillMaxSize().testTag(ReviewTags.READING),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "head", contentType = "head") {
            ChunkyCard(color = colors.info.container) {
                StatusPill(StatusKind.Refining, stringResource(R.string.review_reading_title))
                Spacer(Modifier.height(Spacing.xs))
                BasicText(stringResource(R.string.review_reading_ai), style = type.body.copy(color = colors.info.content))
            }
        }
        item(key = "lines", contentType = "lines") {
            SkeletonGroup {
                Column(
                    Modifier.clearAndSetSemantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    repeat(SKELETON_ROWS) { index ->
                        ChunkyCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkeletonLine(Modifier.weight(1f), widthFraction = SKELETON_WIDTHS[index % SKELETON_WIDTHS.size], height = Sizes.skeletonTitle)
                                Spacer(Modifier.width(Spacing.xl))
                                Box(Modifier.width(Sizes.avatarHero).height(Sizes.skeletonTitle)) { SkeletonBlock(Modifier.fillMaxSize(), Radii.pillShape) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 6
private val SKELETON_WIDTHS = floatArrayOf(0.7f, 0.5f, 0.82f, 0.6f, 0.45f, 0.66f)

@Composable
private fun ReviewList(state: ReviewUiState, form: ReviewForm, callbacks: ReviewCallbacks) {
    val motion = LocalMotion.current
    val listState = rememberLazyListState()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val elapsed = rememberReveal(state.reveal, callbacks.onRevealShown)
    val order = remember(state.reveal) { state.reveal?.keys?.withIndex()?.associate { it.value to it.index }.orEmpty() }
    val notices = noticesOf(state, form)
    // Items above the first row: the notices, the title, the payer chips when there are members, the items header.
    val headerCount = notices.size + 2 + (if (state.members.isNotEmpty()) 1 else 0)
    val rowKeys = form.items.map { it.key }

    // A line just added opens for typing; a save with errors opens the first line that needs a fix and brings it into view.
    val lastKeys = remember { arrayOfNulls<List<String>>(1) }
    LaunchedEffect(rowKeys) {
        val before = lastKeys[0]
        lastKeys[0] = rowKeys
        val added = rowKeys.lastOrNull()
        if (before != null && rowKeys.size == before.size + 1 && added != null && added !in before) {
            expanded = added
            listState.scrollTo(headerCount + rowKeys.size - 1, motion.reduced)
        }
    }
    // An opened row keeps its fields and buttons on screen as it grows, instead of opening under the Save button.
    LaunchedEffect(expanded) { expanded?.let { listState.followOpenedRow(it) } }
    LaunchedEffect(state.errors) {
        val firstBad = form.items.firstOrNull { state.errors.items.containsKey(it.key) } ?: return@LaunchedEffect
        if (expanded != firstBad.key) expanded = firstBad.key
        listState.scrollTo(headerCount + form.items.indexOf(firstBad), motion.reduced)
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag(ReviewTags.LIST),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        for (notice in notices) {
            item(key = notice.name, contentType = "notice") {
                Box(Modifier.animateItem(fadeInSpec = motion.spec(MotionToken.Small), placementSpec = motion.spec(MotionToken.Settle), fadeOutSpec = motion.spec(MotionToken.Micro))) {
                    when (notice) {
                        Notice.Source -> SourceCard(form, state.refining, callbacks.onRerun)
                        Notice.Offer -> state.pendingAi?.let { AiOfferCard(it, callbacks) }
                        Notice.Warnings -> WarningsCard(form.warnings)
                    }
                }
            }
        }
        item(key = "title", contentType = "title") {
            SsTextField(
                value = form.title,
                onValueChange = callbacks.onTitle,
                label = stringResource(R.string.review_title_label),
                placeholder = stringResource(R.string.review_default_title),
                error = state.errors.title?.label(),
                emoji = "🧾",
                modifier = Modifier.testTag(SplitSmartTestTags.REVIEW_TITLE),
            )
        }
        if (state.members.isNotEmpty()) {
            item(key = "payer", contentType = "payer") { PayerChips(state.members, form.payerId, callbacks.onPayer) }
        }
        item(key = "items_header", contentType = "header") {
            ItemsHeader(form.items.size, callbacks.onAddItem)
        }
        items(form.items, key = { it.key }, contentType = { "item" }) { row ->
            val revealOrder = order[row.key]
            ItemRowCard(
                row = row,
                errors = state.errors.items[row.key].orEmpty(),
                expanded = expanded == row.key,
                onToggle = { expanded = if (expanded == row.key) null else row.key },
                progress = if (revealOrder == null) Done else ({ ItemRevealLogic.progress(revealOrder, elapsed.longValue) }),
                callbacks = callbacks,
                modifier = Modifier.animateItem(fadeInSpec = motion.spec(MotionToken.Small), placementSpec = motion.spec(MotionToken.Settle), fadeOutSpec = motion.spec(MotionToken.Micro)),
            )
        }
        item(key = "totals", contentType = "totals") { TotalsCard(state, form, callbacks) }
        item(key = "end", contentType = "end") { Spacer(Modifier.height(Spacing.xl)) }
    }
}

/** The cards above the rows, in this order: who read it (and why not the AI), a late AI answer, repairs. Only the ones that apply are in the list. */
private enum class Notice { Source, Offer, Warnings }

private fun noticesOf(state: ReviewUiState, form: ReviewForm): List<Notice> = buildList {
    add(Notice.Source)
    if (state.pendingAi != null) add(Notice.Offer)
    if (form.warnings.isNotEmpty()) add(Notice.Warnings)
}

@Composable
private fun ItemsHeader(count: Int, onAdd: () -> Unit) {
    // The same section heading as every other screen (emoji, gap, title, action); the list already keeps the gutter.
    SsSectionHeader(
        pluralStringResource(R.plurals.review_items_header, count, count),
        Modifier.padding(top = Spacing.xs),
        emoji = "🧾",
        actionLabel = stringResource(R.string.review_add_item),
        onAction = onAdd,
        actionModifier = Modifier.testTag(SplitSmartTestTags.REVIEW_ADD_ITEM),
        gutter = false,
    )
}

/**
 * Each frame while row [key] grows, scrolls by just enough to keep its bottom in view (never pushing its top out of view), and
 * stops once its height has held still for a few frames. Scrolling by pixels moves the list in layout only.
 */
private suspend fun LazyListState.followOpenedRow(key: String) {
    var lastSize = -1
    var still = 0
    repeat(FOLLOW_MAX_FRAMES) {
        withFrameMillis { }
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val overflow = item.offset + item.size - info.viewportEndOffset
        val room = item.offset - info.viewportStartOffset
        if (overflow > 0 && room > 0) scrollBy(minOf(overflow, room).toFloat())
        still = if (item.size == lastSize) still + 1 else 0
        lastSize = item.size
        if (still >= FOLLOW_STILL_FRAMES) return
    }
}

private const val FOLLOW_MAX_FRAMES = 90
private const val FOLLOW_STILL_FRAMES = 3

private suspend fun LazyListState.scrollTo(index: Int, reduced: Boolean) {
    if (reduced) scrollToItem(index) else animateScrollToItem(index)
}

private val Done: () -> Float = { 1f }

/**
 * Plays a reveal once: a frame clock from 0 to the reveal's length, then "done". Under reduced motion the lines are simply there.
 * The view model is told when it is over, so a later screen never replays it.
 */
@Composable
private fun rememberReveal(reveal: ItemReveal?, onShown: (Long) -> Unit): LongState {
    val motion = LocalMotion.current
    val elapsed = remember { mutableLongStateOf(Long.MAX_VALUE) }
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(reveal?.id) {
        val current = reveal ?: return@LaunchedEffect
        if (motion.reduced || current.keys.isEmpty()) {
            elapsed.longValue = Long.MAX_VALUE
            shown(current.id)
            return@LaunchedEffect
        }
        val total = ItemRevealLogic.durationMs(current.keys.size)
        elapsed.longValue = 0L
        val start = withFrameMillis { it }
        while (elapsed.longValue < total) {
            withFrameMillis { now -> elapsed.longValue = now - start }
        }
        elapsed.longValue = Long.MAX_VALUE
        shown(current.id)
    }
    return elapsed
}

/**
 * Who read the bill, in a pill and a sentence. The AI's reading sits on a plain card with a plum pill; while the AI refines the
 * pill breathes and the card says edits are safe. A parser reading says why the AI was not used: plainly when the build has no
 * key (a normal state), in the warning tone with one retry when trying again can help, in the danger tone when the key was
 * rejected or the pins did not match ([ReviewLogic.sourceTone]).
 */
@Composable
private fun SourceCard(form: ReviewForm, refining: Boolean, onRerun: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val parser = form.source == ExtractionSource.REGEX && !refining
    val failure = form.llmFailure
    val tone = when (ReviewLogic.sourceTone(form.source, refining, failure)) {
        SourceTone.Plain -> null
        SourceTone.Warning -> colors.pending
        SourceTone.Danger -> colors.failed
    }
    val (kind, label) = when {
        refining -> StatusKind.Refining to stringResource(R.string.review_source_draft)
        parser -> StatusKind.Neutral to stringResource(R.string.review_source_regex)
        else -> StatusKind.Ai to stringResource(R.string.review_source_llm)
    }
    val detail = when {
        refining -> stringResource(R.string.review_refining)
        parser -> fallbackMessage(failure, form.retryAfterSeconds)
        else -> stringResource(R.string.review_source_detail_llm)
    }
    val tag = when {
        refining -> SplitSmartTestTags.REFINING_BANNER
        parser -> SplitSmartTestTags.FALLBACK_BANNER
        else -> SplitSmartTestTags.REVIEW_SOURCE
    }
    ChunkyCard(Modifier.fillMaxWidth().testTag(tag), color = tone?.container ?: colors.surface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatusPill(kind, label, Modifier.testTag(SplitSmartTestTags.REVIEW_SOURCE + "_pill"))
            BasicText(stringResource(R.string.review_edit_hint), style = type.label.copy(color = tone?.content ?: colors.textSecondary))
        }
        Spacer(Modifier.height(Spacing.xs))
        BasicText(detail, style = type.body.copy(color = tone?.content ?: colors.text))
        if (parser && failure != LlmFailure.KEY_MISSING && failure != LlmFailure.UNAUTHORIZED) {
            Spacer(Modifier.height(Spacing.sm))
            ChunkyButton(
                stringResource(R.string.review_rerun),
                onRerun,
                Modifier.testTag(SplitSmartTestTags.REVIEW_RERUN),
                style = ButtonStyle.Secondary,
                size = ButtonSize.Compact,
                emoji = "✨",
            )
        }
    }
}

/** The AI finished after the user started editing: offered, never forced. */
@Composable
private fun AiOfferCard(pending: ReviewForm, callbacks: ReviewCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val total = Money.parseInrToPaise(pending.total)?.let { AmountFormat.format(it) } ?: pending.total
    val items = pluralStringResource(R.plurals.item_count, pending.items.size, pending.items.size)
    ChunkyCard(Modifier.fillMaxWidth().testTag(SplitSmartTestTags.AI_READY_CARD), color = colors.info.container) {
        StatusPill(StatusKind.Ai, stringResource(R.string.review_ai_offer_title))
        Spacer(Modifier.height(Spacing.xs))
        BasicText(stringResource(R.string.review_ai_ready, items, total), style = type.body.copy(color = colors.info.content))
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ChunkyButton(
                stringResource(R.string.review_ai_apply),
                callbacks.onApplyAi,
                Modifier.testTag(SplitSmartTestTags.AI_APPLY),
                style = ButtonStyle.Secondary,
                size = ButtonSize.Compact,
                emoji = "✨",
            )
            TextAction(stringResource(R.string.review_ai_keep), callbacks.onDismissAi)
        }
    }
}

/** What the reading repaired, one plain sentence each, so the user knows what to double-check. */
@Composable
private fun WarningsCard(warnings: Set<ExtractionWarning>) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(Modifier.fillMaxWidth().testTag(ReviewTags.WARNINGS), color = colors.pending.container) {
        BasicText("🔎 " + stringResource(R.string.review_warnings), Modifier.semantics { heading() }, style = type.bodyStrong.copy(color = colors.pending.content))
        for (warning in ExtractionWarning.entries.filter { it in warnings }) {
            Spacer(Modifier.height(Spacing.xxs))
            BasicText("• " + stringResource(warning.messageRes()), style = type.body.copy(color = colors.pending.content))
        }
    }
}

private fun ExtractionWarning.messageRes(): Int = when (this) {
    ExtractionWarning.NEGATIVE_DROPPED -> R.string.review_warning_negative
    ExtractionWarning.ABSURD_DROPPED -> R.string.review_warning_absurd
    ExtractionWarning.ITEM_MATH_FIXED -> R.string.review_warning_item_math
    ExtractionWarning.NAME_FILLED -> R.string.review_warning_name
    ExtractionWarning.QTY_FIXED -> R.string.review_warning_qty
    ExtractionWarning.ITEMS_CAPPED -> R.string.review_warning_capped
    ExtractionWarning.TOTAL_DERIVED -> R.string.review_warning_total
    ExtractionWarning.SUM_MISMATCH -> R.string.review_warning_sum
}

@Composable
private fun PayerChips(members: List<Member>, payerId: String?, onPayer: (String?) -> Unit) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BasicText(stringResource(R.string.review_paid_by), Modifier.semantics { heading() }, style = type.labelStrong.copy(color = colors.textSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (member in members) {
                EmojiChip(
                    label = DisplayText.safe(member.name),
                    selected = payerId == member.id,
                    onClick = { onPayer(if (payerId == member.id) null else member.id) },
                    exclusive = true,
                )
            }
        }
    }
}

/**
 * One line of the bill. Collapsed it reads like the receipt: name, quantity, amount. A tap opens the editor in place (the card
 * grows on the Settle spring); Done or a tap on the summary closes it. While a reading resolves, [progress] (read in draw only)
 * wipes the skeleton into the text with a marigold caret.
 */
@Composable
private fun ItemRowCard(
    row: ItemRow,
    errors: Set<ItemError>,
    expanded: Boolean,
    onToggle: () -> Unit,
    progress: () -> Float,
    callbacks: ReviewCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val paise = Money.parseInrToPaise(row.amount)
    val name = DisplayText.safe(row.name).ifBlank { stringResource(R.string.review_item_no_name) }
    val qty = row.qty.trim().ifBlank { "1" }
    val amountSpoken = paise?.let { AmountFormat.spoken(it) } ?: stringResource(R.string.review_row_needs_fix)
    val description = stringResource(R.string.review_row_description, name, qty, amountSpoken)
    val press = rememberPressFeedback()
    val guardToggle by rememberUpdatedState(onToggle)
    val needsFix = errors.isNotEmpty() || paise == null
    val skeleton = colors.skeleton
    val caret = colors.primary
    val caretWidth = Strokes.focus
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.REVIEW_ITEM), contentPadding = PaddingValues(Spacing.xxs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRowCompact)
                .trackPress(press)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.review_row_done else R.string.review_row_edit)) { guardToggle() }
                .squash(press, Squash.Subtle)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag(ReviewTags.ROW_SUMMARY)
                .drawWithContent {
                    val p = progress()
                    if (p >= 1f) {
                        drawContent()
                        return@drawWithContent
                    }
                    val eased = MotionEasing.Emphasized.curve.transform(p)
                    val edge = size.width * eased
                    val barHeight = Sizes.skeletonTitle.toPx()
                    val top = (size.height - barHeight) / 2f
                    val corner = CornerRadius(barHeight / 2f)
                    // What has not typed in yet is still a skeleton; what has is the real line, up to a marigold caret.
                    clipRect(right = edge) { this@drawWithContent.drawContent() }
                    clipRect(left = edge) {
                        drawRoundRect(skeleton, Offset(Spacing.sm.toPx(), top), Size(size.width * SKELETON_NAME, barHeight), corner)
                        drawRoundRect(skeleton, Offset(size.width * SKELETON_AMOUNT_START, top), Size(size.width * SKELETON_AMOUNT, barHeight), corner)
                    }
                    if (p > 0f) drawLine(caret, Offset(edge, top), Offset(edge, top + barHeight), caretWidth.toPx(), StrokeCap.Round)
                }
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    name,
                    style = type.bodyStrong.copy(color = if (row.name.isBlank()) colors.textSecondary else colors.text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    if (qty != "1") add(stringResource(R.string.review_item_qty_short, qty))
                    if (needsFix) add(stringResource(R.string.review_row_needs_fix))
                }
                if (meta.isNotEmpty()) {
                    BasicText(meta.joinToString(" · "), style = type.label.copy(color = if (needsFix) colors.failed.content else colors.textSecondary))
                }
            }
            // The row's sentence already speaks the amount: the figure itself stays silent, or TalkBack would read it twice.
            Box(Modifier.testTag(ReviewTags.ROW_AMOUNT).clearAndSetSemantics { }) {
                if (paise != null) AmountText(paise) else BasicText(row.amount.ifBlank { "₹ —" }, style = type.amount.copy(color = colors.failed.content), maxLines = 1)
            }
        }
        // Opens on the Settle spring (something arriving), folds away on Snap (out of the way, fast).
        AnimatedVisibility(
            expanded,
            enter = fadeIn(motion.spec(MotionToken.Small)) + expandVertically(motion.spec(MotionToken.Settle)),
            exit = fadeOut(motion.spec(MotionToken.Micro)) + shrinkVertically(motion.spec(MotionToken.Snap)),
        ) {
            ItemEditor(row, errors, callbacks, onDone = onToggle)
        }
    }
}

private const val SKELETON_NAME = 0.5f
private const val SKELETON_AMOUNT_START = 0.72f
private const val SKELETON_AMOUNT = 0.22f

@Composable
private fun ItemEditor(row: ItemRow, errors: Set<ItemError>, callbacks: ReviewCallbacks, onDone: () -> Unit) {
    val removeLabel = stringResource(R.string.review_remove_item, DisplayText.safe(row.name))
    Column(
        Modifier.fillMaxWidth().padding(Spacing.sm).testTag(ReviewTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SsTextField(
            value = row.name,
            onValueChange = { callbacks.onItemName(row.key, it) },
            label = stringResource(R.string.review_item_name),
            error = if (ItemError.NAME in errors) stringResource(R.string.error_name_blank) else null,
            modifier = Modifier.testTag(SplitSmartTestTags.REVIEW_ITEM_NAME),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SsTextField(
                value = row.qty,
                onValueChange = { callbacks.onItemQty(row.key, it) },
                label = stringResource(R.string.review_item_qty),
                error = if (ItemError.QTY in errors) stringResource(R.string.review_qty_invalid) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(QTY_WEIGHT).testTag(SplitSmartTestTags.REVIEW_ITEM_QTY),
            )
            SsTextField(
                value = row.amount,
                onValueChange = { callbacks.onItemAmount(row.key, it) },
                label = stringResource(R.string.review_item_amount),
                error = if (ItemError.AMOUNT in errors) stringResource(R.string.error_amount_invalid) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.REVIEW_ITEM_AMOUNT),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ChunkyButton(
                stringResource(R.string.review_row_remove),
                { callbacks.onRemoveItem(row.key) },
                Modifier.semantics { contentDescription = removeLabel },
                style = ButtonStyle.Ghost,
                size = ButtonSize.Compact,
            )
            ChunkyButton(stringResource(R.string.review_row_done), onDone, style = ButtonStyle.Secondary, size = ButtonSize.Compact)
        }
    }
}

private const val QTY_WEIGHT = 0.5f

/** Items, tax, tip and the printed total, with a live check of whether they agree and a one-tap fix when they don't. */
@Composable
private fun TotalsCard(state: ReviewUiState, form: ReviewForm, callbacks: ReviewCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    ChunkyCard(Modifier.fillMaxWidth()) {
        BasicText(stringResource(R.string.review_totals), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.review_items_subtotal), Modifier.weight(1f), style = type.body.copy(color = colors.textSecondary))
            AmountText(state.subtotalPaise ?: 0L)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountField(form.tax, callbacks.onTax, stringResource(R.string.review_tax), state.errors.tax, Modifier.weight(1f).testTag(SplitSmartTestTags.REVIEW_TAX))
            AmountField(form.tip, callbacks.onTip, stringResource(R.string.review_tip), state.errors.tip, Modifier.weight(1f).testTag(SplitSmartTestTags.REVIEW_TIP))
        }
        Spacer(Modifier.height(Spacing.sm))
        AmountField(form.total, callbacks.onTotal, stringResource(R.string.review_total), state.errors.total, Modifier.testTag(SplitSmartTestTags.REVIEW_TOTAL))
        Spacer(Modifier.height(Spacing.sm))
        AnimatedContent(ReviewLogic.mismatch(state.mismatchPaise), transitionSpec = { motion.fadeThrough() }, label = "mismatch") { mismatch ->
            when (mismatch) {
                Mismatch.Unknown -> Spacer(Modifier.height(Spacing.xxs))
                Mismatch.AddsUp -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatusPill(StatusKind.Settled, stringResource(R.string.review_adds_up))
                }
                is Mismatch.Over, is Mismatch.Under -> {
                    val (res, paise) = if (mismatch is Mismatch.Over) R.string.review_mismatch_over to mismatch.paise else R.string.review_mismatch_under to (mismatch as Mismatch.Under).paise
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.pending.container, Radii.smallShape)
                            .padding(Spacing.sm)
                            .testTag(SplitSmartTestTags.MISMATCH_BANNER),
                    ) {
                        BasicText(stringResource(res, AmountFormat.format(paise)), style = type.body.copy(color = colors.pending.content))
                        TextAction(stringResource(R.string.review_use_computed), callbacks.onUseComputedTotal, Modifier.testTag(SplitSmartTestTags.USE_COMPUTED_TOTAL))
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, modifier: Modifier) {
    SsTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        error = if (isError) stringResource(R.string.error_amount_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun fallbackMessage(failure: LlmFailure?, retryAfterSeconds: Long?): String = when (failure) {
    null -> stringResource(R.string.review_fallback_incomplete)
    LlmFailure.KEY_MISSING -> stringResource(R.string.review_fallback_key_missing)
    LlmFailure.UNAUTHORIZED -> stringResource(R.string.review_fallback_unauthorized)
    LlmFailure.RATE_LIMITED -> retryAfterSeconds?.let { stringResource(R.string.review_fallback_rate_limited_after, it.toInt()) }
        ?: stringResource(R.string.review_fallback_rate_limited)
    LlmFailure.TIMEOUT -> stringResource(R.string.review_fallback_timeout)
    LlmFailure.OFFLINE -> stringResource(R.string.review_fallback_offline)
    LlmFailure.INSECURE -> stringResource(R.string.review_fallback_insecure)
    else -> stringResource(R.string.review_fallback_unavailable, failure.label())
}

@Composable
private fun LlmFailure.label(): String = stringResource(
    when (this) {
        LlmFailure.KEY_MISSING -> R.string.llm_failure_key_missing
        LlmFailure.NETWORK -> R.string.llm_failure_network
        LlmFailure.TIMEOUT -> R.string.llm_failure_timeout
        LlmFailure.UNAUTHORIZED -> R.string.llm_failure_unauthorized
        LlmFailure.RATE_LIMITED -> R.string.llm_failure_rate_limited
        LlmFailure.CLIENT_ERROR -> R.string.llm_failure_client
        LlmFailure.SERVER_ERROR -> R.string.llm_failure_server
        LlmFailure.MALFORMED -> R.string.llm_failure_malformed
        LlmFailure.OFFLINE -> R.string.llm_failure_offline
        LlmFailure.INSECURE -> R.string.llm_failure_insecure
    },
)
