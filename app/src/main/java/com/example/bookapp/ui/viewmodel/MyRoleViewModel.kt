package com.example.bookapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.MyRoleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyRoleViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val items: List<MyRoleItem> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val saved = repo.getAllMyRoles()
            val items = saved.mapNotNull { (taziehId, roleId) ->
                val tazieh = repo.getTaziehById(taziehId) ?: return@mapNotNull null
                val role = repo.getRoleByIdOrNull(roleId) ?: return@mapNotNull null
                MyRoleItem(taziehId = taziehId, taziehTitle = tazieh.title, roleId = roleId, roleTitle = role.title)
            }
            _state.update { it.copy(loading = false, items = items) }
        }
    }

    fun exportPdf(context: Context, item: MyRoleItem) {
        viewModelScope.launch {
            val sections = repo.getSectionsByRole(item.roleId)
            repo.exportRoleToPdf(context, item.roleTitle, sections)
        }
    }

    fun remove(item: MyRoleItem) {
        repo.clearMyRole(item.taziehId)
        load()
    }
}
