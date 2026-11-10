package com.darshan.splitsmart.ui.bill

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.formatDate
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.heavy
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.groups.HeroPalette
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.split.AdjustmentKind
import com.darshan.splitsmart.ui.split.ColorDot
import com.darshan.splitsmart.ui.split.DonutLogic
import com.darshan.splitsmart.ui.split.DonutSlice
import com.darshan.splitsmart.ui.split.SliceColors
import com.darshan.splitsmart.ui.split.SplitDonut
import com.darshan.splitsmart.ui.split.SplitModeOption
import com.darshan.splitsmart.ui.split.labelRes
import com.darshan.splitsmart.ui.split.rememberSliceColors
import com.darshan.splitsmart.ui.split.shareBreakdown
import com.darshan.splitsmart.ui.theme.LocalMarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

/** Test tags of the bill screen that are not shared with other screens. */
object BillDetailTags {
    const val LIST = "bill_list"
    const val HERO = "bill_hero"
    const val NOT_SPLIT = "bill_not_split"
    const val DELETE_SHEET = "bill_delete_sheet"
    const val DELETE_CONFIRM = "bill_delete_confirm"
    const val DELETE_KEEP = "bill_delete_keep"
}

@Composable
fun BillDetailRoute(viewModel: BillDetailViewModel = hiltViewModel(), onBack: () -> Unit, onSplit: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BillDetailEvent.Deleted -> onBack()
                BillDetailEvent.Failed -> scope.launch { snackbar.show(failedText, emoji = "😕") }
            }
        }
    }
    BillDetailScreen(state, snackbar, onBack = onBack, onDelete = viewModel::deleteBill, onRetry = viewModel::retry, onSplit = { onSplit(viewModel.billId) })
}

/**
 * One bill on Marigold. The hero (aubergine, night ink like the group's) carries the total, who paid, how the bill was made and
 * the one main action: split it, or edit the split. Below: who pays what (a small donut and each share saying who owes the payer)
 * and the receipt itself, with tax, tip and whatever makes the lines add up to the printed total named for what it is. Delete
 * sits in the top bar and asks first.
 */
