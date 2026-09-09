package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.ListItemData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaziehsViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadedForFieldId: Long? = null,
        val items: List<ListItemData> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded(fieldId: Long) {
        if (_state.value.loadedForFieldId == fieldId) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repo.getTaziehsByField(fieldId).map { ListItemData(it.id, it.title) }
            _state.update { it.copy(loading = false, loadedForFieldId = fieldId, items = items) }
        }
    }
}
