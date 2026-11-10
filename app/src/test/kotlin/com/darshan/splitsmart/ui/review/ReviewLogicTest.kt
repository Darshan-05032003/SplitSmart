package com.darshan.splitsmart.ui.review

import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewLogicTest {
    private fun row(key: String, name: String, qty: String = "1", amount: String = "100.00") = ItemRow(key, name, qty, amount)

    @Test
    fun `rows the AI kept as they were do not type in again`() {
        val draft = listOf(row("a", "Paneer Tikka", amount = "250.00"), row("b", "Butter Naan", "2", "90.00"), row("c", "Lassi", amount = "60"))
        val ai = listOf(
            row("x", "paneer tikka ", amount = "250"), // same item: case, spaces and "250" vs "250.00" do not matter
            row("y", "Butter Naan", "2", "95.00"), // the AI read a different price
            row("z", "Sweet Lassi", amount = "60.00"), // and a better name
            row("w", "Service charge", amount = "20.00"), // and a row the parser missed
        )
        assertThat(ItemRevealLogic.changedKeys(previous = draft, next = ai)).containsExactly("y", "z", "w").inOrder()
    }

    @Test
    fun `a duplicated line counts once per copy`() {
        val previous = listOf(row("a", "Chai"))
        val next = listOf(row("x", "Chai"), row("y", "Chai"))
        assertThat(ItemRevealLogic.changedKeys(previous, next)).containsExactly("y")
    }

    @Test
    fun `a fresh read types every row`() {
        val next = listOf(row("x", "Chai"), row("y", "Samosa"))
        assertThat(ItemRevealLogic.changedKeys(emptyList(), next)).containsExactly("x", "y").inOrder()
    }

    @Test
    fun `rows resolve one after another, then the rest of a long receipt all at once`() {
        val step = MotionDurations.Micro.toLong()
        val type = MotionDurations.SmallSlow.toLong()
        assertThat(ItemRevealLogic.progress(order = 0, elapsedMs = 0)).isEqualTo(0f)
        assertThat(ItemRevealLogic.progress(order = 0, elapsedMs = type / 2)).isWithin(0.01f).of(0.5f)
        assertThat(ItemRevealLogic.progress(order = 0, elapsedMs = type)).isEqualTo(1f)
        // Row 1 is still a skeleton until its turn.
        assertThat(ItemRevealLogic.progress(order = 1, elapsedMs = step - 1)).isEqualTo(0f)
        assertThat(ItemRevealLogic.progress(order = 1, elapsedMs = step + type)).isEqualTo(1f)
        // Past the cap, rows share the last slot instead of queueing for seconds.
        val last = ItemRevealLogic.MAX_STAGGERED - 1
        assertThat(ItemRevealLogic.progress(order = 199, elapsedMs = last * step + type / 2))
            .isEqualTo(ItemRevealLogic.progress(order = last, elapsedMs = last * step + type / 2))
        assertThat(ItemRevealLogic.durationMs(200)).isEqualTo(last * step + type)
        assertThat(ItemRevealLogic.durationMs(1)).isEqualTo(type)
        assertThat(ItemRevealLogic.durationMs(0)).isEqualTo(0L)
    }

    @Test
    fun `the mismatch says which way the numbers are off`() {
        assertThat(ReviewLogic.mismatch(null)).isEqualTo(Mismatch.Unknown)
        assertThat(ReviewLogic.mismatch(0)).isEqualTo(Mismatch.AddsUp)
        assertThat(ReviewLogic.mismatch(1_250)).isEqualTo(Mismatch.Over(1_250))
        assertThat(ReviewLogic.mismatch(-500)).isEqualTo(Mismatch.Under(500))
    }
    @Test
    fun `no key is a normal state for this build, so the parser's card stays plain`() {
        assertThat(ReviewLogic.sourceTone(ExtractionSource.REGEX, refining = false, failure = LlmFailure.KEY_MISSING)).isEqualTo(SourceTone.Plain)
    }

    @Test
    fun `a reading the AI made, or one it is still refining, sits on a plain card`() {
        assertThat(ReviewLogic.sourceTone(ExtractionSource.LLM, refining = false, failure = null)).isEqualTo(SourceTone.Plain)
        assertThat(ReviewLogic.sourceTone(ExtractionSource.REGEX, refining = true, failure = LlmFailure.TIMEOUT)).isEqualTo(SourceTone.Plain)
    }

    @Test
    fun `a rejected key or a pin mismatch is a real failure and keeps the danger tone`() {
        for (failure in listOf(LlmFailure.UNAUTHORIZED, LlmFailure.INSECURE)) {
            assertThat(ReviewLogic.sourceTone(ExtractionSource.REGEX, refining = false, failure = failure)).isEqualTo(SourceTone.Danger)
        }
    }

    @Test
    fun `a failure that trying again can fix is a warning`() {
        val retryable = LlmFailure.entries - setOf(LlmFailure.KEY_MISSING, LlmFailure.UNAUTHORIZED, LlmFailure.INSECURE)
        assertThat(retryable).contains(LlmFailure.OFFLINE)
        for (failure in retryable) {
            assertThat(ReviewLogic.sourceTone(ExtractionSource.REGEX, refining = false, failure = failure)).isEqualTo(SourceTone.Warning)
        }
        assertThat(ReviewLogic.sourceTone(ExtractionSource.REGEX, refining = false, failure = null)).isEqualTo(SourceTone.Warning)
    }
}
