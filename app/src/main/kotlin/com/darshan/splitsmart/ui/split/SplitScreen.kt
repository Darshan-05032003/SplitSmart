package com.darshan.splitsmart.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.AiBadge
import com.darshan.splitsmart.ui.components.BalanceChip
import com.darshan.splitsmart.ui.components.BalanceTone
import com.darshan.splitsmart.ui.components.BannerTone
import com.darshan.splitsmart.ui.components.ContentCard
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.InfoBanner
import com.darshan.splitsmart.ui.components.InitialAvatar
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
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Button(
                        onClick = callbacks.onSave, enabled = editor.preview != null && !state.saving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag(SplitSmartTestTags.SPLIT_SAVE),
                    ) { Text(stringResource(R.string.split_save)) }
                }
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(editor.bill.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (editor.bill.source == BillSource.SCAN_LLM) { Spacer(Modifier.width(6.dp)); AiBadge() }
                    }
                    Text(
                        pluralStringResource(R.plurals.item_count, editor.bill.items.size, editor.bill.items.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.split_bill_total, Money.formatInr(editor.bill.totalPaise)), style = MaterialTheme.typography.titleLarge)
            }
        }

        SplitLabel(stringResource(R.string.split_mode_label))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(
                Triple(SplitMode.EQUAL, R.string.split_mode_equal, SplitSmartTestTags.SPLIT_MODE_EQUAL),
                Triple(SplitMode.BY_ITEM, R.string.split_mode_by_item, SplitSmartTestTags.SPLIT_MODE_BY_ITEM),
                Triple(SplitMode.BY_SHARE, R.string.split_mode_by_share, SplitSmartTestTags.SPLIT_MODE_BY_SHARE),
            )
            modes.forEachIndexed { index, (mode, label, tag) ->
                SegmentedButton(
                    selected = editor.config.mode == mode, onClick = { callbacks.onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size), modifier = Modifier.testTag(tag),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }

        SplitLabel(stringResource(R.string.split_paid_by))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.members.forEach { m ->
                FilterChip(
                    selected = editor.payerId == m.id, onClick = { callbacks.onPayer(if (editor.payerId == m.id) null else m.id) }, label = { Text(m.name) },
                    leadingIcon = { InitialAvatar(m.name, size = 22.dp) }, modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_PAYER_CHIP),
                )
            }
        }

        SplitLabel(stringResource(R.string.split_participants))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.members.forEach { m ->
                val selected = m.id in editor.config.participantIds
                FilterChip(
                    selected = selected, onClick = { callbacks.onToggleParticipant(m.id) }, label = { Text(m.name) },
                    leadingIcon = if (selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
                    modifier = Modifier.testTag(SplitSmartTestTags.SPLIT_PARTICIPANT),
                )
            }
        }

        if (editor.config.mode == SplitMode.BY_ITEM) {
            SplitLabel(stringResource(R.string.split_items_title))
            Text(stringResource(R.string.split_assign_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            editor.bill.items.forEach { item ->
                key(item.id) {
                    ContentCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Money.formatInr(item.totalPaise), style = MaterialTheme.typography.titleSmall)
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
            SplitLabel(stringResource(R.string.split_shares_label))
            ContentCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                editor.config.participantIds.forEach { id ->
                    key(id) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(memberName(id), size = 32.dp)
                            Text(memberName(id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
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
        }

        SplitLabel(stringResource(R.string.split_preview_title))
        editor.error?.let {
            InfoBanner(BannerTone.ERROR, icon = rememberVectorPainter(Icons.Default.Warning)) { Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodyMedium) }
        }
        editor.preview?.let { preview ->
            preview.shares.forEach { share -> PersonShareCard(memberName(share.memberId), share, isPayer = share.memberId == editor.payerId) }
            val exact = preview.totalPaise == editor.bill.totalPaise
            InfoBanner(if (exact) BannerTone.SUCCESS else BannerTone.WARNING, icon = rememberVectorPainter(if (exact) Icons.Default.CheckCircle else Icons.Default.Warning)) {
                Text(
                    if (exact) stringResource(R.string.split_total_check_ok, Money.formatInr(preview.totalPaise))
                    else stringResource(R.string.split_total_check_off, Money.formatInr(preview.totalPaise), Money.formatInr(editor.bill.totalPaise)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SplitLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PersonShareCard(name: String, share: MemberShare, isPayer: Boolean) {
    ContentCard(Modifier.testTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(name, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    if (isPayer) { Spacer(Modifier.width(6.dp)); BalanceChip(stringResource(R.string.split_paid_badge), BalanceTone.OWED) }
                }
                Text(
                    stringResource(R.string.bill_share_breakdown, Money.formatInr(share.itemsPaise), Money.formatInr(share.taxPaise), Money.formatInr(share.tipPaise)) +
                        if (share.adjustmentPaise != 0L) " · " + stringResource(R.string.bill_share_adjustment, Money.formatInr(share.adjustmentPaise)) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.formatInr(share.totalPaise), style = MaterialTheme.typography.titleLarge)
        }
    }
}

fun SplitError.messageRes(): Int = when (this) {
    SplitError.NO_PARTICIPANTS -> R.string.split_error_no_participants
    SplitError.NO_SHARES -> R.string.split_error_no_shares
    SplitError.NEGATIVE_SHARE -> R.string.split_error_negative_share
    SplitError.DUPLICATE_PARTICIPANT -> R.string.split_error_duplicate
}
