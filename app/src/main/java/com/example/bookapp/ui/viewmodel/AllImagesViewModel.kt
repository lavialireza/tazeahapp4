package com.example.bookapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.ui.screens.GalleryImageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AllImagesViewModel(private val repo: TaziehRepository) : ViewModel() {

    data class UiState(val loading: Boolean = true, val images: List<GalleryImageItem> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val images = repo.getAllTaziehImagesWithTaziehTitle().map { (img, taziehTitle, _) ->
                GalleryImageItem(img.id, img.filePath, img.caption, taziehTitle)
            }
            _state.update { it.copy(loading = false, images = images) }
        }
    }
}
