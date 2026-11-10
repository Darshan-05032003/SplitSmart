package com.darshan.splitsmart.domain.model

import java.time.Instant

/** How a settled payment was made, as the person who marked it said. SplitSmart never sees the money itself. */
enum class SettlementMethod {
    /** Pay opened their UPI app with the amount filled in, and they came back and said it went through. */
    UPI_APP,

    /** Cash, a bank transfer, or any way SplitSmart did not open. */
    OTHER,
}

/**
 * One payment someone marked as made: [fromName] paid [toName] [amountPaise]. Recorded only by an explicit "yes, it went
 * through" tap, never from a UPI app's reply. It counts in every balance like a bill in reverse (the payer's balance goes up,
 * the payee's down). The names are kept as they were, so the history still reads after a member leaves; their id then
 * becomes null and the payment stops counting, because their shares left with them.
 */
data class Settlement(
    val id: String,
    val groupId: String,
    val fromMemberId: String?,
    val toMemberId: String?,
    val fromName: String,
    val toName: String,
    val amountPaise: Long,
    val method: SettlementMethod,
    val createdAt: Instant,
) {
    init {
        require(amountPaise > 0) { "amount must be positive: $amountPaise" }
        require(fromName.isNotBlank() && toName.isNotBlank()) { "names are blank" }
        require(fromMemberId == null || fromMemberId != toMemberId) { "a member cannot pay themselves" }
    }

    /** Both people are still in the group, so the payment moves balances. */
    val counts: Boolean get() = fromMemberId != null && toMemberId != null
}
