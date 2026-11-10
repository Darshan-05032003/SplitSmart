package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.heavy
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.simplify.DebtGraphLogic
import com.darshan.splitsmart.ui.simplify.SimplifyCard
import com.darshan.splitsmart.ui.simplify.SimplifySheetContent
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

@Composable
fun GroupDetailRoute(
    viewModel: GroupDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    onScanReceipt: (String) -> Unit,
    onSettleUp: (String) -> Unit = {},
    onScanFromGallery: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.error_unknown)
    val archivedTemplate = stringResource(R.string.groups_archived_message)
    val undo = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupDetailEvent.BillCreated -> onOpenBill(event.billId)
                GroupDetailEvent.GroupDeleted -> onBack()
                GroupDetailEvent.Failed -> scope.launch { snackbar.show(failedText, emoji = "😕") }
                is GroupDetailEvent.GroupArchived -> scope.launch {
                    val message = String.format(archivedTemplate, DisplayText.safe(event.name))
                    if (snackbar.show(message, emoji = "📦", actionLabel = undo) == SnackbarResult.Undo) viewModel.setArchived(false)
                }
            }
        }
    }
    GroupDetailScreen(
        state = state,
        snackbar = snackbar,
        callbacks = GroupDetailCallbacks(
            onBack = onBack,
            onRetry = viewModel::retry,
            onOpenBill = onOpenBill,
            onScan = { onScanReceipt(viewModel.groupId) },
            onSettleUp = { onSettleUp(viewModel.groupId) },
            onDeleteGroup = viewModel::deleteGroup,
            onAddMember = viewModel::openAddMember,
            onDismissMember = viewModel::dismissMember,
            onMemberName = viewModel::onMemberNameChange,
            onMemberVpa = viewModel::onMemberVpaChange,
            onSubmitMember = viewModel::submitMember,
            onRemoveMember = viewModel::removeMember,
            onAddBill = viewModel::openAddBill,
            onDismissBill = viewModel::dismissBill,
            onBillTitle = viewModel::onBillTitleChange,
            onBillAmount = viewModel::onBillAmountChange,
            onBillPayer = viewModel::onBillPayerChange,
            onSubmitBill = viewModel::submitBill,
            onScanFromGallery = { onScanFromGallery(viewModel.groupId) },
            onArchive = viewModel::setArchived,
        ),
    )
}

data class GroupDetailCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenBill: (String) -> Unit,
    val onScan: () -> Unit,
    val onSettleUp: () -> Unit,
    /** Runs only after the delete confirmation. */
    val onDeleteGroup: () -> Unit,
    val onAddMember: () -> Unit,
    val onDismissMember: () -> Unit,
    val onMemberName: (String) -> Unit,
    val onMemberVpa: (String) -> Unit,
    val onSubmitMember: () -> Unit,
    val onRemoveMember: (String) -> Unit,
    val onAddBill: () -> Unit,
    val onDismissBill: () -> Unit,
    val onBillTitle: (String) -> Unit,
    val onBillAmount: (String) -> Unit,
    val onBillPayer: (String?) -> Unit,
    val onSubmitBill: () -> Unit,
    val onScanFromGallery: () -> Unit = {},
    /** true archives, false brings the group back. */
    val onArchive: (Boolean) -> Unit = {},
)

/** Which in-window sheet is up besides the add-member and add-bill drafts (those live in the view model). */
private enum class DetailSheet { None, Options, Delete, Member, Simplify }

/**
 * The group screen on Marigold: the hero (spent together, still to move, Settle up when anyone owes anyone), the members as a
 * row of faces, three ways to add a bill, who owes whom and the bills. The one primary action is Settle up when there is money
 * to move, otherwise the Scan tile. Destructive and rare actions (archive, delete) sit behind the top bar's options, and delete
 * asks first. Every form is an in-window sheet.
 */
