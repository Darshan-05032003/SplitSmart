package com.darshan.splitsmart.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView

@Composable
fun SplitRoute(viewModel: SplitViewModel = hiltViewModel(), onBack: () -> Unit, onSaved: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SplitEvent.Saved -> onSaved(event.billId)
                SplitEvent.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }
    SplitScreen(
        state = state, snackbar = snackbar,
        callbacks = SplitCallbacks(
            onBack = onBack, onRetry = viewModel::retry, onMode = viewModel::setMode, onToggleParticipant = viewModel::toggleParticipant,
            onToggleItem = viewModel::toggleItemAssignment, onShareText = viewModel::setShareText, onPayer = viewModel::setPayer, onSave = viewModel::save,
        ),
    )
}

data class SplitCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onMode: (SplitMode) -> Unit,
    val onToggleParticipant: (String) -> Unit,
    val onToggleItem: (String, String) -> Unit,
    val onShareText: (String, String) -> Unit,
    val onPayer: (String?) -> Unit,
    val onSave: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(state: SplitUiState, snackbar: SnackbarHostState, callbacks: SplitCallbacks) {
    val editor = (state.editor as? LoadState.Content)?.data
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_title)) },
                navigationIcon = { IconButton(onClick = callbacks.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (editor != null) {
                Button(
                    onClick = callbacks.onSave, enabled = editor.preview != null && !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(SplitSmartTestTags.SPLIT_SAVE),
                ) { Text(stringResource(R.string.split_save)) }
            }
        },
    ) { padding ->
        when (val e = state.editor) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Empty -> LoadingView(Modifier.padding(padding))
            is LoadState.Error -> ErrorView(e.reason, callbacks.onRetry, Modifier.padding(padding))
            is LoadState.Content -> SplitEditorContent(e.data, callbacks, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SplitEditorContent(editor: SplitEditor, callbacks: SplitCallbacks, modifier: Modifier) {
    val unknown = stringResource(R.string.member_unknown)
    val memberName = { id: String -> editor.members.find { it.id == id }?.name ?: unknown }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${editor.bill.title} · ${Money.formatInr(editor.bill.totalPaise)}", style = MaterialTheme.typography.titleMedium)

        Text(stringResource(R.string.split_mode_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(SplitMode.EQUAL, editor.config.mode, stringResource(R.string.split_mode_equal), SplitSmartTestTags.SPLIT_MODE_EQUAL, callbacks.onMode)
            ModeChip(SplitMode.BY_ITEM, editor.config.mode, stringResource(R.string.split_mode_by_item), SplitSmartTestTags.SPLIT_MODE_BY_ITEM, callbacks.onMode)
            ModeChip(SplitMode.BY_SHARE, editor.config.mode, stringResource(R.string.split_mode_by_share), SplitSmartTestTags.SPLIT_MODE_BY_SHARE, callbacks.onMode)
        }

        Text(stringResource(R.string.split_paid_by), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.members.forEach { m ->
                FilterChip(selected = editor.payerId == m.id, onClick = { callbacks.onPayer(if (editor.payerId == m.id) null else m.id) }, label = { Text(m.name) }, modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_PAYER_CHIP))
            }
        }

        Text(stringResource(R.string.split_participants), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.members.forEach { m ->
                FilterChip(selected = m.id in editor.config.participantIds, onClick = { callbacks.onToggleParticipant(m.id) }, label = { Text(m.name) }, modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_PARTICIPANT))
            }
        }

        if (editor.config.mode == SplitMode.BY_ITEM) {
            Text(stringResource(R.string.split_items_title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.split_assign_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            editor.bill.items.forEach { item ->
                key(item.id) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.name, modifier = Modifier.weight(1f))
                            Text(Money.formatInr(item.totalPaise))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            editor.config.participantIds.forEach { id ->
                                FilterChip(
                                    selected = id in editor.config.itemAssignments[item.id].orEmpty(), onClick = { callbacks.onToggleItem(item.id, id) },
                                    label = { Text(memberName(id)) }, modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_ITEM_CHIP),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editor.config.mode == SplitMode.BY_SHARE) {
            Text(stringResource(R.string.split_shares_label), style = MaterialTheme.typography.labelLarge)
            editor.config.participantIds.forEach { id ->
                key(id) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(memberName(id), modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = editor.shareTexts[id] ?: "", onValueChange = { callbacks.onShareText(id, it) }, singleLine = true,
                            label = { Text(stringResource(R.string.split_share_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp).testTag(SplitSmartTestTags.SPLIT_SHARE_FIELD),
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.split_preview_title), style = MaterialTheme.typography.labelLarge)
        editor.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        editor.preview?.let { preview ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.shares.forEach { share ->
                        Row(Modifier.fillMaxWidth().testTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW)) {
                            Text(memberName(share.memberId), modifier = Modifier.weight(1f))
                            Text(Money.formatInr(share.totalPaise), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(mode: SplitMode, current: SplitMode, label: String, tag: String, onMode: (SplitMode) -> Unit) {
    FilterChip(selected = current == mode, onClick = { onMode(mode) }, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

fun SplitError.messageRes(): Int = when (this) {
    SplitError.NO_PARTICIPANTS -> R.string.split_error_no_participants
    SplitError.NO_SHARES -> R.string.split_error_no_shares
    SplitError.NEGATIVE_SHARE -> R.string.split_error_negative_share
    SplitError.DUPLICATE_PARTICIPANT -> R.string.split_error_duplicate
}
