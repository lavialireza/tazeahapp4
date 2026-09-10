package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.ListItemData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FieldsViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loaded: Boolean = false,
        val items: List<ListItemData> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * فقط یک‌بار لازم است صدا زده شود؛ اگر ViewModel از قبل داده را بارگذاری کرده
     * (مثلاً کاربر به این صفحه برگشته)، دوباره کوئری نمی‌زند - برخلاف نسخه‌ی قبلی
     * که با هر بازگشت به صفحه، دیتابیس دوباره خوانده می‌شد.
     */
    fun loadIfNeeded() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repo.getFields().map { ListItemData(it.id, it.title) }
            _state.update { it.copy(loading = false, loaded = true, items = items) }
        }
    }
}
