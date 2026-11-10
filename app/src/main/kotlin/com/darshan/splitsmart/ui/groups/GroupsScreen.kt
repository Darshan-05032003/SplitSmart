package com.darshan.splitsmart.ui.groups

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.EmptyState
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.SkeletonBlock
import com.darshan.splitsmart.ui.designsystem.components.SkeletonGroup
import com.darshan.splitsmart.ui.designsystem.components.SkeletonLine
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.modifiers.popInEntrance
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.messageRes
import com.darshan.splitsmart.ui.navigation.HideShellBarWhile
import com.darshan.splitsmart.ui.navigation.LocalBottomBarInset
import com.darshan.splitsmart.ui.navigation.ScanPickerContent
import com.darshan.splitsmart.ui.navigation.ScanTarget
import com.darshan.splitsmart.ui.navigation.ScrollToTopOnReselect
import com.darshan.splitsmart.ui.navigation.ShellRequest
import com.darshan.splitsmart.ui.navigation.ShellRequestEffect
import com.darshan.splitsmart.ui.navigation.ShellRoute
import com.darshan.splitsmart.ui.navigation.scanChoicesOf
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.launch

/**
 * The groups home. [onScan] is the shell's Scan (it already knows which group, or asks); [onAddExpense] opens a group's screen with
 * its add-bill sheet up.
 */
@Composable
fun GroupsRoute(
    onOpenGroup: (String) -> Unit,
    onScan: () -> Unit = {},
    onAddExpense: (String) -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SsSnackbarState() }
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.error_unknown)
    val archivedTemplate = stringResource(R.string.groups_archived_message)
    val undo = stringResource(R.string.action_undo)
    // Scan in the bar with no groups yet asks for "New group" here.
    ShellRequestEffect(ShellRequest.NewGroup) { viewModel.openCreateDialog() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            // Each message in its own coroutine: a newer one replaces it (closing its undo window); the list never waits on it.
            when (event) {
                is GroupsEvent.GroupCreated -> Unit
                GroupsEvent.CreateFailed, GroupsEvent.ActionFailed -> scope.launch { snackbar.show(failedText, emoji = "😕") }
                is GroupsEvent.GroupArchived -> scope.launch {
                    val message = String.format(archivedTemplate, DisplayText.safe(event.name))
                    if (snackbar.show(message, emoji = "📦", actionLabel = undo) == SnackbarResult.Undo) viewModel.unarchive(event.groupId)
                }
            }
        }
    }
    GroupsScreen(
        state = state,
        callbacks = GroupsCallbacks(
            onOpenGroup = onOpenGroup,
            onCreateGroup = viewModel::openCreateDialog,
            onDismissCreate = viewModel::dismissDialog,
            onCreateNameChange = viewModel::onDraftNameChange,
            onConfirmCreate = viewModel::submitCreate,
            onRetry = viewModel::retry,
            onScan = onScan,
            onAddExpense = onAddExpense,
            onArchive = viewModel::archive,
            onUnarchive = viewModel::unarchive,
            profile = ProfileCallbacks(
                onEdit = { viewModel.openNameDialog(state.overview.yourName) },
                onChange = viewModel::onNameDraftChange,
                onSave = viewModel::saveName,
                onDismiss = viewModel::dismissNameDialog,
            ),
        ),
        snackbar = snackbar,
    )
}

