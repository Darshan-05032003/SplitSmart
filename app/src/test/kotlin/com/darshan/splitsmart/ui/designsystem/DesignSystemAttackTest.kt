package com.darshan.splitsmart.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Attacks on the design system (SECURITY_REVIEW "UI Feature: Design system"), kept as regression tests. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class DesignSystemAttackTest {
    @get:Rule val rule = createComposeRule()

    private val bidiSpoof = "Ravi ‮eno‬ paid ⁦₹1⁩"

    @Test
    fun `a name with bidi overrides cannot reorder a row or the top bar`() {
        rule.setDs {
            androidx.compose.foundation.layout.Column {
                SsTopBar(bidiSpoof)
                SsListRow(bidiSpoof, subtitle = "line one\nline two\u0007", modifier = Modifier.testTag("row"))
            }
        }
        val texts = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertThat(texts).containsExactly("Ravi eno paid ₹1", "line one line two")
        assertThat(rule.onAllNodesWithText("Ravi eno paid ₹1", useUnmergedTree = true).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun `a ten-thousand-character name stays on one line inside its row`() {
        val huge = "Asha".repeat(2_500) + "🎉".repeat(500)
        rule.setDs { Box(Modifier.width(320.dp).testTag("box")) { SsListRow(huge, modifier = Modifier.testTag("row")) } }
        val title = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first().text
        assertThat(title.length).isAtMost(122)
        rule.onNodeWithTag("row").assertInside(rule.onNodeWithTag("box"))
    }

    @Test
    fun `hostile names in a stack are cleaned before TalkBack reads them`() {
        rule.setDs { AvatarStack(listOf("A‮sha", "Ra\nvi", "\u0000"), Modifier.testTag("stack")) }
        val description = rule.onNodeWithTag("stack").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
        assertThat(description).isEqualTo("Asha, Ra vi and Someone")
    }

    @Test
    fun `reduced motion keeps the undo step and its full window`() {
        val state = SsSnackbarState()
        var result: SnackbarResult? = null
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            LaunchedEffect(Unit) { result = state.show("Group archived", actionLabel = "Undo") }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SsSnackbarHost(state) }
        }
        rule.mainClock.advanceTimeBy(4_500)
        assertThat(result).isNull()
        rule.onNodeWithText("Undo").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertThat(result).isEqualTo(SnackbarResult.Undo)
    }
}
