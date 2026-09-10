package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.DialogueSearchResult
import com.example.bookapp.data.FieldEntity
import com.example.bookapp.data.SearchResult
import com.example.bookapp.data.TaziehEntity
import com.example.bookapp.data.TaziehRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val fields: List<FieldEntity> = emptyList(),
        val allTaziehs: List<TaziehEntity> = emptyList(),
        val bookmarkedIds: Set<Long> = emptySet()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val fields = repo.getFields()
            val allTaziehs = repo.getAllTaziehs()
            _state.update { it.copy(loaded = true, fields = fields, allTaziehs = allTaziehs, bookmarkedIds = repo.getBookmarks()) }
        }
    }

    suspend fun search(query: String, fieldId: Long?, taziehId: Long?): List<SearchResult> =
        repo.search(query, fieldId, taziehId)

    suspend fun searchDialogues(query: String): List<DialogueSearchResult> = repo.searchDialogues(query)

    fun isBookmarked(sectionId: Long): Boolean = sectionId in _state.value.bookmarkedIds

    fun toggleBookmark(sectionId: Long) {
        repo.toggleBookmark(sectionId)
        _state.update { it.copy(bookmarkedIds = repo.getBookmarks()) }
    }
}