@Composable
fun BillDetailScreen(
    state: BillDetailUiState,
    snackbar: SsSnackbarState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onSplit: () -> Unit = {},
) {
    val colors = SplitSmartTheme.colors
    val content = (state.bill as? LoadState.Content)?.data
    var confirming by rememberSaveable { mutableStateOf(false) }
    val sheetOpen = confirming || state.deleting
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().then(if (sheetOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
            SsTopBar(
                title = content?.bill?.title ?: stringResource(R.string.bill_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                actions = {
                    if (content != null) {
                        ChunkyIconButton(
                            Icons.Filled.Delete,
                            stringResource(R.string.bill_delete),
                            { confirming = true },
                            Modifier.testTag(SplitSmartTestTags.DELETE_BILL_BUTTON),
                            style = ButtonStyle.Ghost,
                            enabled = !state.deleting,
                        )
                    }
                },
            )
            when (val bill = state.bill) {
                LoadState.Loading -> BillLoading()
                LoadState.Empty -> BillError(stringResource(R.string.error_not_found), onRetry)
                is LoadState.Error -> BillError(stringResource(bill.reason.messageRes()), onRetry)
                is LoadState.Content -> BillDetailContent(bill.data, onSplit)
            }
        }
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        if (content != null) {
            DeleteSheet(
                visible = sheetOpen,
                title = content.bill.title,
                deleting = state.deleting,
                onDismiss = { confirming = false },
                onConfirm = onDelete,
            )
        }
    }
}

@Composable
private fun BillDetailContent(detail: BillDetail, onSplit: () -> Unit) {
    val bill = detail.bill
    val unknown = stringResource(R.string.member_unknown)
    val rows = remember(detail, unknown) { BillDetailLogic.shareRows(detail, unknown) }
    val extras = remember(bill) { BillDetailLogic.extras(bill) }
    val slices = remember(rows) { DonutLogic.slices(rows.map { it.memberId to it.share.totalPaise }) }
    val sliceColors = rememberSliceColors(slices)
    // No list-wide spacing: the receipt's lines sit flush on one paper strip, every other block keeps a gap below itself.
    val gap = Modifier.padding(bottom = Spacing.sm)
    LazyColumn(Modifier.fillMaxSize().testTag(BillDetailTags.LIST)) {
        item(key = "hero", contentType = "hero") {
            BillHero(detail, onSplit, gap.padding(horizontal = Spacing.screen, vertical = Spacing.xxs))
        }
        item(key = "sharesHeader", contentType = "header") { SsSectionHeader(stringResource(R.string.bill_who_pays_title), gap, emoji = "🍕") }
        if (rows.isEmpty()) {
            item(key = "notSplit", contentType = "empty") { NotSplit(gap.padding(horizontal = Spacing.screen)) }
        } else {
            item(key = "donut", contentType = "donut") { SharesSummary(detail, rows, slices, sliceColors, gap.padding(horizontal = Spacing.screen)) }
            items(rows, key = { "share:" + it.memberId }, contentType = { "share" }) { row ->
                ShareRowCard(row, sliceColors[row.memberId], gap.padding(horizontal = Spacing.screen))
            }
        }
        item(key = "items", contentType = "header") { SsSectionHeader(stringResource(R.string.bill_receipt_title), gap, emoji = "🧾") }
        if (bill.items.isEmpty()) {
            item(key = "noItems", contentType = "note") {
                BasicText(
                    stringResource(R.string.bill_no_items),
                    gap.padding(horizontal = Spacing.screen),
                    style = SplitSmartTheme.type.body.copy(color = SplitSmartTheme.colors.textSecondary),
                )
            }
        }
        itemsIndexed(bill.items, key = { _, item -> "item:" + item.id }, contentType = { _, _ -> "line" }) { index, item ->
            val last = index == bill.items.lastIndex
            ReceiptLine(item, receiptShape(index, bill.items.size), divider = !last, (if (last) gap else Modifier).padding(horizontal = Spacing.screen))
        }
        item(key = "total", contentType = "total") { ReceiptTotals(bill.totalPaise, extras, Modifier.padding(horizontal = Spacing.screen)) }
        item(key = "end", contentType = "end") { Spacer(Modifier.navigationBarsPadding().height(Spacing.xl)) }
    }
}

/** The hero: status and how it was made, the total, who paid, and Split as the one main action. Night ink on aubergine. */
@Composable
private fun BillHero(detail: BillDetail, onSplit: () -> Unit, modifier: Modifier) {
    val bill = detail.bill
    ChunkyCard(modifier.fillMaxWidth().testTag(BillDetailTags.HERO), color = SplitSmartTheme.colors.bar, shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        CompositionLocalProvider(LocalMarigoldColors provides HeroPalette) {
            val colors = SplitSmartTheme.colors
            val type = SplitSmartTheme.type
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatusPill(bill.status.kind(), bill.status.label(), animate = false)
                if (bill.source == BillSource.SCAN_LLM) StatusPill(StatusKind.Ai, stringResource(R.string.badge_ai), Modifier.testTag(SplitSmartTestTags.AI_BADGE), animate = false)
                Spacer(Modifier.weight(1f))
                BasicText(formatDate(bill.createdAt), style = type.label.copy(color = colors.textSecondary), maxLines = 1)
            }
            AmountText(bill.totalPaise, Modifier.padding(top = Spacing.sm), style = type.hero, color = colors.primary)
            Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val payer = detail.payerName
                if (payer != null) MemberAvatar(payer, size = Sizes.avatarSmall, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        stringResource(R.string.bill_paid_by_value, payer?.let { DisplayText.safe(it) } ?: stringResource(R.string.bill_payer_unknown)),
                        style = type.bodyStrong.copy(color = colors.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(bill.source.label(), style = type.label.copy(color = colors.textSecondary))
                }
            }
            ChunkyButton(
                stringResource(if (bill.split == null) R.string.bill_split_button else R.string.bill_split_edit_button),
                onSplit,
                Modifier.fillMaxWidth().padding(top = Spacing.md).testTag(SplitSmartTestTags.SPLIT_BUTTON),
                emoji = "➗",
            )
        }
    }
}

