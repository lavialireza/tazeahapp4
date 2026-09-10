package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.SectionPickerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DialogueBuilderViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val allSections: List<SectionPickerItem> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded(taziehId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val roles = repo.getRolesByTazieh(taziehId)
            val allSections = roles.flatMap { role ->
                repo.getSectionsByRole(role.id).map { section ->
                    SectionPickerItem(section.id, role.title, section.title)
                }
            }
            _state.update { it.copy(loading = false, allSections = allSections) }
        }
    }

    /** بعد از ساخته‌شدنِ موفق گفتگو صدا زده می‌شود (برای popBackStack در UI) */
    fun save(taziehId: Long, title: String, orderedSectionIds: List<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createDialogue(taziehId, title, orderedSectionIds)
            onDone()
        }
    }
}
