package com.personal.tv.ui.screens.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.tv.data.db.entity.PlaylistEntity
import com.personal.tv.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun addFromUrl(url: String, name: String = "My Playlist", epgUrl: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading playlist…", error = null) }
            repo.loadPlaylistFromUrl(url, name, epgUrl).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loadingMessage = "") } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, loadingMessage = "", error = e.message) } }
            )
        }
    }

    fun addFromFile(uri: Uri, name: String = "Local Playlist") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Reading file…", error = null) }
            repo.loadPlaylistFromUri(uri, name).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loadingMessage = "") } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, loadingMessage = "", error = e.message) } }
            )
        }
    }

    fun setActive(playlistId: String) {
        viewModelScope.launch {
            repo.setActivePlaylist(playlistId)
        }
    }

    fun refresh(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Refreshing…") }
            repo.refreshPlaylist(playlistId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loadingMessage = "") } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, loadingMessage = "", error = e.message) } }
            )
        }
    }

    fun delete(playlistId: String) {
        viewModelScope.launch {
            repo.deletePlaylist(playlistId)
        }
    }

    fun updateEpgUrl(playlistId: String, epgUrl: String) {
        viewModelScope.launch {
            // Reload EPG with new URL
            if (epgUrl.isNotEmpty()) {
                _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading EPG…") }
                repo.loadEpgFromUrl(epgUrl)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "") }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
