package com.darshan.splitsmart.ui.settleup

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.R
import com.darshan.splitsmart.data.upi.QrMatrix
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.split.BillCheck
import com.darshan.splitsmart.domain.split.BillProblem
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonSize
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.LoadingDots
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.modifiers.filterTouchesWhenObscured
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.groups.HeroPalette
import com.darshan.splitsmart.ui.theme.LocalMarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

/** Test tags of the settle-up screen that are not shared with other screens. */
object SettleUpTags {
    const val LIST = "settle_list"
    const val HERO = "settle_hero"
    const val HERO_TOTAL = "settle_hero_total"
    const val ALL_SQUARE = "settle_all_square"
    const val CHECK_CARD = "settle_check"
    const val CHECK_YES = "settle_check_yes"
    const val CHECK_NOT_YET = "settle_check_not_yet"
    const val MARK_PAID = "settle_mark_paid"
    const val SETTLED_CARD = "settle_settled"
    const val STAMP = "settle_stamp"
    const val TO_FIX = "settle_to_fix"
    const val TO_FIX_ROW = "settle_to_fix_row"
    const val HISTORY = "settle_history"
    const val SEE_HISTORY = "settle_see_history"
    const val QR_SHEET = "settle_qr_sheet"
    const val VPA_SHEET = "settle_vpa_sheet"
}

/** How far the glint on each Pay button lags the one above it, as a fraction of the cycle: a wave down the list. */
private const val SHINE_LAG = 0.08f

/** The part of each cycle the glint is crossing; the rest is the pause. */
private const val SHINE_SPAN = 0.3f
private const val SHINE_ALPHA = 0.5f

/** The stamp lands from this size, tilted a little. */
private const val STAMP_FROM = 1.9f
private const val STAMP_TILT = -8f

/**
 * A glint sweeping across a Pay button now and then: drawn over the button in its own layer so it only lights the button's
 * pixels. Decorative: [phase] is null under reduced motion and nothing is drawn. Read in draw, so it never recomposes.
 */
fun Modifier.shineSweep(phase: State<Float>?, index: Int, shine: Color): Modifier {
    if (phase == null) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val band = size.height * 1.2f
            val brush = Brush.linearGradient(listOf(Color.Transparent, shine, Color.Transparent), start = Offset.Zero, end = Offset(band, size.height))
            val bandSize = Size(band, size.height)
            onDrawWithContent {
                drawContent()
                val local = ((phase.value - index * SHINE_LAG) % 1f + 1f) % 1f
                val t = local / SHINE_SPAN
                if (t in 0f..1f) {
                    translate(left = -band + (size.width + band) * t) { drawRect(brush, size = bandSize, blendMode = BlendMode.SrcAtop) }
                }
            }
        }
}

/** "To settle in Goa trip": the total on an odometer, how many payments clear it, and what Pay really does. */
@Composable
fun SettleHero(content: SettleUpContent, modifier: Modifier = Modifier) {
    ChunkyCard(modifier.fillMaxWidth().testTag(SettleUpTags.HERO), color = SplitSmartTheme.colors.bar, shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        CompositionLocalProvider(LocalMarigoldColors provides HeroPalette) {
            val colors = SplitSmartTheme.colors
            val type = SplitSmartTheme.type
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(
                    stringResource(R.string.settle_hero_label, DisplayText.safe(content.group.name)),
                    style = type.labelStrong.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RollingAmount(content.totalPaise, Modifier.testTag(SettleUpTags.HERO_TOTAL), style = type.hero, color = colors.primary)
                BasicText(
                    pluralStringResource(R.plurals.settle_summary, content.items.size, content.items.size),
                    style = type.bodyStrong.copy(color = colors.text),
                )
                BasicText(stringResource(R.string.settle_summary_hint), Modifier.padding(top = Spacing.xxs), style = type.label.copy(color = colors.textSecondary))
            }
        }
    }
}

