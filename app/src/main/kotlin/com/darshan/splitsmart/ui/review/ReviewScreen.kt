package com.darshan.splitsmart.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.label

@Composable
fun ReviewRoute(viewModel: ReviewViewModel = hiltViewModel(), onBack: () -> Unit, onSaved: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.error_unknown)
    val defaultTitle = stringResource(R.string.review_default_title)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.Saved -> onSaved(event.billId)
                ReviewEvent.Failed -> snackbar.showSnackbar(failedText)
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(state: ReviewUiState, snackbar: SnackbarHostState, callbacks: ReviewCallbacks) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { IconButton(onClick = callbacks.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.form != null && (state.phase == ReviewPhase.EDITING || state.phase == ReviewPhase.SAVING)) {
                Button(
                    onClick = callbacks.onSave, enabled = state.phase == ReviewPhase.EDITING,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(SplitSmartTestTags.REVIEW_SAVE),
                ) { Text(stringResource(R.string.review_save)) }
            }
        },
    ) { padding ->
        when (state.phase) {
            ReviewPhase.LOADING -> LoadingView(Modifier.padding(padding))
            ReviewPhase.EXTRACTING -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Text(stringResource(R.string.review_extracting), modifier = Modifier.padding(top = 16.dp))
            }
            ReviewPhase.ERROR -> ErrorView(state.error ?: ErrorReason.UNKNOWN, callbacks.onRetry, Modifier.padding(padding))
            ReviewPhase.EDITING, ReviewPhase.SAVING -> state.form?.let { ReviewForm(state, it, callbacks, Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun ReviewForm(state: ReviewUiState, form: ReviewForm, callbacks: ReviewCallbacks, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = {
                Text(
                    stringResource(
                        when {
                            form.source == ExtractionSource.LLM -> R.string.review_source_llm
                            state.refining -> R.string.review_source_draft
                            else -> R.string.review_source_regex
                        },
                    ),
                )
            }, modifier = Modifier.testTag(SplitSmartTestTags.REVIEW_SOURCE))
            if (form.warnings.isNotEmpty()) Text(stringResource(R.string.review_warnings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            state.refining -> RefiningBanner()
            form.source == ExtractionSource.REGEX -> FallbackBanner(form.llmFailure, form.retryAfterSeconds, callbacks.onRerun)
        }
        state.pendingAi?.let { AiReadyCard(it, callbacks) }
        OutlinedTextField(
            value = form.title, onValueChange = callbacks.onTitle, singleLine = true,
            label = { Text(stringResource(R.string.review_title_label)) }, placeholder = { Text(stringResource(R.string.review_default_title)) },
            isError = state.errors.title != null, supportingText = state.errors.title?.let { { Text(it.label()) } },
            modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.REVIEW_TITLE),
        )
        if (state.members.isNotEmpty()) {
            PayerChips(state.members, form.payerId, callbacks.onPayer)
        }
        Text(stringResource(R.string.bill_items_title), style = MaterialTheme.typography.titleMedium)
        form.items.forEach { row -> key(row.key) { ItemEditor(row, state.errors.items[row.key].orEmpty(), callbacks) } }
        TextButton(onClick = callbacks.onAddItem, modifier = Modifier.testTag(SplitSmartTestTags.REVIEW_ADD_ITEM)) { Text(stringResource(R.string.review_add_item)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField(form.tax, callbacks.onTax, stringResource(R.string.review_tax), state.errors.tax, Modifier.weight(1f).testTag(SplitSmartTestTags.REVIEW_TAX))
            AmountField(form.tip, callbacks.onTip, stringResource(R.string.review_tip), state.errors.tip, Modifier.weight(1f).testTag(SplitSmartTestTags.REVIEW_TIP))
        }
        AmountField(form.total, callbacks.onTotal, stringResource(R.string.review_total), state.errors.total, Modifier.fillMaxWidth().testTag(SplitSmartTestTags.REVIEW_TOTAL))
        state.subtotalPaise?.let { Text(stringResource(R.string.review_subtotal, Money.formatInr(it)), style = MaterialTheme.typography.bodySmall) }
        val mismatch = state.mismatchPaise
        if (mismatch != null && mismatch != 0L) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.MISMATCH_BANNER)) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.review_mismatch, Money.formatInr(mismatch)), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = callbacks.onUseComputedTotal, modifier = Modifier.testTag(SplitSmartTestTags.USE_COMPUTED_TOTAL)) { Text(stringResource(R.string.review_use_computed)) }
                }
            }
        }
    }
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
private fun RefiningBanner() {
    Card(modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.REFINING_BANNER)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_refining), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AiReadyCard(pending: ReviewForm, callbacks: ReviewCallbacks) {
    val total = Money.parseInrToPaise(pending.total)?.let { Money.formatInr(it) } ?: pending.total
    val items = androidx.compose.ui.res.pluralStringResource(R.plurals.item_count, pending.items.size, pending.items.size)
    Card(modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.AI_READY_CARD)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.review_ai_ready, items, total), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = callbacks.onApplyAi, modifier = Modifier.testTag(SplitSmartTestTags.AI_APPLY)) { Text(stringResource(R.string.review_ai_apply)) }
                TextButton(onClick = callbacks.onDismissAi) { Text(stringResource(R.string.review_ai_keep)) }
            }
        }
    }
}

