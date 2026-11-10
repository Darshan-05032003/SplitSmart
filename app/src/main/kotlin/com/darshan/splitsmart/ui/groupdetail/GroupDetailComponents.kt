package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.formatShortDate
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squash
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.groups.GroupTile
import com.darshan.splitsmart.ui.groups.HeroPalette
import com.darshan.splitsmart.ui.navigation.billContainerKey
import com.darshan.splitsmart.ui.navigation.containerTransform
import com.darshan.splitsmart.ui.theme.LocalMarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/** Test tags of the group screen that are not shared with other screens. */
object GroupDetailTags {
    const val LIST = "group_list"
    const val HERO = "group_hero"
    const val SPENT = "group_spent"
    const val OPTIONS = "group_options"
    const val OPTIONS_SHEET = "group_options_sheet"
    const val ARCHIVE = "group_archive"
    const val DELETE_SHEET = "group_delete_sheet"
    const val DELETE_CONFIRM = "group_delete_confirm"
    const val DELETE_KEEP = "group_delete_keep"
    const val MEMBER_SHEET = "group_member_sheet"
    const val MEMBER_REMOVE = "group_member_remove"
    const val MEMBERS_ROW = "group_members_row"
    const val NO_BILLS = "group_no_bills"
    const val SETTLED = "group_settled"
}

/**
 * The group's hero, on the bar's aubergine with night-palette ink like the home hero: what was spent together on a rolling
 * odometer, what is still to move, and Settle up as the one main action when anyone owes anyone.
 */
@Composable
fun GroupHero(detail: GroupDetail, primary: PrimaryAction, onSettleUp: () -> Unit, modifier: Modifier = Modifier) {
    val figures = remember(detail) { GroupDetailLogic.summary(detail) }
    ChunkyCard(modifier.fillMaxWidth().testTag(GroupDetailTags.HERO), color = SplitSmartTheme.colors.bar, shape = Radii.largeShape, contentPadding = PaddingValues(Spacing.lg)) {
        CompositionLocalProvider(LocalMarigoldColors provides HeroPalette) {
            val colors = SplitSmartTheme.colors
            val type = SplitSmartTheme.type
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Amber container: the raised plum would vanish into the hero in the dark theme.
                GroupTile(detail.group.name, size = Sizes.avatar, container = colors.pending.container)
                BasicText(
                    stringResource(
                        R.string.group_summary_meta,
                        pluralStringResource(R.plurals.member_count, detail.members.size, detail.members.size),
                        pluralStringResource(R.plurals.bill_count, detail.bills.size, detail.bills.size),
                    ),
                    style = type.labelStrong.copy(color = colors.textSecondary),
                )
            }
            Column(Modifier.padding(top = Spacing.sm).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(stringResource(R.string.group_spent_together), style = type.bodyStrong.copy(color = colors.text))
                RollingAmount(figures.spentPaise, Modifier.testTag(GroupDetailTags.SPENT), style = type.hero, color = colors.primary)
                BasicText(
                    when {
                        figures.outstandingPaise > 0 -> pluralStringResource(R.plurals.group_summary_outstanding, figures.payments, AmountFormat.format(figures.outstandingPaise), figures.payments)
                        detail.bills.isEmpty() -> stringResource(R.string.group_hero_no_bills)
                        else -> stringResource(R.string.group_all_square)
                    },
                    style = type.body.copy(color = colors.textSecondary),
                )
            }
            if (primary == PrimaryAction.SettleUp) {
                ChunkyButton(
                    stringResource(R.string.group_settle_up),
                    onSettleUp,
                    Modifier.fillMaxWidth().padding(top = Spacing.md).testTag(SplitSmartTestTags.SETTLE_UP_BUTTON),
                    emoji = "💸",
                )
            }
        }
    }
}

/** Everyone in the group as faces with names, side by side; a face opens that person's sheet. Lazy, so 200 people cost a screenful. */
@Composable
fun MembersRow(members: List<Member>, onOpen: (Member) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth().testTag(GroupDetailTags.MEMBERS_ROW),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(members, key = { it.id }, contentType = { "member" }) { member -> MemberFace(member, onClick = { onOpen(member) }) }
    }
}

