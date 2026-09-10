package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AboutViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val fieldsCount: Int = 0,
        val taziehsCount: Int = 0,
        val rolesCount: Int = 0,
        val sectionsCount: Int = 0,
        val readCount: Int = 0,
        val streakDays: Int = 0,
        val activeDaysLast14: List<Boolean> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val counts = repo.getContentCounts()
            _state.update {
                it.copy(
                    loaded = true,
                    fieldsCount = counts.fields,
                    taziehsCount = counts.taziehs,
                    rolesCount = counts.roles,
                    sectionsCount = counts.sections,
                    readCount = repo.getReadSectionsCount(),
                    streakDays = repo.getStreakDays(),
                    activeDaysLast14 = repo.getActiveDaysLast(14)
                )
            }
        }
    }
}