/** Draft is still to do (amber), split is done but money still moves (neutral), settled is mint. Same as the group's rows. */
private fun BillStatus.kind(): StatusKind = when (this) {
    BillStatus.DRAFT -> StatusKind.Refining
    BillStatus.SPLIT -> StatusKind.Neutral
    BillStatus.SETTLED -> StatusKind.Settled
}

@Composable
private fun NotSplit(modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth().testTag(BillDetailTags.NOT_SPLIT)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Illustration(IllustrationKind.Bills, size = Sizes.avatarHero, animate = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(stringResource(R.string.bill_not_split_title), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
                BasicText(stringResource(R.string.bill_not_split_body), style = type.body.copy(color = colors.textSecondary))
            }
        }
    }
}

/** A small donut of the shares beside how the bill was split. The same colours as the split screen, in the same ring order. */
@Composable
private fun SharesSummary(detail: BillDetail, rows: List<ShareRow>, slices: List<DonutSlice>, sliceColors: SliceColors, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val split = detail.bill.split ?: return
    val option = SplitModeOption.of(split.config)
    ChunkyCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SplitDonut(slices, sliceColors, Modifier.clearAndSetSemantics {}, size = Sizes.donutSmall, thickness = Strokes.donutSmall) {
                BasicText(option.emoji, style = type.title.copy(color = colors.text))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(stringResource(option.labelRes()), style = type.bodyStrong.copy(color = colors.text))
                BasicText(pluralStringResource(R.plurals.split_ways, rows.size, rows.size), style = type.label.copy(color = colors.textSecondary))
                if (rows.sumOf { it.share.totalPaise } == detail.bill.totalPaise) {
                    StatusPill(StatusKind.Settled, stringResource(R.string.bill_shares_exact), animate = false)
                }
            }
        }
    }
}

@Composable
private fun ShareRowCard(row: ShareRow, sliceColor: Color, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val name = DisplayText.safe(row.name)
    val amount = AmountFormat.format(row.share.totalPaise)
    val (line, spoken, tone) = when (val r = row.relation) {
        is ShareRelation.Paid -> Triple(
            stringResource(R.string.bill_share_paid_back, AmountFormat.format(r.getsBackPaise)),
            stringResource(R.string.bill_share_paid_spoken, name, AmountFormat.format(r.getsBackPaise)),
            colors.success.content,
        )
        is ShareRelation.Owes -> Triple(
            stringResource(R.string.bill_share_owes, DisplayText.safe(r.payerName)),
            stringResource(R.string.bill_share_owes_spoken, name, DisplayText.safe(r.payerName), amount),
            colors.failed.content,
        )
        ShareRelation.None -> Triple(null, stringResource(R.string.bill_share_spoken, name, amount), colors.textSecondary)
    }
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.SHARE_ROW), contentPadding = PaddingValues(Spacing.md)) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box {
                    MemberAvatar(row.name, size = Sizes.avatar, contentDescription = null)
                    ColorDot(sliceColor, Modifier.align(Alignment.BottomEnd))
                }
                Column(Modifier.weight(1f)) {
                    BasicText(name, style = type.bodyStrong.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line != null) BasicText(line, style = type.labelStrong.copy(color = tone), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AmountText(row.share.totalPaise, style = type.amount, color = colors.text)
            }
            BasicText(shareBreakdown(row.share), style = type.label.copy(color = colors.textSecondary))
        }
    }
}

private val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
private const val DASH_ON = 6f
private const val DASH_OFF = 6f

/** Lines of the receipt sit on one paper strip: the first and last carry its rounded corners. */
private fun receiptShape(index: Int, count: Int): Shape = when {
    count == 1 -> Radii.mediumShape
    index == 0 -> RoundedCornerShape(topStart = Radii.medium, topEnd = Radii.medium)
    index == count - 1 -> RoundedCornerShape(bottomStart = Radii.medium, bottomEnd = Radii.medium)
    else -> RectangleShape
}

