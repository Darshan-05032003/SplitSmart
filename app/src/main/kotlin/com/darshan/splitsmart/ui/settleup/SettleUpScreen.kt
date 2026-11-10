package com.darshan.splitsmart.ui.settleup

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.data.upi.ZxingQrCodeGenerator
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ConfettiBurst
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmptyState
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.components.rememberAmbientPhase
import com.darshan.splitsmart.ui.designsystem.effects.CollectUiEffects
import com.darshan.splitsmart.ui.designsystem.modifiers.FilterObscuredTouches
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

@Composable
fun SettleUpRoute(
    viewModel: SettleUpViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit = {},
    onHistory: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.settle_no_upi_app)
    val shareFailed = stringResource(R.string.settle_share_failed)
    val failed = stringResource(R.string.error_unknown)
    val settledTemplate = stringResource(R.string.settled_message)
    val undo = stringResource(R.string.action_undo)
    // Back from the UPI app (or anything else): the moment to ask whether the payment went through.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    CollectUiEffects(viewModel.effects)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettleUpEvent.NoUpiApp -> scope.launch { snackbar.show(noApp, emoji = "📱") }
                SettleUpEvent.ShareFailed -> scope.launch { snackbar.show(shareFailed, emoji = "😕") }
                SettleUpEvent.Failed -> scope.launch { snackbar.show(failed, emoji = "😕") }
                is SettleUpEvent.Recorded -> scope.launch {
                    val text = String.format(settledTemplate, DisplayText.safe(event.fromName), DisplayText.safe(event.toName), AmountFormat.format(event.amountPaise))
                    if (snackbar.show(text, emoji = "🎉", actionLabel = undo) == SnackbarResult.Undo) viewModel.undo(event.settlementId)
                }
            }
        }
    }
    val group = (state.content as? LoadState.Content)?.data?.group?.name ?: ""
    val withLink = stringResource(R.string.settle_share_text)
    val withoutLink = stringResource(R.string.settle_share_text_no_link)
    SettleUpScreen(
        state = state,
        snackbar = snackbar,
        callbacks = SettleUpCallbacks(
            onBack = onBack,
            onPay = viewModel::pay,
            onShare = { item ->
                val amount = AmountFormat.format(item.debt.amountPaise)
                val text = item.link?.let { String.format(withLink, item.payer.name, item.payee.name, amount, group, it.uri) }
                    ?: String.format(withoutLink, item.payer.name, item.payee.name, amount, group)
                viewModel.share(text, item)
            },
            onShowQr = viewModel::showQr,
            onDismissQr = viewModel::dismissQr,
            onAddVpa = viewModel::editVpa,
            onVpaChange = viewModel::onVpaChange,
            onSaveVpa = viewModel::saveVpa,
            onDismissVpa = viewModel::dismissVpa,
            onMarkPaid = viewModel::markPaid,
            onConfirmPaid = viewModel::confirmPaid,
            onNotYet = viewModel::notYet,
            onOpenBill = onOpenBill,
            onHistory = { onHistory(viewModel.groupId) },
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
    val onMarkPaid: (SettleUpItem) -> Unit = {},
    val onConfirmPaid: () -> Unit = {},
    val onNotYet: () -> Unit = {},
    val onOpenBill: (String) -> Unit = {},
    val onHistory: () -> Unit = {},
)

/**
 * Settle up on Marigold. An aubergine hero with what is left to settle; one card per payment, largest first, with Pay (opens the
 * UPI app with the amount filled in), QR, share and "Paid another way"; the card being settled turns into the question that
 * records it; the payment just marked wears a stamp; bills that can't count yet are named with a way to fix them. All square
 * swaps the hero for the bloom. Overlays and obscured touches are refused while this screen is up.
 */
@Composable
fun SettleUpScreen(state: SettleUpUiState, snackbar: SsSnackbarState, callbacks: SettleUpCallbacks) {
    FilterObscuredTouches()
    val colors = SplitSmartTheme.colors
    val content = (state.content as? LoadState.Content)?.data
    val sheetOpen = state.qrItem != null || state.vpaDraft != null
    // Delight once per payment: not again after a rotation, never for a group that was already square when opened.
    val moment = state.settled
    var celebrated by rememberSaveable { mutableStateOf<String?>(null) }
    val fresh = remember(moment?.settlementId) { moment != null && moment.settlementId != celebrated }
    LaunchedEffect(moment?.settlementId) { if (moment != null) celebrated = moment.settlementId }
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().then(if (sheetOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
            SsTopBar(
                title = stringResource(R.string.settle_title),
                subtitle = content?.group?.name?.let(DisplayText::safe),
                onBack = callbacks.onBack,
                backLabel = stringResource(R.string.action_back),
                actions = {
                    if (content != null) TextAction(stringResource(R.string.settle_history), callbacks.onHistory, Modifier.testTag(SettleUpTags.HISTORY))
                },
            )
            when (val c = state.content) {
                LoadState.Loading -> SettleLoading()
                LoadState.Empty -> EmptyState(
                    IllustrationKind.Bills,
                    stringResource(R.string.settle_empty),
                    stringResource(R.string.settle_empty_subtitle),
                )
                is LoadState.Error -> ErrorState(stringResource(R.string.settle_error_title), stringResource(c.reason.messageRes()), onRetry = callbacks.onBack, retryLabel = stringResource(R.string.action_back))
                is LoadState.Content -> SettleUpList(c.data, state, fresh, callbacks)
            }
        }
        ConfettiBurst(if (fresh) moment?.settlementId else null, Modifier.fillMaxSize())
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        QrSheet(state.qrItem, callbacks.onDismissQr)
        VpaSheet(state.vpaDraft, content?.items?.firstOrNull { it.payee.id == state.vpaDraft?.memberId }?.payee?.name, callbacks)
    }
}

@Composable
private fun SettleUpList(content: SettleUpContent, state: SettleUpUiState, fresh: Boolean, callbacks: SettleUpCallbacks) {
    val motion = LocalMotion.current
    val check = state.check
    val moment = state.settled
    val shine = rememberAmbientPhase(MotionDurations.Shine)
    val gutter = Modifier.padding(horizontal = Spacing.screen)
    LazyColumn(Modifier.fillMaxSize().testTag(SettleUpTags.LIST), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(key = "hero", contentType = "hero") {
            if (content.allSquare) {
                AllSquareHero(content.group.name, playStamp = fresh && moment?.allSquare == true, onHistory = callbacks.onHistory, modifier = gutter.padding(vertical = Spacing.xxs))
            } else {
                SettleHero(content, gutter.padding(vertical = Spacing.xxs))
            }
        }
        if (moment != null && !moment.allSquare) {
            item(key = "settled:" + moment.settlementId, contentType = "settled") {
                SettledCard(moment, playStamp = fresh, modifier = gutter.animateItem())
            }
        }
        if (check != null && content.items.none { it.key == check.key }) {
            item(key = "check", contentType = "check") {
                CheckCard(check, state.recording, callbacks.onConfirmPaid, callbacks.onNotYet, gutter.animateItem())
            }
        }
        itemsIndexed(content.items, key = { _, item -> item.key }, contentType = { _, _ -> "payment" }) { index, item ->
            val asking = check?.key == item.key
            AnimatedContent(asking, gutter.animateItem(), transitionSpec = { motion.fadeThrough() }, label = "payment") { checking ->
                if (checking && check != null) {
                    CheckCard(check, state.recording, callbacks.onConfirmPaid, callbacks.onNotYet)
                } else {
                    PaymentCard(item, primary = index == 0 && check == null, shine = shine, index = index, callbacks = callbacks)
                }
            }
        }
        if (content.toFix.isNotEmpty()) {
            item(key = "toFix", contentType = "toFix") { ToFixCard(content.toFix, callbacks.onOpenBill, gutter) }
        }
        if (content.items.isNotEmpty()) {
            item(key = "note", contentType = "note") {
                BasicText(
                    stringResource(R.string.settle_note),
                    gutter.padding(vertical = Spacing.xs),
                    style = SplitSmartTheme.type.label.copy(color = SplitSmartTheme.colors.textSecondary, textAlign = TextAlign.Center),
                )
            }
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.navigationBarsPadding().height(Spacing.giant)) }
    }
}