/** Nobody owes anybody: a marigold in bloom, the stamp (slammed down when this payment did it) and the way to the history. */
@Composable
fun AllSquareHero(groupName: String, playStamp: Boolean, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    ChunkyCard(modifier.fillMaxWidth().testTag(SettleUpTags.ALL_SQUARE), color = SplitSmartTheme.colors.bar, shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        CompositionLocalProvider(LocalMarigoldColors provides HeroPalette) {
            val colors = SplitSmartTheme.colors
            val type = SplitSmartTheme.type
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    BasicText(stringResource(R.string.settle_square_title), Modifier.semantics { heading() }, style = type.display.copy(color = colors.text))
                    BasicText(stringResource(R.string.settle_square_body, DisplayText.safe(groupName)), style = type.body.copy(color = colors.textSecondary))
                    StampMark(stringResource(R.string.settle_stamp_square), play = playStamp, Modifier.padding(top = Spacing.xs))
                }
                Illustration(IllustrationKind.Settled, size = Sizes.illustrationSmall, animate = false)
            }
            ChunkyButton(
                stringResource(R.string.settle_see_history),
                onHistory,
                Modifier.fillMaxWidth().padding(top = Spacing.md).testTag(SettleUpTags.SEE_HISTORY),
                emoji = "📜",
            )
        }
    }
}

/**
 * One payment: who pays whom (faces, sentence, amount), where it goes, and the ways to settle it. Pay is the main action
 * ([primary] only on the largest payment, so the screen keeps one marigold button), with a glint; QR and share sit beside it;
 * "Paid another way" records cash or a bank transfer, after asking.
 */
@Composable
fun PaymentCard(
    item: SettleUpItem,
    primary: Boolean,
    shine: State<Float>?,
    index: Int,
    callbacks: SettleUpCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val payer = DisplayText.safe(item.payer.name)
    val payee = DisplayText.safe(item.payee.name)
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.SETTLE_ITEM), contentPadding = PaddingValues(Spacing.md)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MemberAvatar(item.payer.name, size = Sizes.avatar, contentDescription = null)
            BasicText("→", Modifier.clearAndSetSemantics {}, style = type.bodyStrong.copy(color = colors.textSecondary))
            MemberAvatar(item.payee.name, size = Sizes.avatar, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(stringResource(R.string.debt_row, payer, payee), style = type.bodyStrong.copy(color = colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(
                    item.payee.vpa?.let { stringResource(R.string.settle_to_vpa, it) } ?: stringResource(R.string.settle_no_upi_id),
                    style = type.label.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AmountText(item.debt.amountPaise, style = type.amount, color = colors.text)
        }
        when {
            item.missingVpa -> Note(stringResource(R.string.settle_missing_vpa, payee))
            item.tooLargeForUpi -> Note(stringResource(R.string.settle_too_large))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                item.link != null -> {
                    ChunkyButton(
                        stringResource(R.string.settle_pay, AmountFormat.format(item.debt.amountPaise)),
                        { callbacks.onPay(item) },
                        Modifier.weight(1f)
                            .filterTouchesWhenObscured()
                            .shineSweep(shine, index, colors.onBar.copy(alpha = SHINE_ALPHA))
                            .testTag(SplitSmartTestTags.SETTLE_PAY),
                        style = if (primary) ButtonStyle.Primary else ButtonStyle.Secondary,
                    )
                    ChunkyIconButton(
                        ImageVector.vectorResource(R.drawable.ic_qr),
                        stringResource(R.string.settle_qr_action, payee),
                        { callbacks.onShowQr(item) },
                        Modifier.testTag(SplitSmartTestTags.SETTLE_QR),
                    )
                }
                item.missingVpa -> ChunkyButton(
                    stringResource(R.string.settle_add_vpa_for, payee),
                    { callbacks.onAddVpa(item.payee.id) },
                    Modifier.weight(1f).testTag(SplitSmartTestTags.SETTLE_ADD_VPA),
                    style = ButtonStyle.Secondary,
                )
                else -> Box(Modifier.weight(1f))
            }
            ChunkyIconButton(Icons.Filled.Share, stringResource(R.string.settle_share_action, payer), { callbacks.onShare(item) }, Modifier.testTag(SplitSmartTestTags.SETTLE_SHARE))
        }
        // Quieter than Pay: a text action, centred under the row. The view model asks before anything is recorded.
        TextAction(
            stringResource(R.string.settle_paid_other),
            { callbacks.onMarkPaid(item) },
            Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xxs).filterTouchesWhenObscured().testTag(SettleUpTags.MARK_PAID),
        )
    }
}

