package com.example.bookapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.ListItemData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SectionsViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadedForRoleId: Long? = null,
        val items: List<ListItemData> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded(roleId: Long) {
        if (_state.value.loadedForRoleId == roleId) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repo.getSectionsByRole(roleId).map { ListItemData(it.id, it.title) }
            _state.update { it.copy(loading = false, loadedForRoleId = roleId, items = items) }
        }
    }

    /** context اینجا فقط برای همین یک فراخوانی استفاده می‌شود، نه اینکه نگه داشته شود (به توضیح TaziehRepository نگاه کنید) */
    fun exportPdf(context: Context, roleId: Long, roleTitle: String) {
        viewModelScope.launch {
            val sections = repo.getSectionsByRole(roleId)
            repo.exportRoleToPdf(context, roleTitle, sections)
        }
    }
}
