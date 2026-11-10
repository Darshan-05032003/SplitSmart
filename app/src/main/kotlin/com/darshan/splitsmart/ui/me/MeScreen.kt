package com.darshan.splitsmart.ui.me

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.components.label
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSectionHeader
import com.darshan.splitsmart.ui.designsystem.components.SsSwitchRow
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.StatusKind
import com.darshan.splitsmart.ui.designsystem.components.StatusPill
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.navigation.HideShellBarWhile
import com.darshan.splitsmart.ui.navigation.LocalBottomBarInset
import com.darshan.splitsmart.ui.navigation.ScrollToTopOnReselect
import com.darshan.splitsmart.ui.navigation.ShellRoute
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

object MeTags {
    const val ROOT = "me_root"
    const val PROFILE = "me_profile"
    const val NAME_FIELD = "me_name_field"
    const val NAME_SAVE = "me_name_save"
    const val HAPTICS = "me_haptics"
    const val REDUCE_MOTION = "me_reduce_motion"
    const val AI = "me_ai"
    const val AI_HINT = "me_ai_hint"
    const val INTRO = "me_intro"
    fun theme(mode: ThemeMode) = "me_theme_${mode.name.lowercase()}"
}

/** Everything the Me tab can ask for. */
data class MeCallbacks(
    val onEditName: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onSaveName: () -> Unit = {},
    val onDismissName: () -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onReplayIntro: () -> Unit = {},
)

@Composable
fun MeRoute(onReplayIntro: () -> Unit, viewModel: MeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeScreen(
        state,
        MeCallbacks(
            onEditName = viewModel::openNameEditor,
            onNameChange = viewModel::onNameChange,
            onSaveName = viewModel::saveName,
            onDismissName = viewModel::dismissNameEditor,
            onHaptics = viewModel::setHaptics,
            onReduceMotion = viewModel::setReduceMotion,
            onTheme = viewModel::setTheme,
            onReplayIntro = onReplayIntro,
        ),
    )
}

/**
 * The Me tab: who you are in your groups, how the app feels, and plain facts about what it does (AI reading on or off in this
 * build, what UPI settle-up does). No primary button on the page itself: the name sheet has the one main action.
 */
@Composable
fun MeScreen(state: MeUiState, callbacks: MeCallbacks, scroll: ScrollState = rememberScrollState()) {
    val colors = SplitSmartTheme.colors
    ScrollToTopOnReselect(ShellRoute.Me, scroll)
    // The name sheet is drawn inside this screen: the floating bar steps aside so it does not cover the sheet's button.
    HideShellBarWhile(state.nameDraft != null)
    Box(Modifier.fillMaxSize().testTag(MeTags.ROOT)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            SsTopBar(stringResource(R.string.me_title), large = true)
            ProfileCard(state.name, callbacks.onEditName)
            Spacer(Modifier.height(Spacing.lg))
            SsSectionHeader(stringResource(R.string.me_feel), emoji = "✨")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SsSwitchRow(
                    stringResource(R.string.me_haptics), state.settings.hapticsEnabled, callbacks.onHaptics,
                    Modifier.testTag(MeTags.HAPTICS), description = stringResource(R.string.me_haptics_hint),
                )
                SsSwitchRow(
                    stringResource(R.string.me_reduce_motion), state.settings.reduceMotion, callbacks.onReduceMotion,
                    Modifier.testTag(MeTags.REDUCE_MOTION), description = stringResource(R.string.me_reduce_motion_hint),
                )
                ThemeChoice(state.settings.theme, callbacks.onTheme)
            }
            Spacer(Modifier.height(Spacing.lg))
            SsSectionHeader(stringResource(R.string.me_facts), emoji = "💡")
            SsListRow(
                title = stringResource(R.string.me_ai_title),
                subtitle = stringResource(if (state.aiReading) R.string.me_ai_on else R.string.me_ai_off),
                subtitleMaxLines = Int.MAX_VALUE,
                trailing = {
                    StatusPill(
                        if (state.aiReading) StatusKind.Ai else StatusKind.Neutral,
                        stringResource(if (state.aiReading) R.string.me_ai_on_pill else R.string.me_ai_off_pill),
                        animate = false,
                    )
                },
                modifier = Modifier.testTag(MeTags.AI),
            )
            if (!state.aiReading) {
                // For whoever builds the app from source: the review card only says AI reading is off, this says how to turn it on.
                BasicText(
                    stringResource(R.string.me_ai_setup_hint),
                    Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.xs).testTag(MeTags.AI_HINT),
                    style = SplitSmartTheme.type.label.copy(color = colors.textSecondary),
                )
            }
            SsListRow(title = stringResource(R.string.me_upi_title), subtitle = stringResource(R.string.me_upi_body), subtitleMaxLines = Int.MAX_VALUE)
            SsListRow(
                title = stringResource(R.string.me_intro),
                leading = { BasicText("🎬", style = SplitSmartTheme.type.title.copy(color = colors.text)) },
                onClick = callbacks.onReplayIntro,
                minHeight = Sizes.listRowCompact,
                modifier = Modifier.testTag(MeTags.INTRO),
            )
            // The floating bar and the system navigation bar sit over the end of the page.
            Spacer(Modifier.navigationBarsPadding().height(LocalBottomBarInset.current + Spacing.md))
        }
        NameSheet(state.nameDraft, callbacks)
    }
}

@Composable
private fun ProfileCard(name: String?, onEdit: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val editLabel = stringResource(R.string.me_name_edit)
    ChunkyCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag(MeTags.PROFILE),
        onClick = onEdit,
        onClickLabel = editLabel,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(name ?: "?", size = Sizes.avatarLarge, contentDescription = null)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                BasicText(
                    name ?: stringResource(R.string.me_name_empty),
                    style = type.title.copy(color = if (name == null) colors.accent else colors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(stringResource(R.string.me_name_hint), style = type.label.copy(color = colors.textSecondary))
            }
        }
    }
}

@Composable
private fun ThemeChoice(selected: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val colors = SplitSmartTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BasicText(stringResource(R.string.me_theme), style = SplitSmartTheme.type.bodyStrong.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        // Wraps on narrow phones and at large text sizes instead of pushing a chip off screen.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for ((mode, label, emoji) in listOf(
                Triple(ThemeMode.System, R.string.me_theme_system, "📱"),
                Triple(ThemeMode.Light, R.string.me_theme_light, "☀️"),
                Triple(ThemeMode.Dark, R.string.me_theme_dark, "🌙"),
            )) {
                EmojiChip(
                    stringResource(label), selected = selected == mode, onClick = { onTheme(mode) },
                    modifier = Modifier.testTag(MeTags.theme(mode)), emoji = emoji, exclusive = true,
                )
            }
        }
    }
}

@Composable
private fun NameSheet(draft: NameEdit?, callbacks: MeCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    SsBottomSheet(visible = draft != null, onDismissRequest = callbacks.onDismissName) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BasicText(stringResource(R.string.profile_title), style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.profile_body), style = type.body.copy(color = colors.textSecondary))
            SsTextField(
                value = draft?.name.orEmpty(),
                onValueChange = callbacks.onNameChange,
                label = stringResource(R.string.member_name_label),
                error = draft?.error?.label(),
                emoji = "🙋",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { callbacks.onSaveName() }),
                modifier = Modifier.fillMaxWidth().testTag(MeTags.NAME_FIELD),
            )
            ChunkyButton(stringResource(R.string.action_save), callbacks.onSaveName, Modifier.fillMaxWidth().testTag(MeTags.NAME_SAVE))
        }
    }
}
