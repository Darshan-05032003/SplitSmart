package com.darshan.splitsmart.ui.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.designsystem.components.AmountDecimals
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountPill
import com.darshan.splitsmart.ui.designsystem.components.AmountSign
import com.darshan.splitsmart.ui.designsystem.components.AmountText
import com.darshan.splitsmart.ui.designsystem.components.AmountTone
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.BottomBarItem
import com.darshan.splitsmart.ui.designsystem.components.BrandLoader
import com.darshan.splitsmart.ui.designsystem.components.BumpCounter
import com.darshan.splitsmart.ui.designsystem.components.ButtonSize
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.ConfettiBurst
import com.darshan.splitsmart.ui.designsystem.components.CountBadge
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.EmptyState
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.IconButtonSize
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.LoadingDots
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SsBottomBar
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsSwitchRow
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionSprings
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.designsystem.tokens.contrastRatio
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

/** One named group of the catalog. Screenshot tests render each one in light and dark. */
data class CatalogSection(val key: String, val title: String, val content: @Composable ColumnScope.() -> Unit)

/** Every token and component in every state. Only invented sample values appear here; nothing reads the user's data. */
val CatalogSections: List<CatalogSection> = listOf(
    CatalogSection("colors", "Colours") { Colors() },
    CatalogSection("type", "Type scale") { TypeScale() },
    CatalogSection("shape", "Spacing, radii and depth") { Shape() },
    CatalogSection("motion", "Motion vocabulary") { Motion() },
    CatalogSection("buttons", "Chunky buttons") { Buttons() },
    CatalogSection("icon_buttons", "Icon buttons") { IconButtons() },
    CatalogSection("inputs", "Fields and switches") { Inputs() },
    CatalogSection("avatars", "Avatars and stacks") { Avatars() },
    CatalogSection("amounts", "Amounts") { Amounts() },
    CatalogSection("counters", "Counters and badges") { Counters() },
    CatalogSection("chips", "Emoji chips") { Chips() },
    CatalogSection("status", "Status pills") { Statuses() },
    CatalogSection("structure", "Bars, headers, cards, rows") { Structure() },
    CatalogSection("states", "Empty and error") { States() },
    CatalogSection("illustrations", "Illustrations") { Illustrations() },
    CatalogSection("skeleton", "Skeleton") { SkeletonList(3) },
    CatalogSection("loaders", "Loaders") { Loaders() },
    CatalogSection("sheet", "Bottom sheet") { Sheet() },
    CatalogSection("snackbar", "Snackbar with undo") { Snackbars() },
    CatalogSection("bottom_bar", "Bottom bar") { BottomBars() },
    CatalogSection("confetti", "Confetti (achievements only)") { Confetti() },
)

@Composable
fun DesignCatalog(dark: Boolean, onDarkChange: (Boolean) -> Unit, reduceMotion: Boolean, onReduceMotionChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.giant),
    ) {
        SsTopBar("Design catalog", large = true, subtitle = "Marigold Mela · debug builds only")
        SsSwitchRow("Dark theme", dark, onDarkChange)
        SsSwitchRow("Reduce motion", reduceMotion, onReduceMotionChange, description = "Decoration stops, transitions become short fades")
        for (section in CatalogSections) CatalogSectionView(section)
    }
}

/** A section with its title, tagged for the screenshot tests. */
@Composable
fun CatalogSectionView(section: CatalogSection, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .testTag("catalog:${section.key}")
            .padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SsSectionHeader(section.title)
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = section.content)
    }
}

@Composable
private fun Caption(text: String) {
    BasicText(text, style = SplitSmartTheme.type.label.copy(color = SplitSmartTheme.colors.textSecondary))
}

@Composable
private fun Swatch(name: String, color: Color) {
    val colors = SplitSmartTheme.colors
    val ink = if (contrastRatio(colors.text, color) >= contrastRatio(colors.bg, color)) colors.text else colors.bg
    Column(
        Modifier
            .width(SWATCH)
            .background(color, Radii.smallShape)
            .border(Strokes.hairline, colors.hairline, Radii.smallShape)
            .padding(Spacing.xs),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        BasicText(name, style = SplitSmartTheme.type.labelStrong.copy(color = ink), maxLines = 1)
        BasicText("#%06X".format(color.toArgb() and 0xFFFFFF), style = SplitSmartTheme.type.label.copy(color = ink), maxLines = 1)
    }
}

