package com.example.bookapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.DialogueTurnDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DialogueReaderViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val dialogueTitle: String = "", val turns: List<DialogueTurnDisplay> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var currentDialogueId: Long? = null

    fun load(dialogueId: Long) {
        currentDialogueId = dialogueId
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val dialogue = repo.getDialogueById(dialogueId)
            val turnEntities = repo.getDialogueTurns(dialogueId)
            val turns = turnEntities.map { turn ->
                val section = repo.getSectionById(turn.sectionId)
                val role = repo.getRoleById(section.roleId)
                DialogueTurnDisplay(
                    turnId = turn.id,
                    sectionId = section.id,
                    roleTitle = role.title,
                    sectionTitle = section.title,
                    content = section.content
                )
            }
            _state.update { it.copy(loading = false, dialogueTitle = dialogue.title, turns = turns) }
        }
    }

    fun moveTurn(index: Int, direction: Int) {
        val dialogueId = currentDialogueId ?: return
        viewModelScope.launch {
            val turnEntities = repo.getDialogueTurns(dialogueId)
            val targetIndex = index + direction
            if (targetIndex in turnEntities.indices) {
                repo.swapDialogueTurnOrder(turnEntities[index], turnEntities[targetIndex])
                load(dialogueId)
            }
        }
    }

    fun deleteTurn(turn: DialogueTurnDisplay) {
        val dialogueId = currentDialogueId ?: return
        viewModelScope.launch {
            repo.deleteDialogueTurn(turn.turnId)
            load(dialogueId)
        }
    }

    fun exportPdf(context: Context) {
        viewModelScope.launch {
            val ui = _state.value
            val triples = ui.turns.map { Triple(it.roleTitle, it.sectionTitle, it.content) }
            repo.exportDialogueToPdf(context, ui.dialogueTitle, triples)
        }
    }
}
