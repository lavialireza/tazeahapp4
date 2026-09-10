package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.DialogueSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DialoguesViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val dialogues: List<DialogueSummary> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var currentTaziehId: Long? = null

    fun load(taziehId: Long) {
        currentTaziehId = taziehId
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val dialogues = repo.getDialoguesByTazieh(taziehId).map { d ->
                DialogueSummary(d.id, d.title, repo.getDialogueTurns(d.id).size)
            }
            _state.update { it.copy(loading = false, dialogues = dialogues) }
        }
    }

    fun delete(dialogue: DialogueSummary) {
        val taziehId = currentTaziehId ?: return
        viewModelScope.launch {
            repo.deleteDialogue(dialogue.id)
            load(taziehId)
        }
    }
}
