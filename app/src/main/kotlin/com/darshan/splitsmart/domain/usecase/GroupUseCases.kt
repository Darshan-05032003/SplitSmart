package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.model.VpaValidation
import com.darshan.splitsmart.domain.model.VpaValidator
import com.darshan.splitsmart.domain.repository.GroupRepository
import javax.inject.Inject

sealed interface CreateGroupResult {
    data class Created(val group: Group) : CreateGroupResult
    data class InvalidName(val error: NameError) : CreateGroupResult
    data class Failed(val cause: Throwable) : CreateGroupResult
}

class CreateGroupUseCase @Inject constructor(private val repository: GroupRepository) {
    suspend operator fun invoke(rawName: String): CreateGroupResult {
        val name = when (val v = NameValidator.validate(rawName)) {
            is NameValidation.Invalid -> return CreateGroupResult.InvalidName(v.error)
            is NameValidation.Valid -> v.name
        }
        return runCatching { repository.createGroup(name) }
            .fold({ CreateGroupResult.Created(it) }, { CreateGroupResult.Failed(it) })
    }
}

sealed interface AddMemberResult {
    data class Added(val member: Member) : AddMemberResult
    data class InvalidName(val error: NameError) : AddMemberResult
    data class InvalidVpa(val error: VpaError) : AddMemberResult
    data object DuplicateName : AddMemberResult
    data object GroupFull : AddMemberResult
    data class Failed(val cause: Throwable) : AddMemberResult
}

class AddMemberUseCase @Inject constructor(private val repository: GroupRepository) {
    suspend operator fun invoke(groupId: String, rawName: String, rawVpa: String?): AddMemberResult {
        val name = when (val v = NameValidator.validate(rawName)) {
            is NameValidation.Invalid -> return AddMemberResult.InvalidName(v.error)
            is NameValidation.Valid -> v.name
        }
        val vpa = rawVpa?.trim()?.takeIf { it.isNotEmpty() }?.let {
            when (val v = VpaValidator.validate(it)) {
                is VpaValidation.Invalid -> return AddMemberResult.InvalidVpa(v.error)
                is VpaValidation.Valid -> v.vpa
            }
        }
        return runCatching {
            val existing = repository.getMembers(groupId)
            when {
                existing.size >= MAX_MEMBERS -> AddMemberResult.GroupFull
                existing.any { it.name.equals(name, ignoreCase = true) } -> AddMemberResult.DuplicateName
                else -> AddMemberResult.Added(repository.addMember(groupId, name, vpa))
            }
        }.getOrElse { AddMemberResult.Failed(it) }
    }

    companion object {
        const val MAX_MEMBERS = 50
    }
}