private val SWATCH = Sizes.avatarHero + Spacing.lg

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Colors() {
    val c = SplitSmartTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Swatch("bg", c.bg)
        Swatch("surface", c.surface)
        Swatch("raised", c.raised)
        Swatch("text", c.text)
        Swatch("secondary", c.textSecondary)
        Swatch("primary", c.primary)
        Swatch("depth", c.primaryDepth)
        Swatch("accent", c.accent)
        Swatch("owed", c.success.content)
        Swatch("refining", c.pending.content)
        Swatch("you owe", c.failed.content)
        Swatch("AI", c.info.content)
        Swatch("outline", c.outline)
        Swatch("bar", c.bar)
    }
    val pairs = c.textPairs()
    Caption("${pairs.size} text pairs, lowest %.2f:1 (AA needs 4.5)".format(pairs.minOf { it.ratio }))
}

@Composable
private fun TypeSample(name: String, style: TextStyle, sample: String) {
    Column {
        Caption("$name · ${style.fontSize.value.toInt()}/${style.lineHeight.value.toInt()}")
        BasicText(sample, style = style.copy(color = SplitSmartTheme.colors.text), maxLines = 1)
    }
}

@Composable
private fun TypeScale() {
    val t = SplitSmartTheme.type
    TypeSample("hero", t.hero, "₹12,34,567")
    TypeSample("display", t.display, "Split it. Settle it.")
    TypeSample("title", t.title, "Goa trip ✈️")
    TypeSample("amount", t.amount, "₹1,118.70")
    TypeSample("body", t.body, "Ravi owes you ₹440.70 for dinner")
    TypeSample("body strong", t.bodyStrong, "Paid by Asha")
    TypeSample("button", t.button, "Save split")
    TypeSample("label", t.label, "3 members · 12 bills")
}

@Composable
private fun Shape() {
    val c = SplitSmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.Bottom) {
        for ((name, size) in listOf("4" to Spacing.xxs, "8" to Spacing.xs, "12" to Spacing.sm, "16" to Spacing.md, "20" to Spacing.lg, "24" to Spacing.xl, "32" to Spacing.xxl, "48" to Spacing.huge)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(Spacing.lg, size).background(c.primary, Radii.smallShape))
                Caption(name)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        for ((name, shape) in listOf("16" to Radii.smallShape, "24" to Radii.mediumShape, "32" to Radii.largeShape)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(Sizes.avatarHero).background(c.raised, shape))
                Caption("radius $name")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        for ((name, depth) in listOf("chip" to Depth.chip, "card" to Depth.card, "button" to Depth.button)) {
            ChunkyCard(Modifier.weight(1f), depth = depth) { BasicText(name, style = SplitSmartTheme.type.bodyStrong.copy(color = c.text)) }
        }
    }
}

@Composable
private fun Motion() {
    Caption("Snappy ${MotionSprings.Snappy.stiffness.toInt()}/${MotionSprings.Snappy.dampingRatio} · Bouncy ${MotionSprings.Bouncy.stiffness.toInt()}/${MotionSprings.Bouncy.dampingRatio} · Gentle ${MotionSprings.Gentle.stiffness.toInt()}/${MotionSprings.Gentle.dampingRatio}")
    Caption("Press ${MotionDurations.Press} · small ${MotionDurations.Small} · screen ${MotionDurations.Screen} · stagger ${MotionDurations.Stagger} ms")
    var amount by rememberSaveable { mutableLongStateOf(1_118_70L) }
    var count by rememberSaveable { mutableIntStateOf(2) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        RollingAmount(amount, Modifier.weight(1f), style = SplitSmartTheme.type.display)
        ChunkyButton("Roll", { amount = if (amount == 1_118_70L) 2_08_310_75L else 1_118_70L }, size = ButtonSize.Compact, style = ButtonStyle.Secondary)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BumpCounter(count, Modifier.weight(1f), style = SplitSmartTheme.type.title)
        ChunkyButton("+1", { count++ }, size = ButtonSize.Compact, style = ButtonStyle.Secondary)
    }
}

@Composable
private fun Buttons() {
    ChunkyButton("Scan a bill", {}, Modifier.fillMaxWidth(), emoji = "🧾")
    ChunkyButton("Save split", {}, Modifier.fillMaxWidth(), press = rememberPressFeedback(initial = 1f))
    Caption("Pressed: squashed into its base")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChunkyButton("New group", {}, Modifier.weight(1f), style = ButtonStyle.Secondary)
        ChunkyButton("Later", {}, Modifier.weight(1f), style = ButtonStyle.Ghost)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChunkyButton("Delete group", {}, Modifier.weight(1f), style = ButtonStyle.Danger, size = ButtonSize.Compact)
        ChunkyButton("Settle up", {}, Modifier.weight(1f), loading = true, size = ButtonSize.Compact)
    }
    ChunkyButton("Add expense", {}, Modifier.fillMaxWidth(), enabled = false)
}

