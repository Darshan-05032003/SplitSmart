package com.darshan.splitsmart.domain.model

import kotlinx.serialization.Serializable

enum class SplitMode { EQUAL, BY_ITEM, BY_SHARE }

/** How a bill is split; stored as JSON on the bill so the split can be edited later. */
@Serializable
data class SplitConfig(
    val mode: SplitMode,
    val participantIds: List<String>,
    /** itemId → members who share that item; an item missing here (or with an empty set) is shared by every participant. */
    val itemAssignments: Map<String, Set<String>> = emptyMap(),
    /** memberId → weight or percentage (any non-negative integers; only ratios matter). */
    val shares: Map<String, Int> = emptyMap(),
)

/** One participant's part of a bill, in paise. `adjustmentPaise` carries rounding and a total that does not add up. */
data class MemberShare(
    val memberId: String,
    val itemsPaise: Long,
    val taxPaise: Long,
    val tipPaise: Long,
    val adjustmentPaise: Long,
) {
    val totalPaise: Long get() = itemsPaise + taxPaise + tipPaise + adjustmentPaise
}

data class SplitResult(val mode: SplitMode, val shares: List<MemberShare>) {
    val totalPaise: Long get() = shares.sumOf { it.totalPaise }
}

data class BillSplit(val config: SplitConfig, val shares: List<MemberShare>)

/** `fromMemberId` pays `amountPaise` to `toMemberId`. */
data class Debt(val fromMemberId: String, val toMemberId: String, val amountPaise: Long)

enum class SplitError { NO_PARTICIPANTS, NO_SHARES, NEGATIVE_SHARE, DUPLICATE_PARTICIPANT }

data class SplitException(val reason: SplitError) : Exception(reason.name)
