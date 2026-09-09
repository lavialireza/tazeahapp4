package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.ListItemData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** رویدادهای «یک‌باره» که نباید در UiState بمانند (وگرنه با هر recomposition دوباره اجرا می‌شوند) */
sealed class RolesEvent {
    data class NavigateToCompare(val roleAId: Long, val roleBId: Long) : RolesEvent()
    data class ShowMessage(val text: String) : RolesEvent()
}

class RolesViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadedForTaziehId: Long? = null,
        val items: List<ListItemData> = emptyList(),
        val compareMode: Boolean = false,
        val selectMyRoleMode: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        val myRoleId: Long? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<RolesEvent>(Channel.BUFFERED)
    val events: Flow<RolesEvent> = _events.receiveAsFlow()

    fun loadIfNeeded(taziehId: Long) {
        if (_state.value.loadedForTaziehId == taziehId) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repo.getRolesByTazieh(taziehId).map { ListItemData(it.id, it.title) }
            val myRoleId = repo.getMyRole(taziehId)
            _state.update {
                it.copy(loading = false, loadedForTaziehId = taziehId, items = items, myRoleId = myRoleId)
            }
        }
    }

    fun toggleCompareMode() {
        _state.update { it.copy(compareMode = !it.compareMode, selectMyRoleMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelectMyRoleMode() {
        _state.update { it.copy(selectMyRoleMode = !it.selectMyRoleMode, compareMode = false, selectedIds = emptySet()) }
    }

    /** برای دکمه‌ی بازگشت وقتی یکی از دو حالت انتخاب فعال است: فقط همان حالت را لغو می‌کند */
    fun cancelSelectionMode() {
        _state.update { it.copy(compareMode = false, selectMyRoleMode = false, selectedIds = emptySet()) }
    }

    /** وقتی کاربر در کارت یک نقش می‌زند، اما یکی از حالت «مقایسه»/«انتخاب نقش من» فعال است */
    fun onToggleSelect(taziehId: Long, item: ListItemData) {
        val current = _state.value
        when {
            current.compareMode -> {
                val newSelected = when {
                    item.id in current.selectedIds -> current.selectedIds - item.id
                    current.selectedIds.size < 2 -> current.selectedIds + item.id
                    else -> current.selectedIds
                }
                if (newSelected.size == 2) {
                    val (a, b) = newSelected.toList()
                    _state.update { it.copy(compareMode = false, selectedIds = emptySet()) }
                    viewModelScope.launch { _events.send(RolesEvent.NavigateToCompare(a, b)) }
                } else {
                    _state.update { it.copy(selectedIds = newSelected) }
                }
            }

            current.selectMyRoleMode -> {
                viewModelScope.launch {
                    repo.setMyRole(taziehId, item.id)
                    _state.update { it.copy(myRoleId = item.id, selectMyRoleMode = false) }
                    _events.send(RolesEvent.ShowMessage("«${item.title}» به‌عنوان نقش شما ثبت شد"))
                }
            }
        }
    }
}
