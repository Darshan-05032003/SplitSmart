package com.darshan.splitsmart.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.AppIcons
import com.darshan.splitsmart.ui.components.AvatarStack
import com.darshan.splitsmart.ui.components.BalanceChip
import com.darshan.splitsmart.ui.components.BalanceTone
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.GroupAvatar
import com.darshan.splitsmart.ui.components.HeroCard
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.SectionHeader
import com.darshan.splitsmart.ui.components.appIcon
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.components.relativeDate
import com.darshan.splitsmart.ui.components.yourBalanceLabel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

@Composable
fun GroupsRoute(viewModel: GroupsViewModel = hiltViewModel(), onOpenGroup: (String) -> Unit, onScanReceipt: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupsEvent.GroupCreated -> Unit
                GroupsEvent.CreateFailed -> snackbar.showSnackbar(failedText)
            }
        }
    }
    GroupsScreen(
        state = state,
        snackbar = snackbar,
        onOpenGroup = onOpenGroup,
        onAdd = viewModel::openCreateDialog,
        onDismiss = viewModel::dismissDialog,
        onNameChange = viewModel::onDraftNameChange,
        onConfirm = viewModel::submitCreate,
        onRetry = viewModel::retry,
        profile = ProfileCallbacks(
            onEdit = { viewModel.openNameDialog(state.overview.yourName) },
            onChange = viewModel::onNameDraftChange,
            onSave = viewModel::saveName,
            onDismiss = viewModel::dismissNameDialog,
        ),
        onScanReceipt = onScanReceipt,
    )
}