@Composable
fun GroupDetailScreen(state: GroupDetailUiState, snackbar: SsSnackbarState, callbacks: GroupDetailCallbacks) {
    val content = (state.detail as? LoadState.Content)?.data
    var sheet by rememberSaveable { mutableStateOf(DetailSheet.None) }
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
    var simplifyPlays by rememberSaveable { mutableIntStateOf(0) }
    val anySheet = sheet != DetailSheet.None || state.memberDraft != null || state.billDraft != null
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if (anySheet) Modifier.clearAndSetSemantics {} else Modifier)) {
            SsTopBar(
                title = content?.group?.name ?: stringResource(R.string.group_title),
                onBack = callbacks.onBack,
                backLabel = stringResource(R.string.action_back),
                subtitle = if (content?.group?.isArchived == true) stringResource(R.string.group_archived_label) else null,
                actions = {
                    if (content != null) {
                        ChunkyIconButton(
                            Icons.Filled.MoreVert,
                            stringResource(R.string.group_options),
                            { sheet = DetailSheet.Options },
                            Modifier.testTag(GroupDetailTags.OPTIONS),
                            style = ButtonStyle.Ghost,
                        )
                    }
                },
            )
            when (val detail = state.detail) {
                LoadState.Loading -> DetailLoading()
                LoadState.Empty -> DetailError(stringResource(R.string.error_not_found), callbacks.onRetry)
                is LoadState.Error -> DetailError(stringResource(detail.reason.messageRes()), callbacks.onRetry)
                is LoadState.Content -> GroupDetailContent(
                    detail.data,
                    callbacks,
                    onOpenMember = { memberId = it.id; sheet = DetailSheet.Member },
                    onOpenSimplify = { simplifyPlays++; sheet = DetailSheet.Simplify },
                )
            }
        }
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        if (content != null) {
            OptionsSheet(
                visible = sheet == DetailSheet.Options,
                archived = content.group.isArchived,
                onDismiss = { sheet = DetailSheet.None },
                onArchive = { sheet = DetailSheet.None; callbacks.onArchive(!content.group.isArchived) },
                onDelete = { sheet = DetailSheet.Delete },
            )
            DeleteSheet(
                visible = sheet == DetailSheet.Delete || state.deleting,
                detail = content,
                deleting = state.deleting,
                onDismiss = { sheet = DetailSheet.None },
                onConfirm = callbacks.onDeleteGroup,
            )
            MemberSheet(
                member = content.members.firstOrNull { it.id == memberId }.takeIf { sheet == DetailSheet.Member },
                onDismiss = { sheet = DetailSheet.None },
                onRemove = { id -> sheet = DetailSheet.None; callbacks.onRemoveMember(id) },
            )
            SimplifySheet(content, visible = sheet == DetailSheet.Simplify, playKey = simplifyPlays, onReplay = { simplifyPlays++ }, onDismiss = { sheet = DetailSheet.None })
        }
        AddMemberSheet(state.memberDraft, callbacks)
        AddBillSheet(state.billDraft, content?.members.orEmpty(), callbacks)
    }
}

