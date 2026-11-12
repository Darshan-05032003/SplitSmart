package com.darshan.splitsmart.data.repository

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.GroupDao
import com.darshan.splitsmart.domain.repository.ExistingGroups
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One `COUNT(*)` on the groups table, archived ones included, off the main thread. */
class RoomExistingGroups @Inject constructor(
    private val groupDao: GroupDao,
    private val dispatchers: DispatcherProvider,
) : ExistingGroups {
    override suspend fun any(): Boolean = withContext(dispatchers.io) { groupDao.count() > 0 }
}
