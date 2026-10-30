package com.darshan.splitsmart.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.label

@Composable
fun GroupsRoute(viewModel: GroupsViewModel = hiltViewModel(), onOpenGroup: (String) -> Unit) {
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag(SplitSmartTestTags.FAB_ADD_GROUP)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.groups_add))
            }
        },
    ) { padding ->
        when (val groups = state.groups) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Empty -> EmptyView(stringResource(R.string.groups_empty_title), Modifier.padding(padding), stringResource(R.string.groups_empty_subtitle))
            is LoadState.Error -> ErrorView(groups.reason, onRetry, Modifier.padding(padding))
            is LoadState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups.data, key = { it.group.id }) { summary -> GroupCard(summary, onClick = { onOpenGroup(summary.group.id) }) }
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
}

@Composable
private fun GroupCard(summary: GroupSummary, onClick: () -> Unit) {
    val members = pluralStringResource(R.plurals.member_count, summary.memberCount, summary.memberCount)
    val bills = pluralStringResource(R.plurals.bill_count, summary.billCount, summary.billCount)
    val description = stringResource(R.string.group_card_description, summary.group.name, members, bills)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(SplitSmartTestTags.GROUP_ITEM).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.group.name, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(members, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bills, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