@Composable
private fun GroupDetailContent(detail: GroupDetail, callbacks: GroupDetailCallbacks, onOpenMember: (Member) -> Unit, onOpenSimplify: () -> Unit) {
    val unknown = stringResource(R.string.member_unknown)
    val names = remember(detail.members) { detail.members.associate { it.id to it.name } }
    val primary = GroupDetailLogic.primaryAction(detail)
    val addMemberLabel = stringResource(R.string.members_add)
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val graph = remember(detail.members, detail.bills, detail.settlements) { DebtGraphLogic.graph(detail.members, detail.bills, detail.settlements) }
    LazyColumn(Modifier.fillMaxSize().testTag(GroupDetailTags.LIST), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(key = "hero", contentType = "hero") {
            GroupHero(detail, primary, callbacks.onSettleUp, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs))
        }
        item(key = "members", contentType = "members") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SsSectionHeader(
                    stringResource(R.string.members_title),
                    emoji = "👯",
                    actionLabel = stringResource(R.string.members_add_short),
                    onAction = callbacks.onAddMember,
                    actionModifier = Modifier.testTag(SplitSmartTestTags.ADD_MEMBER_BUTTON).semantics { contentDescription = addMemberLabel },
                )
                if (detail.members.isEmpty()) {
                    BasicText(
                        stringResource(R.string.members_empty),
                        Modifier.padding(horizontal = Spacing.screen),
                        style = type.body.copy(color = colors.textSecondary),
                    )
                } else {
                    MembersRow(detail.members, onOpenMember)
                }
            }
        }
        item(key = "add", contentType = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SsSectionHeader(stringResource(R.string.group_add_bill_title), emoji = "🧾")
                AddBillRow(primary, callbacks.onScan, callbacks.onScanFromGallery, callbacks.onAddBill)
            }
        }
        if (detail.bills.isNotEmpty()) {
            item(key = "balances", contentType = "header") { SsSectionHeader(stringResource(R.string.group_balances_title), emoji = "🤝") }
            if (detail.debts.isEmpty()) {
                item(key = "settled", contentType = "settled") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag(GroupDetailTags.SETTLED)) {
                        StatusPill(StatusKind.Settled, stringResource(R.string.group_all_square), animate = false)
                    }
                }
            }
            if (graph.saves > 0) {
                item(key = "simplify", contentType = "simplify") { SimplifyCard(graph, onOpenSimplify, Modifier.padding(horizontal = Spacing.screen)) }
            }
            items(detail.debts, key = { "d" + it.fromMemberId + ">" + it.toMemberId }, contentType = { "debt" }) { debt ->
                DebtRow(names[debt.fromMemberId] ?: unknown, names[debt.toMemberId] ?: unknown, debt.amountPaise, Modifier.padding(horizontal = Spacing.screen))
            }
            if (detail.unsettledBills > 0) {
                item(key = "unsettled", contentType = "note") {
                    BasicText(
                        pluralStringResource(R.plurals.group_unsettled, detail.unsettledBills, detail.unsettledBills),
                        Modifier.padding(horizontal = Spacing.screen),
                        style = type.label.copy(color = colors.textSecondary),
                    )
                }
            }
        }
        item(key = "billsHeader", contentType = "header") { SsSectionHeader(stringResource(R.string.bills_title), emoji = "📒") }
        if (detail.bills.isEmpty()) {
            item(key = "noBills", contentType = "empty") { NoBills() }
        }
        items(detail.bills, key = { "b" + it.id }, contentType = { "bill" }) { bill ->
            BillRow(bill, payerName = bill.paidByMemberId?.let { names[it] }, onClick = { callbacks.onOpenBill(bill.id) }, modifier = Modifier.padding(horizontal = Spacing.screen))
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.navigationBarsPadding().height(Spacing.xxl)) }
    }
}

/** The simplify-debts picture in a sheet; it replays each time it opens and on Play again. */
@Composable
private fun SimplifySheet(detail: GroupDetail, visible: Boolean, playKey: Int, onReplay: () -> Unit, onDismiss: () -> Unit) {
    val unknown = stringResource(R.string.member_unknown)
    val names = remember(detail.members) { detail.members.associate { it.id to it.name } }
    val graph = remember(detail.members, detail.bills, detail.settlements) { DebtGraphLogic.graph(detail.members, detail.bills, detail.settlements) }
    SsBottomSheet(visible = visible, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SimplifySheetContent(graph, nameOf = { names[it] ?: unknown }, playKey = playKey, onReplay = onReplay)
        }
    }
}

@Composable
private fun NoBills() {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md).testTag(GroupDetailTags.NO_BILLS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Illustration(IllustrationKind.Bills, size = Sizes.illustrationSmall)
        BasicText(stringResource(R.string.group_no_bills_title), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text, textAlign = TextAlign.Center))
        BasicText(stringResource(R.string.group_no_bills_body), style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
    }
}