@Composable
private fun Note(text: String) {
    val colors = SplitSmartTheme.colors
    BasicText(
        text,
        Modifier.fillMaxWidth().padding(top = Spacing.sm).background(colors.pending.container, Radii.smallShape).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        style = SplitSmartTheme.type.label.copy(color = colors.pending.content),
    )
}

/**
 * The question asked before anything is recorded. While the person is away in their UPI app it waits; once they are back it asks
 * "Did it go through?". Paying another way asks at once. Yes is the main action here; Not yet closes it.
 */
@Composable
fun CheckCard(check: SettleCheck, recording: Boolean, onYes: () -> Unit, onNotYet: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val amount = AmountFormat.format(check.amountPaise)
    val from = DisplayText.safe(check.fromName)
    val to = DisplayText.safe(check.toName)
    val upi = check.method == SettlementMethod.UPI_APP
    ChunkyCard(modifier.fillMaxWidth().testTag(SettleUpTags.CHECK_CARD), color = colors.raised, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(Sizes.avatar).background(colors.surface, Radii.circleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                BasicText(if (!check.asking) "⏳" else if (upi) "🤔" else "💵", style = type.title.copy(color = colors.text))
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                val title = when {
                    !check.asking -> stringResource(R.string.settle_check_away_title)
                    upi -> stringResource(R.string.settle_check_title_upi)
                    else -> stringResource(R.string.settle_check_title_other)
                }
                BasicText(title, Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
                val body = when {
                    !check.asking -> stringResource(R.string.settle_check_away_body, amount, to)
                    upi -> stringResource(R.string.settle_check_body_upi, amount, to)
                    else -> stringResource(R.string.settle_check_body_other, from, to, amount)
                }
                BasicText(body, style = type.body.copy(color = colors.textSecondary))
            }
        }
        if (check.asking) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ChunkyButton(
                    stringResource(R.string.settle_check_yes),
                    onYes,
                    Modifier.weight(1f).filterTouchesWhenObscured().testTag(SettleUpTags.CHECK_YES),
                    loading = recording,
                    emoji = "✅",
                )
                ChunkyButton(stringResource(R.string.settle_check_not_yet), onNotYet, Modifier.testTag(SettleUpTags.CHECK_NOT_YET), style = ButtonStyle.Ghost, enabled = !recording)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LoadingDots(colors.accent)
                ChunkyButton(stringResource(R.string.settle_check_cancel), onNotYet, Modifier.testTag(SettleUpTags.CHECK_NOT_YET), style = ButtonStyle.Ghost, size = ButtonSize.Compact)
            }
        }
    }
}

/** The payment just marked as made, with the stamp. Stays until the screen closes or the next one replaces it; Undo is in the snackbar. */
@Composable
fun SettledCard(moment: SettledMoment, playStamp: Boolean, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth().testTag(SettleUpTags.SETTLED_CARD), color = colors.success.container, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(
                    stringResource(R.string.settled_title, DisplayText.safe(moment.fromName), DisplayText.safe(moment.toName)),
                    style = type.bodyStrong.copy(color = colors.success.content),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AmountText(moment.amountPaise, style = type.amount, color = colors.success.content)
                BasicText(
                    stringResource(if (moment.method == SettlementMethod.UPI_APP) R.string.settled_meta_upi else R.string.settled_meta_other),
                    style = type.label.copy(color = colors.success.content),
                )
            }
            StampMark(stringResource(R.string.settle_stamp), play = playStamp)
        }
    }
}