@Composable
private fun MemberFace(member: Member, onClick: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val haptics = LocalHaptics.current
    val press = rememberPressFeedback()
    val name = DisplayText.safe(member.name)
    val description = if (member.vpa != null) stringResource(R.string.member_face_with_upi, name) else name
    Column(
        Modifier
            .width(Sizes.avatarLarge + Spacing.md)
            .trackPress(press)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.member_open)) {
                haptics.tick()
                onClick()
            }
            .squash(press, Squash.Subtle)
            .testTag(SplitSmartTestTags.MEMBER_ITEM)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        MemberAvatar(member.name, size = Sizes.avatarLarge, ring = member.vpa != null, ringColor = colors.success.content, contentDescription = null)
        BasicText(name, Modifier.clearAndSetSemantics {}, style = type.label.copy(color = colors.text, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One way to add a bill: a chunky tile with an emoji. The [primary] tile is marigold. */
@Composable
fun AddBillTile(emoji: String, label: String, tag: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val ink = if (primary) colors.onPrimary else colors.text
    ChunkyCard(
        modifier.fillMaxHeight().testTag(tag),
        onClick = onClick,
        color = if (primary) colors.primary else colors.raised,
        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.md),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BasicText(emoji, style = type.title.copy(color = ink))
            BasicText(label, style = type.labelStrong.copy(color = ink, textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Scan, gallery and by hand, side by side and the same height whatever the text size. */
@Composable
fun AddBillRow(primary: PrimaryAction, onScan: () -> Unit, onGallery: () -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AddBillTile("📸", stringResource(R.string.scan_receipt), SplitSmartTestTags.SCAN_BUTTON, onScan, primary = primary == PrimaryAction.Scan, Modifier.weight(1f))
        AddBillTile("🖼️", stringResource(R.string.add_bill_gallery), SplitSmartTestTags.GALLERY_ADD_BUTTON, onGallery, primary = false, Modifier.weight(1f))
        AddBillTile("✍️", stringResource(R.string.add_bill_manual), SplitSmartTestTags.ADD_BILL_BUTTON, onManual, primary = false, Modifier.weight(1f))
    }
}

/** "Ravi owes Asha ₹150.00": the two faces with an arrow, the sentence and the amount. */
@Composable
fun DebtRow(from: String, to: String, paise: Long, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    ChunkyCard(modifier.fillMaxWidth().testTag(SplitSmartTestTags.DEBT_ITEM).semantics(mergeDescendants = true) {}, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MemberAvatar(from, size = Sizes.avatarSmall, contentDescription = null)
            BasicText("→", Modifier.clearAndSetSemantics {}, style = type.bodyStrong.copy(color = colors.textSecondary))
            MemberAvatar(to, size = Sizes.avatarSmall, contentDescription = null)
            BasicText(
                stringResource(R.string.debt_row, DisplayText.safe(from), DisplayText.safe(to)),
                Modifier.weight(1f).padding(start = Spacing.xxs),
                style = type.bodyStrong.copy(color = colors.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AmountText(paise, style = type.amount, color = colors.text)
        }
    }
}

/** One bill: what, when, who paid, how it was made (AI gets its sparkle) and how much. Opens the bill. */
@Composable
fun BillRow(bill: Bill, payerName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val ai = bill.source == BillSource.SCAN_LLM
    ChunkyCard(
        modifier.fillMaxWidth().containerTransform(billContainerKey(bill.id)).testTag(SplitSmartTestTags.BILL_ITEM),
        onClick = onClick,
        onClickLabel = stringResource(R.string.bill_open),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BillGlyph(if (bill.source == BillSource.MANUAL) "✍️" else "🧾", ai)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(DisplayText.safe(bill.title), style = type.bodyStrong.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    stringResource(
                        R.string.bill_row_meta,
                        formatShortDate(bill.createdAt),
                        payerName?.let { stringResource(R.string.bill_paid_by_value, DisplayText.safe(it)) } ?: stringResource(R.string.bill_no_payer),
                    ),
                    style = type.label.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    StatusPill(bill.status.kind(), bill.status.label(), animate = false)
                    if (ai) StatusPill(StatusKind.Ai, stringResource(R.string.badge_ai), Modifier.testTag(SplitSmartTestTags.AI_BADGE), animate = false)
                }
            }
            AmountText(bill.totalPaise, style = type.amount, color = colors.text)
        }
    }
}

/** Draft is still to do (amber), split is done but money still moves (neutral), settled is mint. */
private fun BillStatus.kind(): StatusKind = when (this) {
    BillStatus.DRAFT -> StatusKind.Refining
    BillStatus.SPLIT -> StatusKind.Neutral
    BillStatus.SETTLED -> StatusKind.Settled
}

@Composable
private fun BillGlyph(emoji: String, ai: Boolean) {
    val colors = SplitSmartTheme.colors
    Box(
        Modifier.size(Sizes.avatar).background(if (ai) colors.info.container else colors.raised, Radii.smallShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(emoji, style = SplitSmartTheme.type.title.copy(color = colors.text))
    }
}