@Composable
private fun DetailLoading() {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen).testTag(SplitSmartTestTags.LOADING), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonGroup { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.heroSkeleton), Radii.largeShape) }
        SkeletonList(count = DETAIL_SKELETON_ROWS, label = stringResource(R.string.groups_loading))
    }
}

private const val DETAIL_SKELETON_ROWS = 4

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ErrorState(
            title = stringResource(R.string.group_title),
            message = message,
            onRetry = onRetry,
            retryLabel = stringResource(R.string.retry_label),
            modifier = Modifier.testTag(SplitSmartTestTags.ERROR),
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    BasicText(text, Modifier.semantics { heading() }, style = SplitSmartTheme.type.title.copy(color = SplitSmartTheme.colors.text))
}

@Composable
private fun OptionsSheet(visible: Boolean, archived: Boolean, onDismiss: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    SsBottomSheet(visible = visible, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.md).testTag(GroupDetailTags.OPTIONS_SHEET)) {
            BasicText(stringResource(R.string.group_options), Modifier.padding(horizontal = Spacing.screen).semantics { heading() }, style = type.title.copy(color = colors.text))
            SsListRow(
                title = stringResource(if (archived) R.string.group_unarchive else R.string.group_archive),
                subtitle = stringResource(if (archived) R.string.group_unarchive_hint else R.string.group_archive_hint),
                leading = { BasicText(if (archived) "↩️" else "📦", style = type.title.copy(color = colors.text)) },
                onClick = onArchive,
                modifier = Modifier.testTag(GroupDetailTags.ARCHIVE),
            )
            SsListRow(
                title = stringResource(R.string.group_delete),
                subtitle = stringResource(R.string.group_delete_hint),
                leading = { BasicText("🗑️", style = type.title.copy(color = colors.text)) },
                onClick = onDelete,
                modifier = Modifier.testTag(SplitSmartTestTags.DELETE_GROUP_BUTTON),
            )
        }
    }
}

@Composable
private fun DeleteSheet(visible: Boolean, detail: GroupDetail, deleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val haptics = LocalHaptics.current
    val colors = SplitSmartTheme.colors
    SsBottomSheet(visible = visible, onDismissRequest = onDismiss, dismissible = !deleting) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg).testTag(GroupDetailTags.DELETE_SHEET),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle(stringResource(R.string.group_delete_title, DisplayText.safe(detail.group.name)))
            BasicText(
                stringResource(
                    R.string.group_delete_body,
                    pluralStringResource(R.plurals.bill_count, detail.bills.size, detail.bills.size),
                    pluralStringResource(R.plurals.member_count, detail.members.size, detail.members.size),
                ),
                style = SplitSmartTheme.type.body.copy(color = colors.textSecondary),
            )
            ChunkyButton(
                stringResource(R.string.group_delete),
                {
                    haptics.heavy()
                    onConfirm()
                },
                Modifier.fillMaxWidth().testTag(GroupDetailTags.DELETE_CONFIRM),
                style = ButtonStyle.Danger,
                loading = deleting,
            )
            ChunkyButton(
                stringResource(R.string.group_delete_keep),
                onDismiss,
                Modifier.fillMaxWidth().testTag(GroupDetailTags.DELETE_KEEP),
                style = ButtonStyle.Ghost,
                enabled = !deleting,
            )
        }
    }
}

@Composable
private fun MemberSheet(member: Member?, onDismiss: () -> Unit, onRemove: (String) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val shown = remember { arrayOfNulls<Member>(1) }
    if (member != null) shown[0] = member
    val current = member ?: shown[0]
    SsBottomSheet(visible = member != null, onDismissRequest = onDismiss) {
        if (current == null) return@SsBottomSheet
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg).testTag(GroupDetailTags.MEMBER_SHEET),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MemberAvatar(current.name, size = Sizes.avatarHero, contentDescription = null)
            SheetTitle(DisplayText.safe(current.name))
            BasicText(
                current.vpa ?: stringResource(R.string.member_upi_missing),
                style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
            )
            BasicText(stringResource(R.string.member_remove_hint), style = type.label.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
            ChunkyButton(
                stringResource(R.string.member_remove),
                { onRemove(current.id) },
                Modifier.fillMaxWidth().testTag(GroupDetailTags.MEMBER_REMOVE),
                style = ButtonStyle.Ghost,
            )
        }
    }
}