/**
 * The rubber stamp: the word in a rounded border, tilted, in mint. With [play] it slams down from large on the Bouncy spring;
 * under reduced motion, or replayed after a rotation, it is simply there. Animated in the layer only.
 */
@Composable
fun StampMark(label: String, play: Boolean, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val animate = play && motion.decorativeEnabled
    val scale = remember { Animatable(if (animate) STAMP_FROM else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { alpha.animateTo(1f, motion.spec(MotionToken.Micro)) }
        scale.animateTo(1f, motion.spec(MotionToken.Bounce))
    }
    BasicText(
        label.uppercase(),
        modifier
            .testTag(SettleUpTags.STAMP)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = STAMP_TILT
            }
            .border(Strokes.stamp, colors.success.content, Radii.smallShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .semantics { contentDescription = label },
        style = SplitSmartTheme.type.title.copy(color = colors.success.content, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}

/** Bills left out of the payments, each with the reason and a tap to fix it. A long list shows the first few. */
@Composable
fun ToFixCard(toFix: List<BillCheck>, onOpenBill: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth().testTag(SettleUpTags.TO_FIX), contentPadding = PaddingValues(vertical = Spacing.md)) {
        Column(Modifier.padding(horizontal = Spacing.md).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            BasicText(
                "🧩 " + pluralStringResource(R.plurals.settle_to_fix_title, toFix.size, toFix.size),
                Modifier.semantics { heading() },
                style = type.bodyStrong.copy(color = colors.text),
            )
            BasicText(stringResource(R.string.settle_to_fix_body), style = type.label.copy(color = colors.textSecondary))
        }
        for (check in toFix.take(MAX_FIX_ROWS)) {
            SsListRow(
                DisplayText.safe(check.bill.title),
                Modifier.testTag(SettleUpTags.TO_FIX_ROW),
                subtitle = check.problem.label(),
                trailing = { AmountText(check.bill.totalPaise, style = type.amountSmall, color = colors.textSecondary) },
                onClick = { onOpenBill(check.bill.id) },
                onClickLabel = stringResource(R.string.bill_open),
                minHeight = Sizes.listRowCompact,
            )
        }
        if (toFix.size > MAX_FIX_ROWS) {
            BasicText(
                pluralStringResource(R.plurals.settle_to_fix_more, toFix.size - MAX_FIX_ROWS, toFix.size - MAX_FIX_ROWS),
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                style = type.label.copy(color = colors.textSecondary),
            )
        }
    }
}

private const val MAX_FIX_ROWS = 5

@Composable
private fun BillProblem.label(): String = stringResource(
    when (this) {
        BillProblem.MEMBER_LEFT -> R.string.settle_fix_member_left
        BillProblem.PAYER_LEFT -> R.string.settle_fix_payer_left
        BillProblem.NOT_SPLIT -> R.string.settle_fix_not_split
        BillProblem.NO_PAYER -> R.string.settle_fix_no_payer
    },
)

/**
 * A UPI QR code, dark modules on light in both themes (scanners expect it), drawn from the module matrix as one cached path.
 * TalkBack hears [description].
 */
@Composable
fun QrCode(matrix: QrMatrix, description: String, modifier: Modifier = Modifier) {
    val paper = MarigoldLight.surface
    val ink = MarigoldLight.text
    Spacer(
        modifier
            .size(Sizes.qrCode)
            .background(paper, Radii.smallShape)
            .testTag(SplitSmartTestTags.QR_IMAGE)
            .semantics { contentDescription = description }
            .drawWithCache {
                // The matrix carries its own quiet zone, so the modules fill the square.
                val cell = size.minDimension / matrix.size
                val path = Path()
                for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                    if (matrix[x, y]) path.addRect(Rect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell))
                }
                onDrawBehind { drawPath(path, ink) }
            },
    )
}
