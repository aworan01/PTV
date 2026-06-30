package com.personal.tv.ui.screens.epg

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.tv.data.db.entity.PlaylistEntity
import com.personal.tv.data.model.Channel
import com.personal.tv.data.model.Programme
import com.personal.tv.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpgUiState(
    val activePlaylist: PlaylistEntity? = null,
    val groups: List<String> = emptyList(),
    val selectedGroup: String = "All channels",
    val channels: List<Channel> = emptyList(),
    val programmes: Map<String, List<Programme>> = emptyMap(),
    val featuredChannel: Channel? = null,
    val featuredProgramme: Programme? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
    val showGroupOptions: Boolean = false,
    val groupOptionsTarget: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpgViewModel @Inject constructor(
    private val repo: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpgUiState(isLoading = true))
    val uiState: StateFlow<EpgUiState> = _uiState.asStateFlow()

    // Track selected group separately so we can filter reactively
    private val _selectedGroup = MutableStateFlow("All channels")

    init {
        // React to active playlist changes
        viewModelScope.launch {
            repo.getActivePlaylistFlow().collectLatest { playlist ->
                _uiState.update { it.copy(activePlaylist = playlist, isLoading = playlist == null) }

                if (playlist == null) return@collectLatest

                // Collect channels for this playlist, re-scoped when playlist changes
                repo.getChannelsByPlaylist(playlist.id).collectLatest { channels ->
                    val groups = buildGroups(channels)
                    val selectedGroup = _selectedGroup.value
                    val filtered = filterChannels(channels, selectedGroup)

                    _uiState.update { state ->
                        state.copy(
                            groups = groups,
                            channels = filtered,
                            featuredChannel = filtered.firstOrNull(),
                            isLoading = false,
                            error = null
                        )
                    }
                    loadProgrammes(filtered)
                }
            }
        }
    }

    // ── Load from URL ─────────────────────────────────────────────────

    fun loadPlaylistFromUrl(url: String, name: String = "My Playlist", epgUrl: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading playlist…", error = null) }
            repo.loadPlaylistFromUrl(url, name, epgUrl).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loadingMessage = "") } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, loadingMessage = "", error = e.message) } }
            )
        }
    }

    // ── Load from local file ──────────────────────────────────────────

    fun loadPlaylistFromUri(uri: Uri, name: String = "Local Playlist") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Reading file…", error = null) }
            repo.loadPlaylistFromUri(uri, name).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loadingMessage = "") } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, loadingMessage = "", error = e.message) } }
            )
        }
    }

    // ── Group selection ───────────────────────────────────────────────

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        _uiState.update { it.copy(selectedGroup = group) }

        viewModelScope.launch {
            val playlist = _uiState.value.activePlaylist ?: return@launch
            repo.getChannelsByPlaylist(playlist.id).first().let { all ->
                val filtered = filterChannels(all, group)
                _uiState.update { it.copy(channels = filtered) }
                loadProgrammes(filtered)
            }
        }
    }

    // ── EPG refresh ───────────────────────────────────────────────────

    fun refreshEpg() {
        val playlist = _uiState.value.activePlaylist ?: return
        viewModelScope.launch {
            if (playlist.epgUrl.isNotEmpty()) {
                _uiState.update { it.copy(loadingMessage = "Refreshing EPG…") }
                repo.loadEpgFromUrl(playlist.epgUrl)
                loadProgrammes(_uiState.value.channels)
                _uiState.update { it.copy(loadingMessage = "") }
            }
        }
    }

    // ── Group options ─────────────────────────────────────────────────

    fun showGroupOptions(groupName: String) {
        _uiState.update { it.copy(showGroupOptions = true, groupOptionsTarget = groupName) }
    }

    fun dismissGroupOptions() {
        _uiState.update { it.copy(showGroupOptions = false) }
    }

    fun hideGroup(groupName: String) {
        viewModelScope.launch {
            repo.setGroupHidden(groupName, true)
            dismissGroupOptions()
        }
    }

    fun renameGroup(groupName: String, newName: String) {
        viewModelScope.launch {
            repo.renameGroup(groupName, newName)
            dismissGroupOptions()
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    // ── Private helpers ───────────────────────────────────────────────

    private fun buildGroups(channels: List<Channel>): List<String> {
        val extras = channels.map { it.groupTitle }.distinct().filter { it.isNotEmpty() }
        return listOf("Favourites", "All channels") + extras
    }

    private fun filterChannels(channels: List<Channel>, group: String): List<Channel> =
        when (group) {
            "All channels" -> channels
            "Favourites"   -> channels.filter { it.isFavourite }
            else           -> channels.filter { it.groupTitle == group }
        }

    private fun loadProgrammes(channels: List<Channel>) {
        if (channels.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val from = now - 30 * 60 * 1000L
            val to   = now + 4 * 60 * 60 * 1000L
            val programmes = repo.getProgrammesForChannels(channels.map { it.id }, from, to)
            val byChannel = programmes.groupBy { it.channelId }
            val featuredProg = byChannel[channels.firstOrNull()?.id]?.firstOrNull { it.isLive }
            _uiState.update { it.copy(programmes = byChannel, featuredProgramme = featuredProg) }
        }
    }
}
