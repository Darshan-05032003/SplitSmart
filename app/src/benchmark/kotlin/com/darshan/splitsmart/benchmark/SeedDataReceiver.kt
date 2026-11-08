package com.darshan.splitsmart.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Inserts [GROUPS] groups through the repositories, the newest being "[MAIN_GROUP]" with [MAIN_BILLS] bills, so the scroll
 * benchmarks have lists to scroll. Safe to send repeatedly.
 *
 * `adb shell am broadcast -n com.darshan.splitsmart/.benchmark.SeedDataReceiver` replies `seeded=<groups>`.
 */
class SeedDataReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun groupRepository(): GroupRepository
        fun billRepository(): BillRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seed(dependencies.groupRepository(), dependencies.billRepository())
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = "seeded=$GROUPS"
            } catch (e: Exception) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = "failed=${e.javaClass.simpleName}"
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(groups: GroupRepository, bills: BillRepository) {
        // The main group is written last, so a complete one means an earlier run finished.
        val done = groups.observeGroupSummaries().first().any { it.group.name == MAIN_GROUP && it.billCount >= MAIN_BILLS }
        if (done) return
        val now = Instant.now()
        repeat(GROUPS - 1) { index ->
            val members = createGroup(groups, "Group ${index + 1}")
            bills.saveBill(bill(members, index, now))
        }
        delay(NEWEST_GAP_MS) // Sorts the main group above the others (newest first, millisecond timestamps).
        val members = createGroup(groups, MAIN_GROUP)
        repeat(MAIN_BILLS) { index -> bills.saveBill(bill(members, index, now)) }
    }

    private suspend fun createGroup(groups: GroupRepository, name: String): List<Member> {
        val group = groups.createGroup(name)
        return MEMBERS.map { groups.addMember(group.id, it, null) }
    }

    private fun bill(members: List<Member>, index: Int, now: Instant): Bill {
        val total = (index * 137L % 4_000L + 100L) * 100L
        val split = index % 2 == 0
        return Bill(
            id = UUID.randomUUID().toString(),
            groupId = members.first().groupId,
            title = "${TITLES[index % TITLES.size]} ${index + 1}",
            totalPaise = total,
            taxPaise = 0,
            tipPaise = 0,
            paidByMemberId = members[index % members.size].id,
            source = BillSource.MANUAL,
            status = if (split) BillStatus.SPLIT else BillStatus.DRAFT,
            createdAt = now.minus(Duration.ofHours(index.toLong())),
            items = emptyList(),
            split = if (split) equalSplit(members, total) else null,
        )
    }

    private fun equalSplit(members: List<Member>, total: Long): BillSplit {
        val each = total / members.size
        val remainder = total - each * members.size
        return BillSplit(
            config = SplitConfig(SplitMode.EQUAL, members.map { it.id }),
            shares = members.mapIndexed { position, member ->
                MemberShare(member.id, each + if (position == 0) remainder else 0L, taxPaise = 0, tipPaise = 0, adjustmentPaise = 0)
            },
        )
    }

    private companion object {
        const val GROUPS = 200
        const val MAIN_BILLS = 200
        const val MAIN_GROUP = "Benchmark trip"
        const val NEWEST_GAP_MS = 5L
        val MEMBERS = listOf("Asha Rao", "Vikram Shah", "Neha Menon", "Rahul Verma")
        val TITLES = listOf("Dinner", "Taxi", "Groceries", "Hotel", "Coffee", "Museum tickets", "Lunch", "Fuel")
    }
}
