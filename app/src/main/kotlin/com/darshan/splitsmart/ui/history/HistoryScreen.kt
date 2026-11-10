package com.darshan.splitsmart.ui.history

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.components.LoadErrorState
import com.darshan.splitsmart.ui.components.formatDate
import com.darshan.splitsmart.ui.components.formatShortDate
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmptyState
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.effects.CollectUiEffects
import com.darshan.splitsmart.ui.designsystem.modifiers.filterTouchesWhenObscured
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tags of the history screen. */
object HistoryTags {
    const val LIST = "history_list"
    const val INSIGHTS = "history_insights"
    const val PAYER_BAR = "history_payer_bar"
    const val CATEGORY_BAR = "history_category_bar"
    const val MONTH = "history_month"
    const val BILL_ROW = "history_bill"
    const val PAYMENT_ROW = "history_payment"
    const val PAYMENT_SHEET = "history_payment_sheet"
    const val REMOVE = "history_remove"
    const val KEEP = "history_keep"
}

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel(), onBack: () -> Unit, onOpenBill: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.history_removed)
    val failed = stringResource(R.string.error_unknown)
    CollectUiEffects(viewModel.effects)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Removed -> scope.launch { snackbar.show(String.format(removed, DisplayText.safe(event.fromName), DisplayText.safe(event.toName)), emoji = "🗑️") }
                HistoryEvent.Failed -> scope.launch { snackbar.show(failed, emoji = "😕") }
            }
        }
    }
    HistoryScreen(state, snackbar, HistoryCallbacks(onBack, onOpenBill, viewModel::select, viewModel::dismiss, viewModel::remove, viewModel::retry))
}

data class HistoryCallbacks(
    val onBack: () -> Unit,
    val onOpenBill: (String) -> Unit = {},
    val onSelect: (Settlement) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * A group's story on Marigold: the insights card (what was spent and settled, who paid most, top categories as bars that grow in),
 * then every bill and payment on a timeline rail, month by month under sticky headers. A bill opens; a payment opens a sheet where
 * a mistaken one can be removed.
 */
@Composable
fun HistoryScreen(state: HistoryUiState, snackbar: SsSnackbarState, callbacks: HistoryCallbacks) {
    val colors = SplitSmartTheme.colors
    val content = (state.content as? LoadState.Content)?.data
    val sheetOpen = state.selected != null
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().then(if (sheetOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
            SsTopBar(
                title = stringResource(R.string.history_title),
                subtitle = content?.group?.name?.let(DisplayText::safe),
                onBack = callbacks.onBack,
                backLabel = stringResource(R.string.action_back),
            )
            when (val c = state.content) {
                LoadState.Loading -> HistoryLoading()
                LoadState.Empty -> EmptyState(IllustrationKind.Bills, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
                is LoadState.Error -> LoadErrorState(stringResource(R.string.history_error_title), c.reason, callbacks.onRetry, callbacks.onBack)
                is LoadState.Content -> HistoryList(c.data, callbacks)
            }
        }
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        PaymentSheet(state.selected, state.removing, callbacks)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(content: HistoryContent, callbacks: HistoryCallbacks) {
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    LazyColumn(Modifier.fillMaxSize().testTag(HistoryTags.LIST)) {
        item(key = "insights", contentType = "insights") {
            InsightsCard(content.group.name, content.insights, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs).padding(bottom = Spacing.sm))
        }
        for (month in content.months) {
            stickyHeader(key = month.key, contentType = "month") { MonthHeader(month, monthFormat) }
            items(month.entries, key = { it.key }, contentType = { if (it is HistoryEntry.BillEntry) "bill" else "payment" }) { entry ->
                when (entry) {
                    is HistoryEntry.BillEntry -> BillEntryRow(entry, callbacks.onOpenBill)
                    is HistoryEntry.PaymentEntry -> PaymentEntryRow(entry, callbacks.onSelect)
                }
            }
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.navigationBarsPadding().height(Spacing.giant)) }
    }
}

@Composable
private fun MonthHeader(month: HistoryMonth, format: DateTimeFormatter) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
            .testTag(HistoryTags.MONTH)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        BasicText(format.format(month.month), style = type.title.copy(color = colors.text))
        BasicText(
            if (month.settledPaise > 0) {
                stringResource(R.string.history_month_meta, AmountFormat.format(month.spentPaise), AmountFormat.format(month.settledPaise))
            } else {
                stringResource(R.string.history_month_spent, AmountFormat.format(month.spentPaise))
            },
            style = type.label.copy(color = colors.textSecondary),
        )
    }
}

