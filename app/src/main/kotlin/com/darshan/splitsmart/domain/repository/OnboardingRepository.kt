package com.darshan.splitsmart.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether the three-page intro has been finished or skipped on this phone. Nothing here is sensitive. */
interface OnboardingRepository {
    /** Never throws. A store that cannot be read counts as done: the intro is help, never a gate in front of the app. */
    val completed: Flow<Boolean>

    /** A failed write is dropped; the intro then simply shows once more next launch. */
    suspend fun setCompleted(completed: Boolean)
}
