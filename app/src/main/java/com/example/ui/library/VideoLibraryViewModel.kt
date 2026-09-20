package com.example.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.VideoItem
import com.example.core.util.PermissionUtils
import com.example.data.repository.MediaRepository
import com.example.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sort options for the video library.
 */
enum class VideoSortOption(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A-Z)"),
    DURATION_DESC("Longest First"),
    SIZE_DESC("Largest First")
}

/**
 * UI State for the Video Library screen.
 */
data class VideoLibraryUiState(
    val isLoading: Boolean = true,
    val isPermissionGranted: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val rawVideos: List<VideoItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: VideoSortOption = VideoSortOption.DATE_DESC,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && isPermissionGranted && videos.isEmpty()
}

/**
 * ViewModel managing video discovery, permissions, real-time updates, searching, and sorting.
 */
class VideoLibraryViewModel @JvmOverloads constructor(
    application: Application,
    private val mediaRepository: MediaRepository = MediaRepositoryImpl(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VideoLibraryUiState())
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val hasPermission = PermissionUtils.hasVideoPermission(getApplication())
        _uiState.update { it.copy(isPermissionGranted = hasPermission) }

        if (hasPermission) {
            observeVideos()
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = granted) }
        if (granted) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            observeVideos()
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observeVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            mediaRepository.getVideosFlow()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load videos"
                        )
                    }
                }
                .collect { videoList ->
                    _uiState.update { state ->
                        val processed = filterAndSort(videoList, state.searchQuery, state.sortOption)
                        state.copy(
                            isLoading = false,
                            rawVideos = videoList,
                            videos = processed,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val processed = filterAndSort(state.rawVideos, query, state.sortOption)
            state.copy(searchQuery = query, videos = processed)
        }
    }

    fun onSortOptionChanged(option: VideoSortOption) {
        _uiState.update { state ->
            val processed = filterAndSort(state.rawVideos, state.searchQuery, option)
            state.copy(sortOption = option, videos = processed)
        }
    }

    fun refresh() {
        if (_uiState.value.isPermissionGranted) {
            observeVideos()
        } else {
            checkPermissionAndLoad()
        }
    }

    private fun filterAndSort(
        list: List<VideoItem>,
        query: String,
        sort: VideoSortOption
    ): List<VideoItem> {
        val filtered = if (query.isBlank()) {
            list
        } else {
            val lower = query.trim().lowercase()
            list.filter { video ->
                video.title.lowercase().contains(lower) ||
                        video.displayName.lowercase().contains(lower)
            }
        }

        return when (sort) {
            VideoSortOption.DATE_DESC -> filtered.sortedByDescending { it.dateModified }
            VideoSortOption.DATE_ASC -> filtered.sortedBy { it.dateModified }
            VideoSortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            VideoSortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            VideoSortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    class Factory(
        private val application: Application,
        private val mediaRepository: MediaRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return if (mediaRepository != null) {
                VideoLibraryViewModel(application, mediaRepository) as T
            } else {
                VideoLibraryViewModel(application) as T
            }
        }
    }
}