data class ProfileCallbacks(
    val onEdit: () -> Unit = {},
    val onChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Composable
fun GroupsScreen(
    state: GroupsUiState,
    snackbar: SnackbarHostState,
    onOpenGroup: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    profile: ProfileCallbacks = ProfileCallbacks(),
    onScanReceipt: (String) -> Unit = {},
) {
    var pickingGroup by rememberSaveable { mutableStateOf(false) }
    val cards = (state.groups as? LoadState.Content)?.data.orEmpty()
    val scan = {
        when (cards.size) {
            0 -> onAdd()
            1 -> onScanReceipt(cards.single().group.id)
            else -> pickingGroup = true
        }
    }
    val fabDescription = stringResource(R.string.groups_add)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                text = { Text(stringResource(R.string.groups_new_group)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.testTag(SplitSmartTestTags.FAB_ADD_GROUP).semantics { contentDescription = fabDescription },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrandHeader(onEditName = profile.onEdit)
            when (val groups = state.groups) {
                LoadState.Loading -> LoadingView()
                LoadState.Empty -> EmptyView(
                    title = stringResource(R.string.groups_empty_title),
                    subtitle = stringResource(R.string.groups_empty_subtitle),
                    illustration = appIcon(AppIcons.Receipt),
                    action = { Button(onClick = onAdd) { Text(stringResource(R.string.groups_empty_action)) } },
                )
                is LoadState.Error -> ErrorView(groups.reason, onRetry)
                is LoadState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "hero") { BalanceHero(state.overview, onScan = scan, onEditName = profile.onEdit) }
                    item(key = "header") { SectionHeader(stringResource(R.string.groups_your_groups)) }
                    items(groups.data, key = { it.group.id }) { card -> GroupCardView(card, onClick = { onOpenGroup(card.group.id) }) }
                }
            }
        }
    }
    state.draft?.let { draft ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.groups_new_title)) },
            text = {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    isError = draft.error != null,
                    label = { Text(stringResource(R.string.groups_name_label)) },
                    placeholder = { Text(stringResource(R.string.groups_name_placeholder)) },
                    supportingText = draft.error?.let { { Text(it.label()) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GROUP_NAME_FIELD),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !draft.submitting, modifier = Modifier.testTag(SplitSmartTestTags.DIALOG_CONFIRM)) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.nameDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = profile.onDismiss,
            title = { Text(stringResource(R.string.profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = draft.name, onValueChange = profile.onChange, singleLine = true, isError = draft.error != null,
                        label = { Text(stringResource(R.string.member_name_label)) },
                        supportingText = draft.error?.let { { Text(it.label()) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { profile.onSave() }),
                        modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.YOUR_NAME_FIELD),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = profile.onSave, modifier = Modifier.testTag(SplitSmartTestTags.DIALOG_CONFIRM)) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = profile.onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pickingGroup) {
        AlertDialog(
            onDismissRequest = { pickingGroup = false },
            title = { Text(stringResource(R.string.groups_pick_for_scan)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cards.forEach { card ->
                        Row(
                            Modifier.fillMaxWidth().clickable { pickingGroup = false; onScanReceipt(card.group.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupAvatar(card.group.name, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(card.group.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingGroup = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BrandHeader(onEditName: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.small, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(appIcon(AppIcons.Split), contentDescription = null, modifier = Modifier.size(26.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEditName, modifier = Modifier.testTag(SplitSmartTestTags.PROFILE_BUTTON)) {
            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.profile_title))
        }
    }
}

@Composable
private fun BalanceHero(overview: HomeOverview, onScan: () -> Unit, onEditName: () -> Unit) {
    val onHero = SplitSmartTheme.colors.onHero
    HeroCard(Modifier.testTag(SplitSmartTestTags.HOME_BALANCE)) {
        val name = overview.yourName
        when {
            name == null || overview.groupsWithYou == 0 -> {
                Text(
                    pluralStringResource(R.plurals.home_to_settle_across, overview.groupCount, overview.groupCount),
                    style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.9f),
                )
                if (overview.outstandingPaise > 0) {
                    Text(Money.formatInr(overview.outstandingPaise), style = MaterialTheme.typography.displaySmall, color = onHero)
                } else {
                    Text(stringResource(R.string.home_nothing_to_settle), style = MaterialTheme.typography.headlineMedium, color = onHero)
                }
                Surface(
                    onClick = onEditName, color = onHero.copy(alpha = 0.18f), contentColor = onHero, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(SplitSmartTestTags.SET_NAME_PROMPT),
                ) {
                    Text(
                        if (name == null) stringResource(R.string.home_set_name) else stringResource(R.string.home_name_not_found, name),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            overview.youAreOwedPaise > 0 && overview.youOwePaise > 0 -> {
                Text(stringResource(R.string.home_your_balance, name), style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.9f))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HeroFigure(stringResource(R.string.home_you_are_owed), overview.youAreOwedPaise)
                    HeroFigure(stringResource(R.string.home_you_owe), overview.youOwePaise)
                }
            }
            else -> {
                Text(stringResource(R.string.home_your_balance, name), style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.9f))
                val (label, amount) = when {
                    overview.youAreOwedPaise > 0 -> stringResource(R.string.home_you_are_owed) to overview.youAreOwedPaise
                    overview.youOwePaise > 0 -> stringResource(R.string.home_you_owe) to overview.youOwePaise
                    else -> stringResource(R.string.home_all_settled) to 0L
                }
                HeroFigure(label, amount, big = true)
            }
        }
        Spacer(Modifier.size(6.dp))
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = onHero, contentColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.HOME_SCAN),
        ) {
            Icon(appIcon(AppIcons.Camera), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_receipt))
        }
    }
}

@Composable
private fun HeroFigure(label: String, paise: Long, big: Boolean = false) {
    val onHero = SplitSmartTheme.colors.onHero
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.9f))
        if (paise > 0 || big) {
            Text(Money.formatInr(paise), style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = onHero)
        }
    }
}

@Composable
private fun GroupCardView(card: GroupCard, onClick: () -> Unit) {
    val members = pluralStringResource(R.plurals.member_count, card.summary.memberCount, card.summary.memberCount)
    val bills = pluralStringResource(R.plurals.bill_count, card.summary.billCount, card.summary.billCount)
    val description = stringResource(R.string.group_card_description, card.group.name, members, bills)
    val (chipText, tone) = when {
        card.yourBalancePaise != null -> yourBalanceLabel(card.yourBalancePaise)
        card.outstandingPaise > 0 -> stringResource(R.string.balance_to_settle, Money.formatInr(card.outstandingPaise)) to BalanceTone.NEUTRAL
        card.summary.billCount == 0 -> stringResource(R.string.balance_no_bills) to BalanceTone.NEUTRAL
        else -> stringResource(R.string.balance_settled) to BalanceTone.NEUTRAL
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GROUP_ITEM).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            GroupAvatar(card.group.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.group_card_meta, members, bills, relativeDate(card.lastActivity)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (card.memberNames.isNotEmpty()) AvatarStack(card.memberNames, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    BalanceChip(chipText, tone)
                }
            }
        }
    }
}
