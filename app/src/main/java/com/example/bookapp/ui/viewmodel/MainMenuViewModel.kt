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

class MainMenuViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val randomVerse: SearchResult? = null,
        val recentItems: List<SearchResult> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val random = repo.getRandomSection()
            val ids = repo.getRecentIds()
            val recent = if (ids.isEmpty()) emptyList() else {
                val byId = repo.getResultsByIds(ids).associateBy { it.sectionId }
                ids.mapNotNull { byId[it] }
            }
            _state.update { it.copy(loaded = true, randomVerse = random, recentItems = recent) }
        }
    }
}
