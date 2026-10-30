package com.darshan.splitsmart.ui.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.ui.components.AiBadge
import com.darshan.splitsmart.ui.components.AmountRow
import com.darshan.splitsmart.ui.components.AppIcons
import com.darshan.splitsmart.ui.components.ContentCard
import com.darshan.splitsmart.ui.components.HeroCard
import com.darshan.splitsmart.ui.components.InitialAvatar
import com.darshan.splitsmart.ui.components.SectionHeader
import com.darshan.splitsmart.ui.components.appIcon
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.formatDate
import com.darshan.splitsmart.ui.components.label

@Composable
fun BillDetailRoute(viewModel: BillDetailViewModel = hiltViewModel(), onBack: () -> Unit, onSplit: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BillDetailEvent.Deleted -> onBack()
                BillDetailEvent.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }
    BillDetailScreen(state, snackbar, onBack = onBack, onDelete = viewModel::deleteBill, onRetry = viewModel::retry, onSplit = { onSplit(viewModel.billId) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(state: BillDetailUiState, snackbar: SnackbarHostState, onBack: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit, onSplit: () -> Unit = {}) {
    val content = state.bill as? LoadState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.data?.bill?.title ?: stringResource(R.string.bill_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    if (content != null) {
                        IconButton(onClick = onDelete, enabled = !state.deleting, modifier = Modifier.testTag(SplitSmartTestTags.DELETE_BILL_BUTTON)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bill_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val bill = state.bill) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Empty -> EmptyView(stringResource(R.string.error_not_found), Modifier.padding(padding))
            is LoadState.Error -> ErrorView(bill.reason, onRetry, Modifier.padding(padding))
            is LoadState.Content -> BillDetailContent(bill.data, onSplit, Modifier.padding(padding))
        }
    }
}

@Composable
private fun BillDetailContent(detail: BillDetail, onSplit: () -> Unit, modifier: Modifier) {
    val bill = detail.bill
    val onHero = SplitSmartTheme.colors.onHero
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "hero") {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDate(bill.createdAt)} · ${bill.status.label()}", style = MaterialTheme.typography.labelLarge,
                        color = onHero.copy(alpha = 0.9f), modifier = Modifier.weight(1f),
                    )
                    if (bill.source == BillSource.SCAN_LLM) AiBadge(label = stringResource(R.string.badge_ai_read))
                }
                Text(Money.formatInr(bill.totalPaise), style = MaterialTheme.typography.displaySmall, color = onHero)
                Text(
                    stringResource(R.string.bill_paid_by_value, detail.payerName ?: stringResource(R.string.bill_payer_unknown)) + " · " + bill.source.label(),
                    style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.9f),
                )
                Button(
                    onClick = onSplit,
                    colors = ButtonDefaults.buttonColors(containerColor = onHero, contentColor = SplitSmartTheme.colors.heroAction),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag(SplitSmartTestTags.SPLIT_BUTTON),
                ) {
                    Icon(appIcon(AppIcons.Split), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (bill.split == null) R.string.bill_split_button else R.string.bill_split_edit_button))
                }
            }
        }
        bill.split?.let { split ->
            item(key = "sharesHeader") { SectionHeader(stringResource(R.string.bill_shares_title)) }
            items(split.shares, key = { "s" + it.memberId }) { share ->
                val name = detail.members.find { it.id == share.memberId }?.name ?: stringResource(R.string.member_unknown)
                ContentCard(Modifier.testTag(SplitSmartTestTags.SHARE_ROW), padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(name, size = 36.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.bill_share_breakdown, Money.formatInr(share.itemsPaise), Money.formatInr(share.taxPaise), Money.formatInr(share.tipPaise)) +
                                    if (share.adjustmentPaise != 0L) " · " + stringResource(R.string.bill_share_adjustment, Money.formatInr(share.adjustmentPaise)) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.formatInr(share.totalPaise), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (bill.items.isEmpty()) {
            item(key = "noItems") { Text(stringResource(R.string.bill_no_items), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item(key = "items") {
                ContentCard {
                    Text(stringResource(R.string.bill_items_title), style = MaterialTheme.typography.titleMedium)
                    bill.items.forEachIndexed { i, item ->
                        key(item.id) {
                            BillItemRow(item)
                            if (i < bill.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (bill.taxPaise > 0 || bill.tipPaise > 0) HorizontalDivider()
                    if (bill.taxPaise > 0) AmountRow(stringResource(R.string.review_tax_label), Money.formatInr(bill.taxPaise))
                    if (bill.tipPaise > 0) AmountRow(stringResource(R.string.review_tip_label), Money.formatInr(bill.tipPaise))
                }
            }
        }
    }
}

@Composable
fun BillItemRow(item: BillItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(SplitSmartTestTags.BILL_ITEM_ROW), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            if (item.qty > 1 || item.unitPaise > 0) {
                Text(stringResource(R.string.bill_item_qty, item.qty, Money.formatInr(item.unitPaise)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(Money.formatInr(item.totalPaise), style = MaterialTheme.typography.bodyLarge)
    }
}