data class ProfileCallbacks(
    val onEdit: () -> Unit = {},
    val onChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** Everything the groups home can ask for. */
data class GroupsCallbacks(
    val onOpenGroup: (String) -> Unit = {},
    val onCreateGroup: () -> Unit = {},
    val onDismissCreate: () -> Unit = {},
    val onCreateNameChange: (String) -> Unit = {},
    val onConfirmCreate: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onAddExpense: (String) -> Unit = {},
    val onArchive: (String) -> Unit = {},
    val onUnarchive: (String) -> Unit = {},
    val profile: ProfileCallbacks = ProfileCallbacks(),
)

/**
 * The groups home on Marigold: a large title, the balance hero, group cards that pop in staggered and swipe away to archive, the
 * archived groups folded at the end, and a FAB that bursts into Scan / Add an expense / New group. It draws under the floating
 * bar; the end of the list and the FAB keep clear of it. The one primary action is the FAB, or the empty page's button when
 * there are no groups yet (the FAB is hidden then).
 */
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    callbacks: GroupsCallbacks,
    snackbar: SsSnackbarState = remember { SsSnackbarState() },
    listState: LazyListState = rememberLazyListState(),
) {
    ScrollToTopOnReselect(ShellRoute.Groups, listState)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pickingForExpense by rememberSaveable { mutableStateOf(false) }
    val content = state.groups as? LoadState.Content
    val choices = scanChoicesOf((content?.data.orEmpty() + state.archived).map { it.group })
    val addExpense = {
        when (val target = ScanTarget.resolve(choices)) {
            ScanTarget.Waiting -> Unit
            ScanTarget.NeedsGroup -> callbacks.onCreateGroup()
            is ScanTarget.Direct -> callbacks.onAddExpense(target.groupId)
            is ScanTarget.Pick -> pickingForExpense = true
        }
    }
    val sheetOpen = state.draft != null || state.nameDraft != null || pickingForExpense
    // Sheets and the menu are drawn inside this screen: the floating bar steps aside while they are up.
    HideShellBarWhile(sheetOpen || menuOpen)
    val barInset = LocalBottomBarInset.current
    Box(Modifier.fillMaxSize()) {
        // While a sheet or the menu is up, the page behind it is hidden from TalkBack.
        Box(Modifier.fillMaxSize().then(if (sheetOpen || menuOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
            when (val groups = state.groups) {
                LoadState.Loading -> GroupsLoading()
                LoadState.Empty -> GroupsEmpty(callbacks.onCreateGroup)
                is LoadState.Error -> GroupsError(stringResource(groups.reason.messageRes()), callbacks.onRetry)
                is LoadState.Content -> GroupsList(state, groups.data, callbacks, listState)
            }
        }
        if (content != null) {
            GroupsFabMenu(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                actions = listOf(
                    FabAction(stringResource(R.string.groups_menu_scan), "📸", GroupsTags.MENU_SCAN, callbacks.onScan),
                    FabAction(stringResource(R.string.groups_menu_expense), "✍️", GroupsTags.MENU_EXPENSE, addExpense),
                    FabAction(stringResource(R.string.groups_new_group), "👯", GroupsTags.MENU_NEW_GROUP, callbacks.onCreateGroup),
                ),
                contentPadding = PaddingValues(bottom = barInset),
                modifier = Modifier.navigationBarsPadding(),
            )
        }
        SsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
        CreateGroupSheet(state.draft, callbacks)
        NameSheet(state.nameDraft, callbacks.profile)
        SsBottomSheet(visible = pickingForExpense, onDismissRequest = { pickingForExpense = false }) {
            Box(Modifier.testTag(GroupsTags.EXPENSE_PICKER)) {
                ScanPickerContent(choices, title = stringResource(R.string.groups_pick_for_expense)) { groupId ->
                    pickingForExpense = false
                    callbacks.onAddExpense(groupId)
                }
            }
        }
    }
}

@Composable
private fun GroupsList(state: GroupsUiState, cards: List<GroupCard>, callbacks: GroupsCallbacks, listState: LazyListState) {
    val motion = LocalMotion.current
    // Saved, so coming back from a group (or rotating) does not replay the opening stagger.
    var opened by rememberSaveable { mutableStateOf(false) }
    val entrance = remember { EntranceTracker(staggerFirstLoad = !opened) }
    entrance.update(cards.map { it.group.id })
    LaunchedEffect(Unit) { opened = true }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(key = "top", contentType = "top") {
            SsTopBar(stringResource(R.string.app_name), large = true, subtitle = stringResource(R.string.app_tagline))
        }
        item(key = "hero", contentType = "hero") {
            BalanceHero(state.overview, callbacks.profile.onEdit, Modifier.padding(horizontal = Spacing.screen))
        }
        if (cards.isNotEmpty()) {
            item(key = "header", contentType = "header") {
                SsSectionHeader(stringResource(R.string.groups_your_groups), Modifier.padding(top = Spacing.xs), emoji = "👯")
            }
        } else {
            item(key = "all_archived", contentType = "note") { AllArchivedNote() }
        }
        items(cards, key = { it.group.id }, contentType = { "group" }) { card ->
            val delayIndex = remember(card.group.id) { entrance.take(card.group.id) }
            SwipeToArchive(
                onArchive = { callbacks.onArchive(card.group.id) },
                modifier = Modifier
                    .animateItem(fadeInSpec = null, placementSpec = motion.spec(MotionToken.Settle), fadeOutSpec = motion.spec(MotionToken.Micro))
                    .padding(horizontal = Spacing.screen)
                    .then(if (delayIndex != null) Modifier.popInEntrance(delayIndex, card.group.id) else Modifier),
            ) {
                GroupCardView(card, onClick = { callbacks.onOpenGroup(card.group.id) }, onArchive = { callbacks.onArchive(card.group.id) })
            }
        }
        if (cards.isNotEmpty() && state.archived.isEmpty()) {
            item(key = "hint", contentType = "hint") {
                BasicText(
                    stringResource(R.string.groups_archived_hint),
                    Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg),
                    style = SplitSmartTheme.type.label.copy(color = SplitSmartTheme.colors.textSecondary, textAlign = TextAlign.Center),
                )
            }
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived_toggle", contentType = "toggle") {
                ArchivedToggle(state.archived.size, archivedOpen) { archivedOpen = !archivedOpen }
            }
            if (archivedOpen) {
                items(state.archived, key = { "archived:" + it.group.id }, contentType = { "archived" }) { card ->
                    ArchivedRow(card, Modifier.animateItem(placementSpec = motion.spec(MotionToken.Settle)), callbacks)
                }
            }
        }
        // The floating bar, the FAB and the system navigation bar sit over the end of the list.
        item(key = "end", contentType = "end") {
            Spacer(Modifier.navigationBarsPadding().height(LocalBottomBarInset.current + Sizes.fab + Spacing.xxl))
        }
    }
}