@Composable
private fun AddMemberSheet(draft: MemberDraft?, callbacks: GroupDetailCallbacks) {
    val shown = remember { arrayOfNulls<MemberDraft>(1) }
    if (draft != null) shown[0] = draft
    val current = draft ?: shown[0] ?: MemberDraft()
    SsBottomSheet(visible = draft != null, onDismissRequest = callbacks.onDismissMember, dismissible = !current.submitting) {
        Column(Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SheetTitle(stringResource(R.string.members_add))
            SsTextField(
                value = current.name,
                onValueChange = callbacks.onMemberName,
                label = stringResource(R.string.member_name_label),
                error = when {
                    current.nameError != null -> current.nameError.label()
                    current.duplicate -> stringResource(R.string.error_member_duplicate)
                    current.groupFull -> stringResource(R.string.error_group_full)
                    else -> null
                },
                emoji = "🙂",
                modifier = Modifier.testTag(SplitSmartTestTags.MEMBER_NAME_FIELD),
            )
            SsTextField(
                value = current.vpa,
                onValueChange = callbacks.onMemberVpa,
                label = stringResource(R.string.member_vpa_label),
                error = current.vpaError?.label(),
                supporting = stringResource(R.string.member_vpa_hint),
                emoji = "₹",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.testTag(SplitSmartTestTags.MEMBER_VPA_FIELD),
            )
            ChunkyButton(
                stringResource(R.string.members_add),
                callbacks.onSubmitMember,
                Modifier.fillMaxWidth().testTag(SplitSmartTestTags.DIALOG_CONFIRM),
                loading = current.submitting,
            )
        }
    }
}

@Composable
private fun AddBillSheet(draft: BillDraft?, members: List<Member>, callbacks: GroupDetailCallbacks) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    val shown = remember { arrayOfNulls<BillDraft>(1) }
    if (draft != null) shown[0] = draft
    val current = draft ?: shown[0] ?: BillDraft()
    SsBottomSheet(visible = draft != null, onDismissRequest = callbacks.onDismissBill, dismissible = !current.submitting) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle(stringResource(R.string.bills_add))
            SsTextField(
                value = current.title,
                onValueChange = callbacks.onBillTitle,
                label = stringResource(R.string.bill_title_label),
                error = current.titleError?.label(),
                emoji = "🧾",
                modifier = Modifier.testTag(SplitSmartTestTags.BILL_TITLE_FIELD),
            )
            SsTextField(
                value = current.amount,
                onValueChange = callbacks.onBillAmount,
                label = stringResource(R.string.bill_amount_label),
                error = if (current.amountError) stringResource(R.string.error_amount_invalid) else null,
                emoji = "₹",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.testTag(SplitSmartTestTags.BILL_AMOUNT_FIELD),
            )
            if (members.isNotEmpty()) {
                BasicText(stringResource(R.string.bill_paid_by), style = type.labelStrong.copy(color = colors.textSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    members.forEach { m ->
                        EmojiChip(
                            DisplayText.safe(m.name),
                            selected = current.payerId == m.id,
                            onClick = { callbacks.onBillPayer(if (current.payerId == m.id) null else m.id) },
                        )
                    }
                }
            }
            ChunkyButton(
                stringResource(R.string.bills_add),
                callbacks.onSubmitBill,
                Modifier.fillMaxWidth().testTag(SplitSmartTestTags.DIALOG_CONFIRM),
                loading = current.submitting,
            )
        }
    }
}