@Composable
private fun IconButtons() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ChunkyIconButton(Icons.Filled.Share, "Share", {})
        ChunkyIconButton(Icons.Filled.Settings, "Settings", {}, style = ButtonStyle.Ghost)
        ChunkyIconButton(Icons.Filled.Add, "Add", {}, style = ButtonStyle.Primary)
        ChunkyIconButton(Icons.Filled.Add, "New", {}, style = ButtonStyle.Primary, size = IconButtonSize.Fab)
        ChunkyIconButton(Icons.Filled.Add, "Unavailable", {}, enabled = false)
    }
}

@Composable
private fun Inputs() {
    var name by rememberSaveable { mutableStateOf("") }
    SsTextField(name, { name = it }, "Group name", placeholder = "Flat 302, Goa trip…", emoji = "🏠")
    SsTextField("Ravi", {}, "Friend's name", supporting = "They'll see this on the split")
    SsTextField("12a", {}, "Amount", error = "Use numbers only, like 450 or 450.50", emoji = "₹")
    SsTextField("ravi@okbank", {}, "UPI ID", enabled = false)
    var haptics by rememberSaveable { mutableStateOf(true) }
    var sound by rememberSaveable { mutableStateOf(false) }
    SsSwitchRow("Haptics", haptics, { haptics = it })
    SsSwitchRow("Sounds", sound, { sound = it }, description = "Off by default")
}

private val Crowd = listOf("Asha Rao", "Ravi Kumar", "Meera Iyer", "Kabir Shah") + (5..200).map { "Friend $it" }

@Composable
private fun Avatars() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        MemberAvatar("Asha Rao", size = Sizes.avatarSmall)
        MemberAvatar("Ravi Kumar")
        MemberAvatar("Meera Iyer", size = Sizes.avatarLarge)
        MemberAvatar("Kabir", size = Sizes.avatarHero)
        MemberAvatar("राहुल शर्मा")
        MemberAvatar("🌼")
    }
    Caption("Stacked")
    AvatarStack(Crowd.take(3))
    Caption("200 members")
    AvatarStack(Crowd)
    Caption("Fanned out (tap to stack)")
    var expanded by rememberSaveable { mutableStateOf(true) }
    AvatarStack(Crowd.take(5), expanded, { expanded = it })
}

@Composable
private fun Amounts() {
    val t = SplitSmartTheme.type
    AmountText(1_118_70L, style = t.hero)
    AmountText(-60_000L, style = t.amount, color = SplitSmartTheme.colors.failed.content)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AmountPill(67_800L, AmountTone.Owed)
        AmountPill(60_000L, AmountTone.Owe)
        AmountPill(1_412_50L, AmountTone.Neutral)
    }
    AmountPill(44_070L, AmountTone.Owed, label = "you get")
    Caption("Largest amount, fitted to the width")
    RollingAmount(AmountFormat.MAX_DISPLAY_PAISE)
    AmountText(60_000L, style = t.amount, decimals = AmountDecimals.WhenNonZero, sign = AmountSign.None)
}

@Composable
private fun Counters() {
    val c = SplitSmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(c.raised, Radii.pillShape).padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
            BumpCounter(3, style = SplitSmartTheme.type.title)
        }
        Box {
            MemberAvatar("Asha Rao", size = Sizes.avatarLarge)
            CountBadge(3, Modifier.align(Alignment.TopEnd).offset(x = Spacing.xs, y = -Spacing.xxs), contentDescription = "3 items")
        }
        Box {
            MemberAvatar("Ravi Kumar", size = Sizes.avatarLarge)
            CountBadge(120, Modifier.align(Alignment.TopEnd).offset(x = Spacing.sm, y = -Spacing.xxs), contentDescription = "120 items")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips() {
    var picked by rememberSaveable { mutableStateOf(setOf("Food")) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        for ((emoji, label) in listOf("🍛" to "Food", "🚕" to "Travel", "🏠" to "Rent", "🎬" to "Fun")) {
            EmojiChip(label, label in picked, { picked = if (label in picked) picked - label else picked + label }, emoji = emoji)
        }
        EmojiChip("Gifts", false, {}, emoji = "🎁", enabled = false)
    }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf("Equal", "By item", "Shares", "%").forEachIndexed { index, label -> EmojiChip(label, mode == index, { mode = index }, exclusive = true) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Statuses() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatusPill(StatusKind.Owed, "You're owed")
        StatusPill(StatusKind.Owe, "You owe")
        StatusPill(StatusKind.Settled, "Settled")
        StatusPill(StatusKind.Refining, "AI is refining")
        StatusPill(StatusKind.Ai, "Read by AI")
        StatusPill(StatusKind.Neutral, "Not split yet")
    }
}

@Composable
private fun Structure() {
    SsTopBar("Goa trip", onBack = {}, subtitle = "4 members") { ChunkyIconButton(Icons.Filled.Share, "Share", {}, style = ButtonStyle.Ghost) }
    SsSectionHeader("Recent bills", emoji = "🧾", actionLabel = "See all", onAction = {})
    ChunkyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs)) {
        SsListRow("Dinner at the beach shack", subtitle = "Paid by Asha · 4 people", leading = { MemberAvatar("Asha Rao") }, trailing = { AmountPill(67_800L, AmountTone.Owed) }, onClick = {})
        SsListRow("Scooter rental", subtitle = "Paid by you", leading = { MemberAvatar("Darshan") }, trailing = { AmountPill(60_000L, AmountTone.Owe) }, onClick = {})
        SsListRow("A very long bill name that keeps going well past the edge of the card", subtitle = "Text from a receipt is cut cleanly", leading = { MemberAvatar("Meera Iyer") })
    }
}

