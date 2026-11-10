package com.darshan.splitsmart.integration

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.MainActivity
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import com.darshan.splitsmart.ui.onboarding.OnboardingTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Full Hilt graph: a fresh install opens on the intro, skipping it lands on the groups list, and the next launch goes straight there. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class OnboardingFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var onboarding: OnboardingRepository
    @Inject lateinit var uiPreferences: UiPreferences

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            onboarding.setCompleted(false)
            // The intro's art bobs forever under full motion, and an auto-advancing test clock would never go idle.
            uiPreferences.setReduceMotion(true)
        }
    }

    @Test
    fun `a fresh install shows the intro once, skip leads to the groups list, the next launch goes straight there`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(OnboardingTags.SKIP) }
            compose.onNodeWithTag(OnboardingTags.SKIP).performClick()
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
            compose.onNodeWithTag(OnboardingTags.PAGER).assertDoesNotExist()
        }
        assertThat(runBlocking { onboarding.completed.first() }).isTrue()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
            compose.onNodeWithTag(OnboardingTags.PAGER).assertDoesNotExist()
        }
    }
}