@Composable
private fun ReceiptLine(item: BillItem, shape: Shape, divider: Boolean, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val hairline = colors.hairline
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .drawBehind {
                // A dotted tear line between lines, like a printed receipt.
                if (divider) {
                    val y = size.height - Strokes.hairline.toPx() / 2f
                    drawLine(hairline, Offset(Spacing.md.toPx(), y), Offset(size.width - Spacing.md.toPx(), y), Strokes.hairline.toPx(), pathEffect = dash)
                }
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(SplitSmartTestTags.BILL_ITEM_ROW)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(DisplayText.safe(item.name), style = type.body.copy(color = colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.qty > 1 || item.unitPaise > 0 && item.unitPaise != item.totalPaise) {
                BasicText(stringResource(R.string.bill_item_qty, item.qty, AmountFormat.format(item.unitPaise)), style = type.label.copy(color = colors.textSecondary))
            }
        }
        AmountText(item.totalPaise, style = type.amountSmall, color = colors.text)
    }
}

/** Tax, tip and the adjustment (each read out as "Tax, ₹62.50"), then the printed total in bold. */
@Composable
private fun ReceiptTotals(totalPaise: Long, extras: List<ReceiptExtra>, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            extras.forEach { extra ->
                val label = stringResource(extra.labelRes())
                val spoken = stringResource(R.string.bill_line_spoken, label, AmountFormat.spoken(extra.paise))
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(label, Modifier.weight(1f), style = type.body.copy(color = colors.textSecondary))
                    AmountText(extra.paise, style = type.amountSmall, color = if (extra.paise < 0) colors.success.content else colors.text)
                }
            }
            val totalLabel = stringResource(R.string.bill_total_label)
            val totalSpoken = stringResource(R.string.bill_line_spoken, totalLabel, AmountFormat.spoken(totalPaise))
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = totalSpoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(totalLabel, Modifier.weight(1f), style = type.title.copy(color = colors.text))
                AmountText(totalPaise, style = type.amount, color = colors.text)
            }
        }
    }
}

private fun ReceiptExtra.labelRes(): Int = when (this) {
    is ReceiptExtra.Tax -> R.string.bill_tax_label
    is ReceiptExtra.Tip -> R.string.bill_tip_label
    is ReceiptExtra.Adjustment -> when (kind) {
        AdjustmentKind.Discount -> R.string.bill_discount_label
        AdjustmentKind.Charges -> R.string.bill_charges_label
        AdjustmentKind.RoundOff -> R.string.bill_round_off_label
    }
}

@Composable
private fun BillLoading() {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen).testTag(SplitSmartTestTags.LOADING), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonGroup { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.heroSkeleton), Radii.largeShape) }
        SkeletonList(count = LOADING_ROWS, label = stringResource(R.string.loading))
    }
}

private const val LOADING_ROWS = 4

@Composable
private fun BillError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ErrorState(
            title = stringResource(R.string.bill_title),
            message = message,
            onRetry = onRetry,
            retryLabel = stringResource(R.string.retry_label),
            modifier = Modifier.testTag(SplitSmartTestTags.ERROR),
        )
    }
}

@Composable
private fun DeleteSheet(visible: Boolean, title: String, deleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val haptics = LocalHaptics.current
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    SsBottomSheet(visible = visible, onDismissRequest = onDismiss, dismissible = !deleting) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg).testTag(BillDetailTags.DELETE_SHEET),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BasicText(stringResource(R.string.bill_delete_title, DisplayText.safe(title)), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
            BasicText(stringResource(R.string.bill_delete_body), style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Start))
            ChunkyButton(
                stringResource(R.string.bill_delete),
                {
                    haptics.heavy()
                    onConfirm()
                },
                Modifier.fillMaxWidth().testTag(BillDetailTags.DELETE_CONFIRM),
                style = ButtonStyle.Danger,
                loading = deleting,
            )
            ChunkyButton(
                stringResource(R.string.bill_delete_keep),
                onDismiss,
                Modifier.fillMaxWidth().testTag(BillDetailTags.DELETE_KEEP),
                style = ButtonStyle.Ghost,
                enabled = !deleting,
            )
        }
    }
}
