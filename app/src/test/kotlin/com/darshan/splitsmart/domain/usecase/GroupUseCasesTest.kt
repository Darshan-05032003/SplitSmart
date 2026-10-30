package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GroupUseCasesTest {
    private val repo = FakeGroupRepository()

    @Test
    fun `create group validates and trims the name`() = runTest {
        val result = CreateGroupUseCase(repo)("  Goa  trip ")
        assertThat(result).isInstanceOf(CreateGroupResult.Created::class.java)
        assertThat((result as CreateGroupResult.Created).group.name).isEqualTo("Goa trip")
        assertThat(repo.groups.value).hasSize(1)
    }

    @Test
    fun `create group rejects invalid names without touching the repository`() = runTest {
        assertThat(CreateGroupUseCase(repo)("")).isEqualTo(CreateGroupResult.InvalidName(NameError.BLANK))
        assertThat(CreateGroupUseCase(repo)("x".repeat(100))).isEqualTo(CreateGroupResult.InvalidName(NameError.TOO_LONG))
        assertThat(repo.groups.value).isEmpty()
    }

    @Test
    fun `create group reports repository failures`() = runTest {
        repo.failWith = IllegalStateException("disk full")
        assertThat(CreateGroupUseCase(repo)("Trip")).isInstanceOf(CreateGroupResult.Failed::class.java)
    }

    @Test
    fun `add member validates name, vpa and duplicates`() = runTest {
        repo.groups.value = listOf(testGroup())
        val useCase = AddMemberUseCase(repo)
        val ok = useCase("g1", "Asha", " Asha@okicici ")
        assertThat(ok).isInstanceOf(AddMemberResult.Added::class.java)
        assertThat((ok as AddMemberResult.Added).member.vpa).isEqualTo("asha@okicici")

        assertThat(useCase("g1", "", null)).isEqualTo(AddMemberResult.InvalidName(NameError.BLANK))
        assertThat(useCase("g1", "Ravi", "bad vpa@ybl")).isEqualTo(AddMemberResult.InvalidVpa(VpaError.INVALID_CHARS))
        assertThat(useCase("g1", "asha", null)).isEqualTo(AddMemberResult.DuplicateName)
        assertThat(useCase("g1", "Ravi", "")).isInstanceOf(AddMemberResult.Added::class.java)
        assertThat(repo.members.value.last().vpa).isNull()
    }

    @Test
    fun `add member caps the group size`() = runTest {
        repo.members.value = (0 until AddMemberUseCase.MAX_MEMBERS).map { testMember(id = "m$it", name = "Member $it") }
        assertThat(AddMemberUseCase(repo)("g1", "One more", null)).isEqualTo(AddMemberResult.GroupFull)
    }
}
