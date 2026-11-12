package com.darshan.splitsmart.domain.repository

/**
 * What a delete took away, held in memory for the few seconds Undo is offered. Never written anywhere: if the app's process
 * dies in that window, the delete simply stands.
 */
sealed interface Removal {
    /** The group's name or the bill's title, for "… deleted". */
    val name: String
    val rows: RemovedRows

    data class Group(val groupId: String, override val name: String, override val rows: RemovedRows) : Removal

    data class Bill(val billId: String, val groupId: String, override val name: String, override val rows: RemovedRows) : Removal
}

/** Every row a delete removed, exactly as it was stored. Only the data layer reads it. */
interface RemovedRows

/**
 * Deletes that can be undone. A delete reads everything the database would cascade away and deletes it in one transaction; a
 * restore writes the same rows back in one transaction, so either all of it comes back as it was or none of it does.
 */
interface TrashRepository {
    /** The group, its members, bills (lines and shares), settled payments and unsaved scans. Null if it was already gone. */
    suspend fun removeGroup(groupId: String): Removal.Group?

    /** The bill with its lines and shares. Null if it was already gone. */
    suspend fun removeBill(billId: String): Removal.Bill?

    /**
     * Puts the rows back exactly. False, with nothing written, when they can't go back as they were: the bill's group was
     * deleted meanwhile, a person on the bill was removed, or a row with the same id appeared.
     */
    suspend fun restore(removal: Removal): Boolean
}
