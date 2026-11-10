package com.darshan.splitsmart.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the shell itself needs: the groups the bar's Scan can open the camera for (null until loaded). */
@HiltViewModel
class ShellViewModel @Inject constructor(groups: GroupRepository) : ViewModel() {
    val scanChoices: StateFlow<List<ScanChoice>?> = groups.observeGroupSummaries()
        .map { summaries -> summaries.map { ScanChoice(it.group.id, it.group.name) } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