@Composable
private fun SettleLoading() {
    val label = stringResource(R.string.settle_loading)
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SkeletonGroup { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.heroSkeleton), Radii.largeShape) }
        SkeletonList(3, label = label)
    }
}

/** Show this to the person paying: their UPI app reads the amount and the payee from it. */
@Composable
private fun QrSheet(item: SettleUpItem?, onDismiss: () -> Unit) {
    // Kept while the sheet slides away, so its content does not vanish mid-exit.
    var shown by remember { mutableStateOf(item) }
    if (item != null) shown = item
    SsBottomSheet(visible = item != null, onDismissRequest = onDismiss, modifier = Modifier.testTag(SettleUpTags.QR_SHEET)) {
        val current = shown ?: return@SsBottomSheet
        val link = current.link ?: return@SsBottomSheet
        val type = SplitSmartTheme.type
        val colors = SplitSmartTheme.colors
        val payee = DisplayText.safe(current.payee.name)
        val qr = remember(link.uri) { runCatching { ZxingQrCodeGenerator().generate(link.uri) }.getOrNull() }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BasicText(stringResource(R.string.settle_qr_title, payee), style = type.title.copy(color = colors.text, textAlign = TextAlign.Center))
            if (qr != null) {
                QrCode(qr, stringResource(R.string.settle_qr_description, payee, AmountFormat.format(current.debt.amountPaise)))
            }
            AmountText(current.debt.amountPaise, style = type.display, color = colors.text)
            BasicText(link.payeeVpa, style = type.label.copy(color = colors.textSecondary))
            BasicText(stringResource(R.string.settle_qr_hint, DisplayText.safe(current.payer.name), payee), style = type.label.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
            ChunkyButton(stringResource(R.string.settle_qr_dismiss), onDismiss, Modifier.fillMaxWidth().padding(vertical = Spacing.xs), style = ButtonStyle.Secondary)
        }
    }
}

@Composable
private fun VpaSheet(draft: VpaDraft?, payeeName: String?, callbacks: SettleUpCallbacks) {
    var shown by remember { mutableStateOf(draft) }
    if (draft != null) shown = draft
    SsBottomSheet(visible = draft != null, onDismissRequest = callbacks.onDismissVpa, modifier = Modifier.testTag(SettleUpTags.VPA_SHEET)) {
        val current = shown ?: return@SsBottomSheet
        val type = SplitSmartTheme.type
        val colors = SplitSmartTheme.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BasicText(
                payeeName?.let { stringResource(R.string.settle_add_vpa_for, DisplayText.safe(it)) } ?: stringResource(R.string.settle_add_vpa),
                style = type.title.copy(color = colors.text),
            )
            SsTextField(
                value = current.text,
                onValueChange = callbacks.onVpaChange,
                label = stringResource(R.string.member_vpa_label),
                modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.MEMBER_VPA_FIELD),
                error = current.error?.label(),
                supporting = stringResource(R.string.member_vpa_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ChunkyButton(
                stringResource(R.string.action_add),
                callbacks.onSaveVpa,
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag(SplitSmartTestTags.DIALOG_CONFIRM),
                loading = current.saving,
            )
        }
    }
}
