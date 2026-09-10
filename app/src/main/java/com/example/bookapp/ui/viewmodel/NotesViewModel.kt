package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.NoteEntity
import com.example.bookapp.data.TaziehRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val notes: List<NoteEntity> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            _state.update { it.copy(loading = false, notes = repo.getAllNotes()) }
        }
    }

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repo.insertNote(title, content)
            load()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repo.deleteNote(id)
            load()
        }
    }
}
