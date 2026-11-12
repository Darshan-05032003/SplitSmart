package com.darshan.splitsmart.ui.undo

import com.darshan.splitsmart.domain.repository.Removal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one delete that can still be undone, app-wide and in memory only. A screen that deletes something hands what it took to
 * [offer] and leaves; the shell, above every screen, shows "… deleted · Undo" wherever the person lands. A newer delete replaces
 * the offer (the older one stands). [take] ends an offer exactly once, whether by Undo or by the snackbar timing out.
 */
@Singleton
class UndoCenter @Inject constructor() {
    private val current = MutableStateFlow<Removal?>(null)
    val offer: StateFlow<Removal?> = current.asStateFlow()

    fun offer(removal: Removal) {
        current.value = removal
    }

    /** True for the first caller only: one Undo, or one timeout, ends the offer. */
    fun take(removal: Removal): Boolean = current.compareAndSet(removal, null)
}