@Composable
private fun States() {
    ChunkyCard { EmptyState(IllustrationKind.Groups, "No groups yet", "Make one for your flat, a trip or tonight's dinner.", actionLabel = "New group", onAction = {}, actionEmoji = "✨") }
    ChunkyCard { ErrorState("Couldn't load your groups", "Something went wrong on this phone. Your data is safe.", onRetry = {}) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Illustrations() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (kind in IllustrationKind.entries) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Illustration(kind, size = Sizes.illustrationSmall)
                Caption(kind.name)
            }
        }
    }
}

@Composable
private fun Loaders() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
        BrandLoader()
        BrandLoader(size = Sizes.loaderSmall)
        LoadingDots(SplitSmartTheme.colors.text)
    }
}

@Composable
private fun Sheet() {
    var open by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxWidth().height(SHEET_DEMO_HEIGHT).background(SplitSmartTheme.colors.raised, Radii.mediumShape)) {
        if (!open) ChunkyButton("Open sheet", { open = true }, Modifier.align(Alignment.Center), style = ButtonStyle.Secondary)
        SsBottomSheet(open, { open = false }) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BasicText("Who paid?", style = SplitSmartTheme.type.title.copy(color = SplitSmartTheme.colors.text))
                SsListRow("Asha Rao", leading = { MemberAvatar("Asha Rao") }, onClick = { open = false }, minHeight = Sizes.listRowCompact)
                ChunkyButton("Done", { open = false }, Modifier.fillMaxWidth())
            }
        }
    }
}

private val SHEET_DEMO_HEIGHT: Dp = Sizes.illustration * 2

@Composable
private fun Snackbars() {
    val undo = remember { SsSnackbarState() }
    val info = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { launch { undo.show("Group archived", emoji = "📦", actionLabel = "Undo") } }
    LaunchedEffect(Unit) { launch { info.show("Split saved", emoji = "✅") } }
    Box(Modifier.fillMaxWidth().height(Sizes.illustrationSmall)) { SsSnackbarHost(undo, Modifier.align(Alignment.BottomCenter)) }
    Box(Modifier.fillMaxWidth().height(Sizes.illustrationSmall)) { SsSnackbarHost(info, Modifier.align(Alignment.BottomCenter)) }
    ChunkyButton("Show again", { scope.launch { undo.show("Bill deleted", emoji = "🗑️", actionLabel = "Undo") } }, size = ButtonSize.Compact, style = ButtonStyle.Secondary)
}

@Composable
private fun BottomBars() {
    val items = listOf(
        BottomBarItem("Groups", ImageVector.vectorResource(R.drawable.ic_group)),
        BottomBarItem("Scan", ImageVector.vectorResource(R.drawable.ic_camera), badge = 2),
        BottomBarItem("Me", ImageVector.vectorResource(R.drawable.ic_wallet)),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }
    SsBottomBar(items, selected, { selected = it })
}

@Composable
private fun Confetti() {
    var key by rememberSaveable { mutableIntStateOf(0) }
    Caption("Plays only for real achievements: settled, all square. Off under reduced motion.")
    Box(Modifier.fillMaxWidth().height(Sizes.illustration), contentAlignment = Alignment.Center) {
        ChunkyButton("Celebrate (demo)", { key++ }, emoji = "🎉")
        ConfettiBurst(if (key == 0) null else key, Modifier.matchParentSize())
    }
}
