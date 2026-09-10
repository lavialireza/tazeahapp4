package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.SectionEntity
import com.example.bookapp.data.TaziehRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RehearsalViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loadedForRoleId: Long? = null, val sections: List<SectionEntity> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded(roleId: Long) {
        if (_state.value.loadedForRoleId == roleId) return
        viewModelScope.launch {
            val sections = repo.getSectionsByRole(roleId)
            _state.update { it.copy(loadedForRoleId = roleId, sections = sections) }
        }
    }
}
