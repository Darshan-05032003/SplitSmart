package com.darshan.splitsmart.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * "Who am I" for balances. Groups only know member names, so the home screen matches this name (case-insensitive)
 * against each group's members to show "you owe" / "you are owed". Null = not set yet.
 */
interface ProfileRepository {
    val yourName: Flow<String?>
    suspend fun setYourName(name: String?)
}
