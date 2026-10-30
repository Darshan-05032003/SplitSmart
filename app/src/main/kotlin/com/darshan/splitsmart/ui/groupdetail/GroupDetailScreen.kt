package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.AiBadge
import com.darshan.splitsmart.ui.components.AppIcons
import com.darshan.splitsmart.ui.components.BannerTone
import com.darshan.splitsmart.ui.components.ContentCard
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.GroupAvatar
import com.darshan.splitsmart.ui.components.HeroCard
import com.darshan.splitsmart.ui.components.IconBubble
import com.darshan.splitsmart.ui.components.InfoBanner
import com.darshan.splitsmart.ui.components.InitialAvatar
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.SectionHeader
import com.darshan.splitsmart.ui.components.appIcon
import com.darshan.splitsmart.ui.components.formatShortDate
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

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
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupDetailEvent.BillCreated -> onOpenBill(event.billId)
                GroupDetailEvent.GroupDeleted -> onBack()
                GroupDetailEvent.Failed -> snackbar.showSnackbar(failedText)
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
        ),
    )
}

data class GroupDetailCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenBill: (String) -> Unit,
    val onScan: () -> Unit,
    val onSettleUp: () -> Unit,
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(state: GroupDetailUiState, snackbar: SnackbarHostState, callbacks: GroupDetailCallbacks) {
    val content = state.detail as? LoadState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.data?.group?.name ?: stringResource(R.string.group_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = callbacks.onDeleteGroup, modifier = Modifier.testTag(SplitSmartTestTags.DELETE_GROUP_BUTTON)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.group_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val detail = state.detail) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Empty -> EmptyView(stringResource(R.string.error_not_found), Modifier.padding(padding))
            is LoadState.Error -> ErrorView(detail.reason, callbacks.onRetry, Modifier.padding(padding))
            is LoadState.Content -> GroupDetailContent(detail.data, callbacks, Modifier.padding(padding))
        }
    }
    state.memberDraft?.let { MemberDialog(it, callbacks) }
    state.billDraft?.let { BillDialog(it, content?.data?.members.orEmpty(), callbacks) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDetailContent(detail: GroupDetail, callbacks: GroupDetailCallbacks, modifier: Modifier) {
    val unknown = stringResource(R.string.member_unknown)
    val nameOf = { id: String? -> detail.members.find { it.id == id }?.name }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") { SummaryCard(detail, callbacks.onSettleUp) }
        item(key = "members") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.members_title)) {
                    TextButton(onClick = callbacks.onAddMember, modifier = Modifier.testTag(SplitSmartTestTags.ADD_MEMBER_BUTTON)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.members_add))
                    }
                }
                if (detail.members.isEmpty()) {
                    Text(stringResource(R.string.members_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.members.forEach { member -> MemberChip(member, onRemove = { callbacks.onRemoveMember(member.id) }) }
                    }
                }
            }
        }
        item(key = "add") { AddBillCard(callbacks) }
        if (detail.bills.isNotEmpty()) {
            item(key = "balances") {
                SectionHeader(stringResource(R.string.group_balances_title))
            }
            if (detail.debts.isEmpty()) {
                item(key = "settled") {
                    InfoBanner(BannerTone.SUCCESS, icon = rememberVectorPainter(Icons.Default.CheckCircle)) {
                        Text(stringResource(R.string.group_all_settled), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(detail.debts, key = { "d" + it.fromMemberId + it.toMemberId }) { debt ->
                DebtRow(debt, nameOf(debt.fromMemberId) ?: unknown, nameOf(debt.toMemberId) ?: unknown)
            }
            if (detail.unsettledBills > 0) {
                item(key = "unsettled") {
                    Text(
                        pluralStringResource(R.plurals.group_unsettled, detail.unsettledBills, detail.unsettledBills),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "billsHeader") { SectionHeader(stringResource(R.string.bills_title)) }
        if (detail.bills.isEmpty()) {
            item(key = "noBills") {
                Text(stringResource(R.string.bills_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(detail.bills, key = { "b" + it.id }) { bill ->
            BillRow(bill, payerName = nameOf(bill.paidByMemberId), onClick = { callbacks.onOpenBill(bill.id) })
        }
    }
}

@Composable
private fun SummaryCard(detail: GroupDetail, onSettleUp: () -> Unit) {
    val onHero = SplitSmartTheme.colors.onHero
    val spent = detail.bills.sumOf { it.totalPaise }
    val outstanding = detail.debts.sumOf { it.amountPaise }
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupAvatar(detail.group.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(
                    R.string.group_summary_meta,
                    pluralStringResource(R.plurals.member_count, detail.members.size, detail.members.size),
                    pluralStringResource(R.plurals.bill_count, detail.bills.size, detail.bills.size),
                ),
                style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.9f),
            )
        }
        Text(
            buildAnnotatedString {
                withStyle(MaterialTheme.typography.headlineLarge.toSpanStyle()) { append(Money.formatInr(spent)) }
                withStyle(MaterialTheme.typography.titleMedium.toSpanStyle()) { append(" " + stringResource(R.string.group_summary_spent)) }
            },
            color = onHero,
        )
        Text(
            if (outstanding > 0) {
                pluralStringResource(R.plurals.group_summary_outstanding, detail.debts.size, Money.formatInr(outstanding), detail.debts.size)
            } else {
                stringResource(if (detail.bills.isEmpty()) R.string.group_summary_no_bills else R.string.group_all_settled)
            },
            style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.9f),
        )
        if (detail.debts.isNotEmpty()) {
            Button(
                onClick = onSettleUp,
                colors = ButtonDefaults.buttonColors(containerColor = onHero, contentColor = SplitSmartTheme.colors.heroAction),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag(SplitSmartTestTags.SETTLE_UP_BUTTON),
            ) {
                Icon(appIcon(AppIcons.Wallet), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.group_settle_up))
            }
        }
    }
}

@Composable
private fun AddBillCard(callbacks: GroupDetailCallbacks) {
    ContentCard {
        Text(stringResource(R.string.bills_add), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddBillAction(appIcon(AppIcons.Camera), stringResource(R.string.scan_receipt), SplitSmartTestTags.SCAN_BUTTON, callbacks.onScan, primary = true)
            AddBillAction(appIcon(AppIcons.Gallery), stringResource(R.string.add_bill_gallery), SplitSmartTestTags.GALLERY_ADD_BUTTON, callbacks.onScanFromGallery)
            AddBillAction(appIcon(AppIcons.EditNote), stringResource(R.string.add_bill_manual), SplitSmartTestTags.ADD_BILL_BUTTON, callbacks.onAddBill)
        }
    }
}

@Composable
private fun RowScope.AddBillAction(icon: Painter, label: String, tag: String, onClick: () -> Unit, primary: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (primary) scheme.primary else scheme.surfaceContainerHighest,
        contentColor = if (primary) scheme.onPrimary else scheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.weight(1f).testTag(tag),
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun MemberChip(member: Member, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape,
        modifier = Modifier.testTag(SplitSmartTestTags.MEMBER_ITEM),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(member.name, size = 32.dp)
            Column(Modifier.padding(start = 8.dp)) {
                Text(member.name, style = MaterialTheme.typography.labelLarge)
                member.vpa?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.members_remove, member.name), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, from: String, to: String) {
    ContentCard(Modifier.testTag(SplitSmartTestTags.DEBT_ITEM), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(from, size = 32.dp)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
            )
            InitialAvatar(to, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.debt_row, from, to), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(Money.formatInr(debt.amountPaise), style = MaterialTheme.typography.titleMedium, color = SplitSmartTheme.colors.owe.content)
        }
    }
}

/** One bill in a group's history: date, payer, amount and an AI badge when Gemini read the receipt. */
@Composable
fun BillRow(bill: Bill, payerName: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.BILL_ITEM),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val scanned = bill.source != BillSource.MANUAL
            IconBubble(
                appIcon(if (scanned) AppIcons.Receipt else AppIcons.EditNote),
                container = if (bill.source == BillSource.SCAN_LLM) scheme.secondaryContainer else scheme.primaryContainer,
                content = if (bill.source == BillSource.SCAN_LLM) scheme.onSecondaryContainer else scheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bill.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (bill.source == BillSource.SCAN_LLM) {
                        Spacer(Modifier.width(6.dp))
                        AiBadge(Modifier.testTag(SplitSmartTestTags.AI_BADGE))
                    }
                }
                Text(
                    stringResource(
                        R.string.bill_row_meta, formatShortDate(bill.createdAt),
                        payerName?.let { stringResource(R.string.bill_paid_by_value, it) } ?: stringResource(R.string.bill_no_payer),
                    ),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(bill.status.label(), style = MaterialTheme.typography.labelSmall, color = if (bill.status == BillStatus.DRAFT) scheme.tertiary else scheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Text(Money.formatInr(bill.totalPaise), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MemberDialog(draft: MemberDraft, callbacks: GroupDetailCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissMember,
        title = { Text(stringResource(R.string.members_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name, onValueChange = callbacks.onMemberName, singleLine = true,
                    label = { Text(stringResource(R.string.member_name_label)) },
                    isError = draft.nameError != null || draft.duplicate || draft.groupFull,
                    supportingText = when {
                        draft.nameError != null -> { { Text(draft.nameError.label()) } }
                        draft.duplicate -> { { Text(stringResource(R.string.error_member_duplicate)) } }
                        draft.groupFull -> { { Text(stringResource(R.string.error_group_full)) } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.MEMBER_NAME_FIELD),
                )
                OutlinedTextField(
                    value = draft.vpa, onValueChange = callbacks.onMemberVpa, singleLine = true,
                    label = { Text(stringResource(R.string.member_vpa_label)) },
                    isError = draft.vpaError != null,
                    supportingText = draft.vpaError?.let { { Text(it.label()) } } ?: { Text(stringResource(R.string.member_vpa_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.MEMBER_VPA_FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = callbacks.onSubmitMember, enabled = !draft.submitting, modifier = Modifier.testTag(SplitSmartTestTags.DIALOG_CONFIRM)) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = callbacks.onDismissMember) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BillDialog(draft: BillDraft, members: List<Member>, callbacks: GroupDetailCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissBill,
        title = { Text(stringResource(R.string.bills_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.title, onValueChange = callbacks.onBillTitle, singleLine = true,
                    label = { Text(stringResource(R.string.bill_title_label)) },
                    isError = draft.titleError != null,
                    supportingText = draft.titleError?.let { { Text(it.label()) } },
                    modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.BILL_TITLE_FIELD),
                )
                OutlinedTextField(
                    value = draft.amount, onValueChange = callbacks.onBillAmount, singleLine = true,
                    label = { Text(stringResource(R.string.bill_amount_label)) },
                    isError = draft.amountError,
                    supportingText = if (draft.amountError) { { Text(stringResource(R.string.error_amount_invalid)) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.BILL_AMOUNT_FIELD),
                )
                if (members.isNotEmpty()) {
                    Text(stringResource(R.string.bill_paid_by), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { m ->
                            FilterChip(selected = draft.payerId == m.id, onClick = { callbacks.onBillPayer(if (draft.payerId == m.id) null else m.id) }, label = { Text(m.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = callbacks.onSubmitBill, enabled = !draft.submitting, modifier = Modifier.testTag(SplitSmartTestTags.DIALOG_CONFIRM)) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = callbacks.onDismissBill) { Text(stringResource(R.string.action_cancel)) } },
    )
}
