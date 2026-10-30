package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.formatDate
import com.darshan.splitsmart.ui.components.label

@Composable
fun GroupDetailRoute(
    viewModel: GroupDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    onScanReceipt: (String) -> Unit,
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
        ),
    )
}

data class GroupDetailCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenBill: (String) -> Unit,
    val onScan: () -> Unit,
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(state: GroupDetailUiState, snackbar: SnackbarHostState, callbacks: GroupDetailCallbacks) {
    val content = state.detail as? LoadState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.data?.group?.name ?: stringResource(R.string.group_title)) },
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
        floatingActionButton = {
            if (content != null) {
                ExtendedFloatingActionButton(
                    onClick = callbacks.onScan,
                    modifier = Modifier.testTag(SplitSmartTestTags.SCAN_BUTTON),
                    text = { Text(stringResource(R.string.scan_receipt)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                )
            }
        },
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

@Composable
private fun GroupDetailContent(detail: GroupDetail, callbacks: GroupDetailCallbacks, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item {
            SectionHeader(stringResource(R.string.members_title), stringResource(R.string.members_add), SplitSmartTestTags.ADD_MEMBER_BUTTON, callbacks.onAddMember)
        }
        if (detail.members.isEmpty()) {
            item { Text(stringResource(R.string.members_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        }
        items(detail.members, key = { "m" + it.id }) { member -> MemberRow(member, onRemove = { callbacks.onRemoveMember(member.id) }) }
        item {
            Spacer(Modifier.padding(top = 16.dp))
            SectionHeader(stringResource(R.string.bills_title), stringResource(R.string.bills_add), SplitSmartTestTags.ADD_BILL_BUTTON, callbacks.onAddBill)
        }
        if (detail.bills.isEmpty()) {
            item { Text(stringResource(R.string.bills_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        }
        items(detail.bills, key = { "b" + it.id }) { bill -> BillRow(bill, onClick = { callbacks.onOpenBill(bill.id) }) }
        if (detail.bills.isNotEmpty()) {
            item {
                Spacer(Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.group_balances_title), style = MaterialTheme.typography.titleMedium)
            }
            if (detail.debts.isEmpty()) {
                item { Text(stringResource(R.string.group_all_settled), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(detail.debts, key = { "d" + it.fromMemberId + it.toMemberId }) { debt -> DebtRow(debt, detail.members) }
            if (detail.unsettledBills > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.group_unsettled, detail.unsettledBills, detail.unsettledBills),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, members: List<Member>) {
    val unknown = stringResource(R.string.member_unknown)
    val name = { id: String -> members.find { it.id == id }?.name ?: unknown }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(SplitSmartTestTags.DEBT_ITEM), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.debt_row, name(debt.fromMemberId), name(debt.toMemberId)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(Money.formatInr(debt.amountPaise), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, tag: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction, modifier = Modifier.testTag(tag)) { Text(action) }
    }
}

@Composable
private fun MemberRow(member: Member, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(SplitSmartTestTags.MEMBER_ITEM), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.bodyLarge)
            member.vpa?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.members_remove, member.name)) }
    }
}

@Composable
fun BillRow(bill: Bill, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick).testTag(SplitSmartTestTags.BILL_ITEM)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bill.title, style = MaterialTheme.typography.titleSmall)
                Text("${formatDate(bill.createdAt)} · ${bill.status.label()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