/** The timeline's rail runs behind each row's tile, row after row, so the entries read as one line through time. */
private fun Modifier.rail(color: Color): Modifier = drawBehind {
    val x = (Spacing.screen + Spacing.md + Sizes.avatar / 2).toPx()
    val width = Strokes.rail.toPx()
    drawRect(color, topLeft = Offset(x - width / 2, 0f), size = Size(width, size.height))
}

@Composable
private fun BillEntryRow(entry: HistoryEntry.BillEntry, onOpen: (String) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val bill = entry.bill
    val date = formatShortDate(bill.createdAt)
    SsListRow(
        bill.title,
        Modifier.rail(colors.hairline).padding(horizontal = Spacing.screen).testTag(HistoryTags.BILL_ROW),
        subtitle = entry.payerName?.let { stringResource(R.string.history_bill_meta, date, DisplayText.safe(it)) }
            ?: stringResource(R.string.history_bill_meta_no_payer, date),
        leading = { Tile(BillCategory.of(bill.title).emoji, colors.raised) },
        trailing = { AmountText(bill.totalPaise, style = type.amountSmall, color = colors.text) },
        onClick = { onOpen(bill.id) },
        onClickLabel = stringResource(R.string.bill_open),
        minHeight = Sizes.listRowCompact,
    )
}

@Composable
private fun PaymentEntryRow(entry: HistoryEntry.PaymentEntry, onSelect: (Settlement) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val payment = entry.settlement
    val date = formatShortDate(payment.createdAt)
    SsListRow(
        stringResource(R.string.history_payment_title, DisplayText.safe(payment.fromName), DisplayText.safe(payment.toName)),
        Modifier.rail(colors.hairline).padding(horizontal = Spacing.screen).testTag(HistoryTags.PAYMENT_ROW),
        subtitle = stringResource(if (payment.method == SettlementMethod.UPI_APP) R.string.history_payment_meta_upi else R.string.history_payment_meta_other, date),
        leading = { Tile("✅", colors.success.container) },
        trailing = { AmountText(payment.amountPaise, style = type.amountSmall, color = colors.success.content) },
        onClick = { onSelect(payment) },
        onClickLabel = stringResource(R.string.history_open_payment),
        minHeight = Sizes.listRowCompact,
    )
}

@Composable
private fun Tile(emoji: String, color: Color) {
    Box(Modifier.size(Sizes.avatar).background(color, Radii.circleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        BasicText(emoji, style = SplitSmartTheme.type.bodyStrong.copy(color = SplitSmartTheme.colors.text))
    }
}

/**
 * "Goa trip so far": what was spent and settled, then who paid most and the top categories as bars. The bars grow from zero one
 * after another on the Settle spring the first time the card shows; under reduced motion they are simply there. Drawn in draw.
 */
@Composable
fun InsightsCard(groupName: String, insights: Insights, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth().testTag(HistoryTags.INSIGHTS), contentPadding = PaddingValues(Spacing.md)) {
        BasicText(stringResource(R.string.insights_title, DisplayText.safe(groupName)), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Figure(stringResource(R.string.insights_spent), insights.spentPaise, pluralStringResource(R.plurals.insights_bills, insights.billCount, insights.billCount), colors.text, Modifier.weight(1f))
            Figure(stringResource(R.string.insights_settled), insights.settledPaise, pluralStringResource(R.plurals.insights_payments, insights.settledCount, insights.settledCount), colors.success.content, Modifier.weight(1f))
        }
        if (insights.topPayers.isNotEmpty()) {
            BarsHeader(stringResource(R.string.insights_payers), null)
            insights.topPayers.forEachIndexed { index, bar ->
                val name = DisplayText.safe(bar.label)
                Bar(bar, name, index, colors.primary, stringResource(R.string.insights_bar_payer, name, AmountFormat.spoken(bar.paise)), HistoryTags.PAYER_BAR)
            }
        }
        if (insights.topCategories.isNotEmpty()) {
            BarsHeader(stringResource(R.string.insights_categories), stringResource(R.string.insights_categories_hint))
            insights.topCategories.forEachIndexed { index, bar ->
                val label = categoryLabel(bar.key)
                Bar(bar, "${bar.emoji.orEmpty()} $label".trim(), insights.topPayers.size + index, colors.info.content, stringResource(R.string.insights_bar_category, label, AmountFormat.spoken(bar.paise)), HistoryTags.CATEGORY_BAR)
            }
        }
    }
}

@Composable
private fun Figure(label: String, paise: Long, meta: String, color: Color, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(modifier.background(colors.raised, Radii.smallShape).padding(Spacing.sm).semantics(mergeDescendants = true) {}) {
        BasicText(label, style = type.label.copy(color = colors.textSecondary))
        AmountText(paise, style = type.amount, color = color)
        BasicText(meta, style = type.label.copy(color = colors.textSecondary))
    }
}

