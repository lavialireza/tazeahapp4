package com.example.bookapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.TaziehIndexItem
import com.example.bookapp.ui.screens.sortTaziehIndexItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaziehIndexViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val items: List<TaziehIndexItem> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(taziehId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val roles = repo.getRolesByTazieh(taziehId)
            val items = roles.map { role ->
                val firstSection = repo.getSectionsByRole(role.id).firstOrNull()
                val firstVerse = firstSection?.content
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim() ?: ""
                TaziehIndexItem(roleId = role.id, roleTitle = role.title, firstVerse = firstVerse)
            }
            _state.update { it.copy(loading = false, items = items) }
        }
    }

    fun exportPdf(context: Context, taziehId: Long, taziehTitle: String) {
        viewModelScope.launch {
            val roles = repo.getRolesByTazieh(taziehId)
            val rolesWithSections = roles.map { role -> role.title to repo.getSectionsByRole(role.id) }
            repo.exportTaziehToPdf(context, taziehTitle, rolesWithSections)
        }
    }

    fun rename(taziehId: Long, item: TaziehIndexItem, newTitle: String) {
        viewModelScope.launch {
            repo.updateRoleTitle(item.roleId, newTitle)
            load(taziehId)
        }
    }

    fun move(taziehId: Long, index: Int, direction: Int) {
        viewModelScope.launch {
            val sorted = sortTaziehIndexItems(_state.value.items)
            val targetIndex = index + direction
            if (targetIndex in sorted.indices) {
                val roleA = repo.getRoleById(sorted[index].roleId)
                val roleB = repo.getRoleById(sorted[targetIndex].roleId)
                repo.updateRoleOrderIndex(roleA.id, roleB.orderIndex)
                repo.updateRoleOrderIndex(roleB.id, roleA.orderIndex)
                load(taziehId)
            }
        }
    }
}
