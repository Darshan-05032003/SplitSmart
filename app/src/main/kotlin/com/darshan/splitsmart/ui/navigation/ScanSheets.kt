package com.darshan.splitsmart.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

object ShellTags {
    const val BAR = "shell_bar"
    const val SCAN_PICKER = "shell_scan_picker"
    const val SCAN_NEEDS_GROUP = "shell_scan_needs_group"
    const val SCAN_CREATE_GROUP = "shell_scan_create_group"
    fun scanChoice(groupId: String) = "shell_scan_choice_$groupId"
}

/**
 * "Which group is this receipt for?": every group, newest first, scrolling inside the sheet when there are many. [title] asks
 * the question (the groups home asks it for an expense typed by hand).
 */
@Composable
fun ScanPickerContent(choices: List<ScanChoice>, title: String = stringResource(R.string.groups_pick_for_scan), onPick: (String) -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(Modifier.fillMaxWidth().testTag(ShellTags.SCAN_PICKER), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BasicText(
            title,
            style = type.title.copy(color = colors.text),
            modifier = Modifier.padding(horizontal = Spacing.screen).semantics { heading() },
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = Sizes.sheetListMax), contentPadding = PaddingValues(bottom = Spacing.md)) {
            items(choices, key = { it.groupId }, contentType = { "group" }) { choice ->
                SsListRow(
                    title = choice.name,
                    leading = { MemberAvatar(choice.name, contentDescription = null) },
                    onClick = { onPick(choice.groupId) },
                    minHeight = Sizes.listRowCompact,
                    modifier = Modifier.testTag(ShellTags.scanChoice(choice.groupId)),
                )
            }
        }
    }
}

/** Scan with no groups yet: explain why a group comes first, and offer to make one. */
@Composable
fun ScanNeedsGroupContent(onCreateGroup: () -> Unit) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag(ShellTags.SCAN_NEEDS_GROUP),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Illustration(IllustrationKind.Groups, size = Sizes.illustrationSmall)
        BasicText(
            stringResource(R.string.scan_needs_group_title),
            style = type.title.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.scan_needs_group_body), style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
        ChunkyButton(
            stringResource(R.string.groups_add),
            onCreateGroup,
            Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag(ShellTags.SCAN_CREATE_GROUP),
            emoji = "👯",
        )
    }
}