@Composable
private fun FallbackBanner(failure: LlmFailure?, retryAfterSeconds: Long?, onRerun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.FALLBACK_BANNER)) {
        Column(Modifier.padding(12.dp)) {
            Text(fallbackMessage(failure, retryAfterSeconds), style = MaterialTheme.typography.bodyMedium)
            if (failure != LlmFailure.KEY_MISSING && failure != LlmFailure.UNAUTHORIZED) {
                TextButton(onClick = onRerun, modifier = Modifier.testTag(SplitSmartTestTags.REVIEW_RERUN)) { Text(stringResource(R.string.review_rerun)) }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayerChips(members: List<Member>, payerId: String?, onPayer: (String?) -> Unit) {
    Column {
        Text(stringResource(R.string.bill_paid_by), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { m -> FilterChip(selected = payerId == m.id, onClick = { onPayer(if (payerId == m.id) null else m.id) }, label = { Text(m.name) }) }
        }
    }
}

@Composable
private fun ItemEditor(row: ItemRow, errors: Set<ItemError>, callbacks: ReviewCallbacks) {
    Row(Modifier.fillMaxWidth().testTag(SplitSmartTestTags.REVIEW_ITEM), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = row.name, onValueChange = { callbacks.onItemName(row.key, it) }, singleLine = true,
            label = { Text(stringResource(R.string.review_item_name)) }, isError = ItemError.NAME in errors,
            supportingText = if (ItemError.NAME in errors) { { Text(stringResource(R.string.error_name_blank)) } } else null,
            modifier = Modifier.weight(2f).testTag(SplitSmartTestTags.REVIEW_ITEM_NAME),
        )
        OutlinedTextField(
            value = row.qty, onValueChange = { callbacks.onItemQty(row.key, it) }, singleLine = true,
            label = { Text(stringResource(R.string.review_item_qty)) }, isError = ItemError.QTY in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(64.dp).testTag(SplitSmartTestTags.REVIEW_ITEM_QTY),
        )
        OutlinedTextField(
            value = row.amount, onValueChange = { callbacks.onItemAmount(row.key, it) }, singleLine = true,
            label = { Text(stringResource(R.string.review_item_amount)) }, isError = ItemError.AMOUNT in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.3f).testTag(SplitSmartTestTags.REVIEW_ITEM_AMOUNT),
        )
        IconButton(onClick = { callbacks.onRemoveItem(row.key) }, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.review_remove_item, row.name))
        }
    }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) }, isError = isError,
        supportingText = if (isError) { { Text(stringResource(R.string.error_amount_invalid)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}
