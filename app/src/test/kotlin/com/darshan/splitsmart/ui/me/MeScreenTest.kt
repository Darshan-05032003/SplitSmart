package com.darshan.splitsmart.ui.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MeScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callbacks = MeCallbacks(
        onEditName = { calls += "edit" },
        onNameChange = { calls += "name:$it" },
        onSaveName = { calls += "save" },
        onDismissName = { calls += "dismiss" },
        onHaptics = { calls += "haptics:$it" },
        onReduceMotion = { calls += "reduce:$it" },
        onTheme = { calls += "theme:$it" },
        onReplayIntro = { calls += "intro" },
    )

    private fun show(state: MeUiState, fontScale: Float = 1f, reduceMotion: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setDs(fontScale = fontScale, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { MeScreen(state, callbacks) }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun `shows your name, the switches as they are and the chosen theme`() {
        show(MeUiState(name = "Asha", settings = UiSettings(hapticsEnabled = true, reduceMotion = false, theme = ThemeMode.Dark)))
        rule.onNodeWithText("Asha").assertExists()
        rule.onNodeWithTag(MeTags.HAPTICS).assertIsOn()
        rule.onNodeWithTag(MeTags.REDUCE_MOTION).assertIsOff()
        rule.onNodeWithTag(MeTags.theme(ThemeMode.Dark)).assertIsSelected()
        rule.onNodeWithTag(MeTags.theme(ThemeMode.System)).assertIsNotSelected()
    }

    @Test
    fun `without a name it asks for one`() {
        show(MeUiState(name = null))
        rule.onNodeWithText("Add your name").assertExists()
        rule.onNodeWithTag(MeTags.PROFILE).performClick()
        assertThat(calls).contains("edit")
    }

    @Test
    fun `each control reports what it changes`() {
        show(MeUiState(name = "Asha"))
        rule.onNodeWithTag(MeTags.HAPTICS).performClick()
        rule.onNodeWithTag(MeTags.REDUCE_MOTION).performClick()
        rule.onNodeWithTag(MeTags.theme(ThemeMode.Light)).performClick()
        rule.onNodeWithTag(MeTags.INTRO).scrolledTo().performClick()
        assertThat(calls).containsAtLeast("haptics:false", "reduce:true", "theme:Light", "intro").inOrder()
    }

    @Test
    fun `AI reading says on or off honestly, from the build`() {
        show(MeUiState(aiReading = false))
        rule.onNodeWithText("no Gemini API key", substring = true).assertExists()
        rule.onNodeWithContentDescription("Off").assertExists()
        // The developer's setup hint lives here (the review card only says AI reading is off).
        rule.onNodeWithText("LLM_API_KEY", substring = true).assertExists()
    }

    @Test
    fun `AI reading on says the receipt text goes to Gemini`() {
        show(MeUiState(aiReading = true))
        rule.onNodeWithText("sent to Gemini", substring = true).assertExists()
        rule.onNodeWithContentDescription("On").assertExists()
        rule.onNodeWithText("LLM_API_KEY", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the name sheet shows the draft and its error, and saves`() {
        show(MeUiState(name = "Asha", nameDraft = NameEdit("", NameError.BLANK)))
        rule.onNodeWithText("Enter a name").assertExists()
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        rule.onNodeWithTag(MeTags.NAME_SAVE).performClick()
        assertThat(calls).contains("save")
    }

    @Test
    fun `at 1_3x text the page stays inside the screen`() {
        show(MeUiState(name = "Asha Raghunathan Venkataraman", aiReading = true), fontScale = 1.3f)
        val root = rule.onNodeWithTag(ROOT)
        rule.onNodeWithTag(MeTags.PROFILE).assertInside(root)
        rule.onNodeWithTag(MeTags.AI).scrolledTo().assertInside(root)
        rule.onNodeWithTag(MeTags.INTRO).scrolledTo().assertInside(root)
    }

    /**
     * Scrolls the page to this node. The page's scroll animates, and an animation never ends on the paused test clock (the
     * scroll action would wait forever), so the clock runs for the scroll and is paused again after.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrolledTo(): androidx.compose.ui.test.SemanticsNodeInteraction {
        rule.mainClock.autoAdvance = true
        performScrollTo()
        rule.mainClock.autoAdvance = false
        return this
    }

    @Test
    fun `reduced motion changes nothing about what is offered`() {
        show(MeUiState(name = "Asha"), reduceMotion = true)
        rule.onNodeWithTag(MeTags.HAPTICS).assertIsOn()
        rule.onNodeWithTag(MeTags.INTRO).scrolledTo().performClick()
        assertThat(calls).contains("intro")
    }

    private companion object {
        const val ROOT = "root"
    }
}
