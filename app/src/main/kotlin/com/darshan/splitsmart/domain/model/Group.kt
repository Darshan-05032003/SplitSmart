package com.darshan.splitsmart.domain.model

import java.time.Instant

data class Group(
    val id: String,
    val name: String,
    val createdAt: Instant,
)

/** A group as shown in the list: the group plus the counts that come from Room joins. */
data class GroupSummary(
    val group: Group,
    val memberCount: Int,
    val billCount: Int,
)

data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    /** Optional UPI handle used for settle-up links; stored lowercase. */
    val vpa: String?,
    val createdAt: Instant,
)
