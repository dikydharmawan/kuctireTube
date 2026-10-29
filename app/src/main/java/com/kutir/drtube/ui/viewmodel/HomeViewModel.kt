package com.kutir.drtube.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kutir.drtube.data.model.Video
import com.kutir.drtube.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val categories: Map<String, List<Video>>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchFeed()
    }

    fun fetchFeed() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = videoRepository.getFeedCategories()
            result.onSuccess { categories ->
                _uiState.value = HomeUiState.Success(categories)
            }.onFailure { exception ->
                _uiState.value = HomeUiState.Error(exception.localizedMessage ?: "Unknown error occurred")
            }
        }
    }
}
