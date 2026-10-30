package com.darshan.splitsmart.ui.bill

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
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
fun BillDetailRoute(viewModel: BillDetailViewModel = hiltViewModel(), onBack: () -> Unit) {
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
    BillDetailScreen(state, snackbar, onBack = onBack, onDelete = viewModel::deleteBill, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(state: BillDetailUiState, snackbar: SnackbarHostState, onBack: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit) {
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
            is LoadState.Content -> BillDetailContent(bill.data, Modifier.padding(padding))
        }
    }
}

@Composable
private fun BillDetailContent(detail: BillDetail, modifier: Modifier) {
    val bill = detail.bill
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Money.formatInr(bill.totalPaise), style = MaterialTheme.typography.headlineMedium)
                    Text("${formatDate(bill.createdAt)} · ${bill.status.label()} · ${bill.source.label()}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.bill_paid_by_value, detail.payerName ?: stringResource(R.string.bill_payer_unknown)), style = MaterialTheme.typography.bodyMedium)
                    if (bill.taxPaise > 0) Text(stringResource(R.string.bill_tax, Money.formatInr(bill.taxPaise)), style = MaterialTheme.typography.bodyMedium)
                    if (bill.tipPaise > 0) Text(stringResource(R.string.bill_tip, Money.formatInr(bill.tipPaise)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (bill.items.isEmpty()) {
            item { Text(stringResource(R.string.bill_no_items), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { Text(stringResource(R.string.bill_items_title), style = MaterialTheme.typography.titleMedium) }
            items(bill.items, key = { it.id }) { item -> BillItemRow(item); HorizontalDivider() }
        }
    }
}

@Composable
fun BillItemRow(item: BillItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(SplitSmartTestTags.BILL_ITEM_ROW)) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            if (item.qty > 1 || item.unitPaise > 0) {
                Text(stringResource(R.string.bill_item_qty, item.qty, Money.formatInr(item.unitPaise)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(Money.formatInr(item.totalPaise), style = MaterialTheme.typography.bodyLarge)
    }
}