@Composable
private fun BarsHeader(title: String, hint: String?) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(Modifier.padding(top = Spacing.md, bottom = Spacing.xxs).semantics(mergeDescendants = true) { heading() }) {
        BasicText(title, style = type.bodyStrong.copy(color = colors.text))
        if (hint != null) BasicText(hint, style = type.label.copy(color = colors.textSecondary))
    }
}

/** One labelled bar: name, a track the bar grows along, the amount. One TalkBack sentence ([spoken]). */
@Composable
private fun Bar(bar: InsightBar, label: String, index: Int, fill: Color, spoken: String, tag: String) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val progress = remember(bar.key) { Animatable(if (motion.reduced) bar.fraction else 0f) }
    LaunchedEffect(bar.key, bar.fraction, motion) {
        if (motion.reduced) {
            progress.snapTo(bar.fraction)
        } else {
            delay(motion.staggerDelayMs(index).toLong())
            progress.animateTo(bar.fraction, motion.spec(MotionToken.Settle))
        }
    }
    val track = colors.raised
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs).testTag(tag).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BasicText(label, Modifier.weight(BAR_LABEL_WEIGHT), style = type.label.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(
            Modifier
                .weight(BAR_TRACK_WEIGHT)
                .height(Sizes.insightBar)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(track, cornerRadius = radius)
                    val width = size.width * progress.value.coerceIn(0f, 1f)
                    if (width > 0f) drawRoundRect(fill, size = Size(width.coerceAtLeast(size.height), size.height), cornerRadius = radius)
                },
        )
        AmountText(bar.paise, Modifier.weight(BAR_AMOUNT_WEIGHT), style = type.amountSmall, color = colors.text)
    }
}

private const val BAR_LABEL_WEIGHT = 0.3f
private const val BAR_TRACK_WEIGHT = 0.4f
private const val BAR_AMOUNT_WEIGHT = 0.3f

@Composable
private fun categoryLabel(key: String): String = stringResource(
    when (key) {
        "food" -> R.string.category_food
        "travel" -> R.string.category_travel
        "stay" -> R.string.category_stay
        "groceries" -> R.string.category_groceries
        "fun" -> R.string.category_fun
        "home" -> R.string.category_home
        else -> R.string.category_other
    },
)

@Composable
private fun HistoryLoading() {
    val label = stringResource(R.string.history_loading)
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SkeletonGroup { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.heroSkeleton), Radii.mediumShape) }
        SkeletonList(6, label = label)
    }
}

/** A payment marked as made, with the way to remove one marked by mistake. The sheet is the confirmation; Remove acts once. */
@Composable
private fun PaymentSheet(selected: Settlement?, removing: Boolean, callbacks: HistoryCallbacks) {
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) shown = selected
    SsBottomSheet(visible = selected != null, onDismissRequest = callbacks.onDismiss, modifier = Modifier.testTag(HistoryTags.PAYMENT_SHEET), dismissible = !removing) {
        val payment = shown ?: return@SsBottomSheet
        val colors = SplitSmartTheme.colors
        val type = SplitSmartTheme.type
        val date = formatDate(payment.createdAt)
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BasicText(
                stringResource(R.string.history_payment_title, DisplayText.safe(payment.fromName), DisplayText.safe(payment.toName)),
                Modifier.semantics { heading() },
                style = type.title.copy(color = colors.text),
            )
            AmountText(payment.amountPaise, style = type.display, color = colors.success.content)
            BasicText(
                stringResource(if (payment.method == SettlementMethod.UPI_APP) R.string.history_sheet_meta_upi else R.string.history_sheet_meta_other, date),
                style = type.body.copy(color = colors.textSecondary),
            )
            BasicText(stringResource(R.string.history_sheet_note), style = type.label.copy(color = colors.textSecondary))
            BasicText(stringResource(R.string.history_remove_hint), Modifier.padding(top = Spacing.sm), style = type.label.copy(color = colors.textSecondary))
            ChunkyButton(
                stringResource(R.string.history_remove),
                callbacks.onRemove,
                Modifier.fillMaxWidth().filterTouchesWhenObscured().testTag(HistoryTags.REMOVE),
                style = ButtonStyle.Danger,
                loading = removing,
            )
            ChunkyButton(
                stringResource(R.string.history_keep),
                callbacks.onDismiss,
                Modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag(HistoryTags.KEEP),
                style = ButtonStyle.Ghost,
                enabled = !removing,
            )
        }
    }
}
