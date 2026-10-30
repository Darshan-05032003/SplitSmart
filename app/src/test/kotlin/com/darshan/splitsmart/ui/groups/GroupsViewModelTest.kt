package com.darshan.splitsmart.ui.groups

import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.usecase.CreateGroupUseCase
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.LoadState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.darshan.splitsmart.domain.model.GroupSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val repo = FakeGroupRepository()
    private val bills = com.darshan.splitsmart.testutil.FakeBillRepository()
    private val profile = com.darshan.splitsmart.data.settings.InMemoryProfileRepository()

    private fun vm(repository: FakeGroupRepository = repo) =
        GroupsViewModel(repository, bills, profile, CreateGroupUseCase(repository), TestDispatcherProvider(dispatcher))

    @Test
    fun `starts loading then shows empty when there are no groups`() = runTest(dispatcher) {
        vm().uiState.test {
            assertThat(awaitItem().groups).isEqualTo(LoadState.Loading)
            assertThat(awaitItem().groups).isEqualTo(LoadState.Empty)
        }
    }

    @Test
    fun `shows groups newest first with member counts`() = runTest(dispatcher) {
        repo.groups.value = listOf(testGroup("a", "Old", createdAt = java.time.Instant.EPOCH), testGroup("b", "New"))
        vm().uiState.test {
            awaitItem()
            val content = awaitItem().groups as LoadState.Content
            assertThat(content.data.map { it.group.name }).containsExactly("New", "Old").inOrder()
        }
    }

    @Test
    fun `create group emits Created event and closes the dialog`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.openCreateDialog()
            assertThat(awaitItem().draft).isNotNull()
            vm.onDraftNameChange("Trip")
            assertThat(awaitItem().draft!!.name).isEqualTo("Trip")
            vm.events.test {
                vm.submitCreate()
                val event = awaitItem()
                assertThat(event).isInstanceOf(GroupsEvent.GroupCreated::class.java)
            }
            // dialog closed, list updated
            cancelAndIgnoreRemainingEvents()
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.draft).isNull()
        assertThat((vm.uiState.value.groups as LoadState.Content).data.single().group.name).isEqualTo("Trip")
    }

    @Test
    fun `invalid name keeps the dialog open with an error`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openCreateDialog()
        vm.onDraftNameChange("   ")
        vm.submitCreate()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.draft!!.error).isEqualTo(NameError.BLANK)
        assertThat(repo.groups.value).isEmpty()
    }

    @Test
    fun `repository failure surfaces as an error state and retry reloads`() = runTest(dispatcher) {
        val failing = object : FakeGroupRepository() {
            var calls = 0
            override fun observeGroupSummaries(): Flow<List<GroupSummary>> = flow {
                if (calls++ == 0) throw IllegalStateException("boom") else emitAll(super.observeGroupSummaries())
            }
        }
        val vm = vm(failing)
        vm.uiState.test {
            awaitItem()
            assertThat(awaitItem().groups).isInstanceOf(LoadState.Error::class.java)
            vm.retry()
            assertThat(awaitItem().groups).isEqualTo(LoadState.Loading)
            assertThat(awaitItem().groups).isEqualTo(LoadState.Empty)
        }
    }

    @Test
    fun `double submit creates only one group`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.openCreateDialog()
        vm.onDraftNameChange("Trip")
        vm.submitCreate()
        vm.submitCreate()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(repo.groups.value).hasSize(1)
    }

    // ----- home overview -----
    private fun splitBill(id: String, groupId: String, payer: String, total: Long, shares: Map<String, Long>, at: java.time.Instant = com.darshan.splitsmart.testutil.FIXED_INSTANT) =
        com.darshan.splitsmart.testutil.testBill(id = id, groupId = groupId, totalPaise = total, items = listOf(com.darshan.splitsmart.testutil.testItem(billId = id, unitPaise = total)), paidByMemberId = payer, createdAt = at)
            .copy(
                status = com.darshan.splitsmart.domain.model.BillStatus.SPLIT,
                split = com.darshan.splitsmart.domain.model.BillSplit(
                    com.darshan.splitsmart.domain.model.SplitConfig(com.darshan.splitsmart.domain.model.SplitMode.EQUAL, shares.keys.toList()),
                    shares.map { (m, p) -> com.darshan.splitsmart.domain.model.MemberShare(m, p, 0, 0, 0) },
                ),
            )

    private fun seedTwoGroups() {
        repo.groups.value = listOf(testGroup("goa", "Goa trip", createdAt = java.time.Instant.EPOCH), testGroup("flat", "Flat 302", createdAt = java.time.Instant.EPOCH))
        repo.members.value = listOf(
            com.darshan.splitsmart.testutil.testMember("g-me", "goa", "Darshan"), com.darshan.splitsmart.testutil.testMember("g-asha", "goa", "Asha"),
            com.darshan.splitsmart.testutil.testMember("f-me", "flat", "darshan "), com.darshan.splitsmart.testutil.testMember("f-ravi", "flat", "Ravi"),
        )
        // Goa: I paid 300 split equally → Asha owes me 150. Flat: Ravi paid 200 split equally → I owe Ravi 100.
        bills.bills.value = listOf(
            splitBill("b1", "goa", payer = "g-me", total = 30_000, shares = mapOf("g-me" to 15_000L, "g-asha" to 15_000L)),
            splitBill("b2", "flat", payer = "f-ravi", total = 20_000, shares = mapOf("f-me" to 10_000L, "f-ravi" to 10_000L), at = java.time.Instant.EPOCH.plusSeconds(60)),
        )
    }

    @Test
    fun `cards carry members, last activity, outstanding money and your balance matched by name`() {
        seedTwoGroups()
        val cards = GroupsViewModel.buildCards(
            repo.groups.value.map { GroupSummary(it, 2, 1) }, repo.members.value, bills.bills.value, yourName = " DARSHAN",
        )
        val goa = cards.single { it.group.id == "goa" }
        val flat = cards.single { it.group.id == "flat" }
        assertThat(goa.memberNames).containsExactly("Darshan", "Asha").inOrder()
        assertThat(goa.outstandingPaise).isEqualTo(15_000)
        assertThat(goa.yourBalancePaise).isEqualTo(15_000)
        assertThat(flat.yourBalancePaise).isEqualTo(-10_000)
        assertThat(cards.first().group.id).isEqualTo("goa") // newest activity first (FIXED_INSTANT > EPOCH + 60 s)
        val overview = GroupsViewModel.overviewOf(cards, "Darshan")
        assertThat(overview.youAreOwedPaise).isEqualTo(15_000)
        assertThat(overview.youOwePaise).isEqualTo(10_000)
        assertThat(overview.outstandingPaise).isEqualTo(25_000)
        assertThat(overview.groupsWithYou).isEqualTo(2)
    }

    @Test
    fun `without a name there is no personal balance, only what is left to settle`() {
        seedTwoGroups()
        val cards = GroupsViewModel.buildCards(repo.groups.value.map { GroupSummary(it, 2, 1) }, repo.members.value, bills.bills.value, yourName = null)
        assertThat(cards.map { it.yourBalancePaise }).containsExactly(null, null)
        val overview = GroupsViewModel.overviewOf(cards, null)
        assertThat(overview.groupsWithYou).isEqualTo(0)
        assertThat(overview.outstandingPaise).isEqualTo(25_000)
        assertThat(overview.groupCount).isEqualTo(2)
    }

    @Test
    fun `a group without bills shows its creation as last activity and nothing outstanding`() {
        val g = testGroup("g", "New")
        val card = GroupsViewModel.buildCards(listOf(GroupSummary(g, 0, 0)), emptyList(), emptyList(), "Asha").single()
        assertThat(card.lastActivity).isEqualTo(g.createdAt)
        assertThat(card.outstandingPaise).isEqualTo(0)
        assertThat(card.yourBalancePaise).isNull() // Asha is not a member
    }

    @Test
    fun `ui state exposes the overview and updates when your name is saved`() = runTest(dispatcher) {
        seedTwoGroups()
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            val first = awaitItem()
            assertThat(first.overview.yourName).isNull()
            assertThat(first.overview.outstandingPaise).isEqualTo(25_000)
            vm.openNameDialog(null)
            assertThat(awaitItem().nameDraft).isEqualTo(NameDraft())
            vm.onNameDraftChange("Darshan")
            awaitItem()
            vm.saveName()
            var state = awaitItem()
            while (state.overview.yourName == null) state = awaitItem()
            assertThat(state.nameDraft).isNull()
            assertThat(state.overview.youAreOwedPaise).isEqualTo(15_000)
            assertThat(state.overview.youOwePaise).isEqualTo(10_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an invalid name keeps the dialog open and dismiss closes it`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.openNameDialog("Old")
            assertThat(awaitItem().nameDraft!!.name).isEqualTo("Old")
            vm.onNameDraftChange("  ")
            awaitItem()
            vm.saveName()
            assertThat(awaitItem().nameDraft!!.error).isEqualTo(NameError.BLANK)
            vm.dismissNameDialog()
            assertThat(awaitItem().nameDraft).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(profile.yourName.first()).isNull()
    }
}
