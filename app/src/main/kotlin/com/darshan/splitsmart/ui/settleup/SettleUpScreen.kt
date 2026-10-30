package com.darshan.splitsmart.ui.settleup

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.data.upi.QrMatrix
import com.darshan.splitsmart.data.upi.ZxingQrCodeGenerator
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.EmptyView
import com.darshan.splitsmart.ui.components.ErrorView
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.label

@Composable
fun SettleUpRoute(viewModel: SettleUpViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val noApp = stringResource(R.string.settle_no_upi_app)
    val shareFailed = stringResource(R.string.settle_share_failed)
    val failed = stringResource(R.string.error_unknown)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    SettleUpEvent.NoUpiApp -> noApp
                    SettleUpEvent.ShareFailed -> shareFailed
                    SettleUpEvent.Failed -> failed
                },
            )
        }
    }
    val group = (state.content as? LoadState.Content)?.data?.group?.name ?: ""
    val withLink = stringResource(R.string.settle_share_text)
    val withoutLink = stringResource(R.string.settle_share_text_no_link)
    SettleUpScreen(
        state = state, snackbar = snackbar,
        callbacks = SettleUpCallbacks(
            onBack = onBack,
            onPay = viewModel::pay,
            onShare = { item ->
                val amount = Money.formatInr(item.debt.amountPaise)
                val text = item.link?.let { String.format(withLink, item.payer.name, item.payee.name, amount, group, it.uri) }
                    ?: String.format(withoutLink, item.payer.name, item.payee.name, amount, group)
                viewModel.share(text)
            },
            onShowQr = viewModel::showQr, onDismissQr = viewModel::dismissQr,
            onAddVpa = viewModel::editVpa, onVpaChange = viewModel::onVpaChange, onSaveVpa = viewModel::saveVpa, onDismissVpa = viewModel::dismissVpa,
        ),
    )
}

data class SettleUpCallbacks(
    val onBack: () -> Unit,
    val onPay: (SettleUpItem) -> Unit,
    val onShare: (SettleUpItem) -> Unit,
    val onShowQr: (SettleUpItem) -> Unit,
    val onDismissQr: () -> Unit,
    val onAddVpa: (String) -> Unit,
    val onVpaChange: (String) -> Unit,
    val onSaveVpa: () -> Unit,
    val onDismissVpa: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(state: SettleUpUiState, snackbar: SnackbarHostState, callbacks: SettleUpCallbacks) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settle_title)) },
                navigationIcon = { IconButton(onClick = callbacks.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val content = state.content) {
            LoadState.Loading -> LoadingView(Modifier.padding(padding))
            LoadState.Empty -> EmptyView(stringResource(R.string.settle_empty), Modifier.padding(padding), stringResource(R.string.settle_empty_subtitle))
            is LoadState.Error -> ErrorView(content.reason, onRetry = {}, Modifier.padding(padding))
            is LoadState.Content -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(content.data.items, key = { it.debt.fromMemberId + ">" + it.debt.toMemberId }) { item -> SettleUpCard(item, callbacks) }
            }
        }
    }
    state.qrItem?.let { QrDialog(it, callbacks.onDismissQr) }
    state.vpaDraft?.let { VpaDialog(it, callbacks) }
}

@Composable
private fun SettleUpCard(item: SettleUpItem, callbacks: SettleUpCallbacks) {
    Card(Modifier.fillMaxWidth().testTag(SplitSmartTestTags.SETTLE_ITEM)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.debt_row, item.payer.name, item.payee.name), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Money.formatInr(item.debt.amountPaise), style = MaterialTheme.typography.titleMedium)
            }
            if (item.missingVpa) {
                Text(stringResource(R.string.settle_missing_vpa, item.payee.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.link != null) {
                    Button(onClick = { callbacks.onPay(item) }, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.SETTLE_PAY)) { Text(stringResource(R.string.settle_pay)) }
                    OutlinedButton(onClick = { callbacks.onShowQr(item) }, modifier = Modifier.testTag(SplitSmartTestTags.SETTLE_QR)) { Text(stringResource(R.string.settle_qr)) }
                } else {
                    Button(onClick = { callbacks.onAddVpa(item.payee.id) }, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.SETTLE_ADD_VPA)) { Text(stringResource(R.string.settle_add_vpa)) }
                }
                OutlinedButton(onClick = { callbacks.onShare(item) }, modifier = Modifier.testTag(SplitSmartTestTags.SETTLE_SHARE)) { Text(stringResource(R.string.settle_share)) }
            }
        }
    }
}

@Composable
private fun QrDialog(item: SettleUpItem, onDismiss: () -> Unit) {
    val link = item.link ?: return
    val qr = remember(link.uri) { runCatching { ZxingQrCodeGenerator().generate(link.uri) }.getOrNull() }
    val description = stringResource(R.string.settle_qr_description, item.payee.name, Money.formatInr(item.debt.amountPaise))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_qr_title, item.payee.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (qr != null) {
                    Image(bitmap = remember(qr) { qr.toBitmap(scale = 8).asImageBitmap() }, contentDescription = description, modifier = Modifier.size(240.dp).testTag(SplitSmartTestTags.QR_IMAGE))
                }
                Text(Money.formatInr(item.debt.amountPaise), style = MaterialTheme.typography.titleLarge)
                Text(link.payeeVpa, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settle_qr_dismiss)) } },
    )
}

@Composable
private fun VpaDialog(draft: VpaDraft, callbacks: SettleUpCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissVpa,
        title = { Text(stringResource(R.string.settle_add_vpa)) },
        text = {
            OutlinedTextField(
                value = draft.text, onValueChange = callbacks.onVpaChange, singleLine = true,
                label = { Text(stringResource(R.string.member_vpa_label)) }, isError = draft.error != null,
                supportingText = draft.error?.let { { Text(it.label()) } } ?: { Text(stringResource(R.string.member_vpa_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.MEMBER_VPA_FIELD),
            )
        },
        confirmButton = { TextButton(onClick = callbacks.onSaveVpa, enabled = !draft.saving, modifier = Modifier.testTag(SplitSmartTestTags.DIALOG_CONFIRM)) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = callbacks.onDismissVpa) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Renders the module matrix to an ARGB bitmap, [scale] pixels per module (dark modules black on white). */
fun QrMatrix.toBitmap(scale: Int): Bitmap {
    val px = size * scale
    val pixels = IntArray(px * px)
    for (y in 0 until px) for (x in 0 until px) pixels[y * px + x] = if (this[x / scale, y / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}