@Composable
private fun AllArchivedNote() {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg).testTag(GroupsTags.ALL_ARCHIVED),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Illustration(IllustrationKind.Groups, size = Sizes.illustrationSmall)
        BasicText(stringResource(R.string.groups_all_archived_title), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text, textAlign = TextAlign.Center))
        BasicText(stringResource(R.string.groups_all_archived_body), style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
    }
}

@Composable
private fun ArchivedToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val turn = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open, motion) { turn.animateTo(if (open) 1f else 0f, motion.spec(MotionToken.Snap)) }
    val state = stringResource(if (open) R.string.groups_archived_open else R.string.groups_archived_closed)
    SsListRow(
        title = stringResource(R.string.groups_archived_count, count),
        leading = { BasicText("📦", style = SplitSmartTheme.type.title.copy(color = colors.text)) },
        trailing = {
            Image(
                rememberVectorPainter(Icons.Filled.KeyboardArrowDown),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textSecondary),
                modifier = Modifier.graphicsLayer { rotationZ = ARROW_TURN_DEGREES * turn.value },
            )
        },
        onClick = onToggle,
        minHeight = Sizes.listRowCompact,
        modifier = Modifier.padding(top = Spacing.xs).testTag(GroupsTags.ARCHIVED_TOGGLE).semantics { stateDescription = state },
    )
}

private const val ARROW_TURN_DEGREES = 180f

@Composable
private fun ArchivedRow(card: GroupCard, modifier: Modifier, callbacks: GroupsCallbacks) {
    val (_, balance) = GroupsHomeLogic.cardBalance(card).label()
    SsListRow(
        title = card.group.name,
        subtitle = balance,
        leading = { GroupTile(card.group.name, size = Sizes.avatar) },
        trailing = {
            TextAction(stringResource(R.string.groups_unarchive), { callbacks.onUnarchive(card.group.id) }, Modifier.testTag(GroupsTags.unarchive(card.group.id)))
        },
        onClick = { callbacks.onOpenGroup(card.group.id) },
        onClickLabel = stringResource(R.string.group_card_open),
        modifier = modifier.testTag(GroupsTags.ARCHIVED_ITEM),
    )
}

