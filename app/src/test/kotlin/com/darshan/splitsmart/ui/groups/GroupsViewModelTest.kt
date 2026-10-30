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

    private fun vm(repository: FakeGroupRepository = repo) =
        GroupsViewModel(repository, CreateGroupUseCase(repository), TestDispatcherProvider(dispatcher))

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
}
