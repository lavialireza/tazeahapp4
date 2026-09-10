package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.SearchResult
import com.example.bookapp.data.TaziehRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookmarksViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val items: List<SearchResult> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repo.getResultsByIds(repo.getBookmarks().toList())
            _state.update { it.copy(loading = false, items = items) }
        }
    }
}