@Composable
private fun GroupsLoading() {
    val label = stringResource(R.string.groups_loading)
    Column(Modifier.fillMaxSize()) {
        SsTopBar(stringResource(R.string.app_name), large = true, subtitle = stringResource(R.string.app_tagline))
        SkeletonGroup {
            Column(
                Modifier.padding(horizontal = Spacing.screen).testTag(SplitSmartTestTags.LOADING).clearAndSetSemantics { contentDescription = label },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.heroSkeleton), Radii.largeShape)
                SkeletonLine(Modifier.padding(vertical = Spacing.sm), widthFraction = SKELETON_HEADER_FRACTION, height = Sizes.skeletonTitle)
                repeat(SKELETON_CARDS) { SkeletonBlock(Modifier.fillMaxWidth().height(Sizes.cardSkeleton), Radii.mediumShape) }
            }
        }
    }
}

private const val SKELETON_CARDS = 3
private const val SKELETON_HEADER_FRACTION = 0.4f

@Composable
private fun GroupsEmpty(onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SsTopBar(stringResource(R.string.app_name), large = true, subtitle = stringResource(R.string.app_tagline))
        EmptyState(
            IllustrationKind.Groups,
            title = stringResource(R.string.groups_empty_title),
            message = stringResource(R.string.groups_empty_subtitle),
            actionLabel = stringResource(R.string.groups_empty_action),
            onAction = onCreate,
            actionEmoji = "👯",
            modifier = Modifier.testTag(SplitSmartTestTags.EMPTY),
        )
        Spacer(Modifier.navigationBarsPadding().height(LocalBottomBarInset.current + Spacing.md))
    }
}

@Composable
private fun GroupsError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SsTopBar(stringResource(R.string.app_name), large = true)
        ErrorState(
            title = stringResource(R.string.groups_error_title),
            message = message,
            onRetry = onRetry,
            retryLabel = stringResource(R.string.retry_label),
            modifier = Modifier.testTag(SplitSmartTestTags.ERROR),
        )
    }
}

@Composable
private fun CreateGroupSheet(draft: GroupDraft?, callbacks: GroupsCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    // Keeps the last draft on screen while the sheet slides away.
    val shown = remember { arrayOfNulls<GroupDraft>(1) }
    if (draft != null) shown[0] = draft
    val current = draft ?: shown[0] ?: GroupDraft()
    SsBottomSheet(visible = draft != null, onDismissRequest = callbacks.onDismissCreate, dismissible = !current.submitting) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg).testTag(GroupsTags.CREATE_SHEET),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BasicText(stringResource(R.string.groups_new_title), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
            SsTextField(
                value = current.name,
                onValueChange = callbacks.onCreateNameChange,
                label = stringResource(R.string.groups_name_label),
                placeholder = stringResource(R.string.groups_name_placeholder),
                error = current.error?.label(),
                emoji = "👯",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { callbacks.onConfirmCreate() }),
                modifier = Modifier.testTag(SplitSmartTestTags.GROUP_NAME_FIELD),
            )
            ChunkyButton(
                stringResource(R.string.groups_create_button),
                callbacks.onConfirmCreate,
                Modifier.fillMaxWidth().testTag(SplitSmartTestTags.DIALOG_CONFIRM),
                loading = current.submitting,
            )
        }
    }
}

@Composable
private fun NameSheet(draft: NameDraft?, profile: ProfileCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val shown = remember { arrayOfNulls<NameDraft>(1) }
    if (draft != null) shown[0] = draft
    val current = draft ?: shown[0] ?: NameDraft()
    SsBottomSheet(visible = draft != null, onDismissRequest = profile.onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BasicText(stringResource(R.string.profile_title), Modifier.semantics { heading() }, style = type.title.copy(color = colors.text))
            BasicText(stringResource(R.string.profile_body), style = type.body.copy(color = colors.textSecondary))
            SsTextField(
                value = current.name,
                onValueChange = profile.onChange,
                label = stringResource(R.string.member_name_label),
                error = current.error?.label(),
                emoji = "🙋",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { profile.onSave() }),
                modifier = Modifier.testTag(SplitSmartTestTags.YOUR_NAME_FIELD),
            )
            ChunkyButton(stringResource(R.string.action_save), profile.onSave, Modifier.fillMaxWidth().testTag(SplitSmartTestTags.DIALOG_CONFIRM))
        }
    }
}
