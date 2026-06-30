package com.personal.tv.ui.screens.settings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.tv.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsUiState(
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val hwAccelEnabled: Boolean = true,
    val aspectRatio: String = "Fit",
    val bufferMode: String = "Auto",
    val deinterlaceMode: String = "Auto",
    val autoSubtitles: Boolean = false,
    val subtitleSize: String = "Medium",
    val autoRefreshEpg: Boolean = true,
    val epgWindow: String = "4 hours"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val repo: ChannelRepository
) : AndroidViewModel(application) {

    private val dataStore = application.dataStore

    private val PLAYLIST_URL    = stringPreferencesKey("playlist_url")
    private val EPG_URL         = stringPreferencesKey("epg_url")
    private val HW_ACCEL        = booleanPreferencesKey("hw_accel")
    private val ASPECT_RATIO    = stringPreferencesKey("aspect_ratio")
    private val BUFFER_MODE     = stringPreferencesKey("buffer_mode")
    private val DEINTERLACE     = stringPreferencesKey("deinterlace")
    private val AUTO_SUBTITLES  = booleanPreferencesKey("auto_subtitles")
    private val SUBTITLE_SIZE   = stringPreferencesKey("subtitle_size")
    private val AUTO_EPG        = booleanPreferencesKey("auto_epg")
    private val EPG_WINDOW      = stringPreferencesKey("epg_window")

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.value = SettingsUiState(
                    playlistUrl    = prefs[PLAYLIST_URL]   ?: "",
                    epgUrl         = prefs[EPG_URL]        ?: "",
                    hwAccelEnabled = prefs[HW_ACCEL]       ?: true,
                    aspectRatio    = prefs[ASPECT_RATIO]   ?: "Fit",
                    bufferMode     = prefs[BUFFER_MODE]    ?: "Auto",
                    deinterlaceMode= prefs[DEINTERLACE]    ?: "Auto",
                    autoSubtitles  = prefs[AUTO_SUBTITLES] ?: false,
                    subtitleSize   = prefs[SUBTITLE_SIZE]  ?: "Medium",
                    autoRefreshEpg = prefs[AUTO_EPG]       ?: true,
                    epgWindow      = prefs[EPG_WINDOW]     ?: "4 hours"
                )
            }
        }
    }

    fun setPlaylistUrl(url: String) = savePref { it[PLAYLIST_URL] = url }
    fun setEpgUrl(url: String)      = savePref { it[EPG_URL]      = url }
    fun setHwAccel(v: Boolean)      = savePref { it[HW_ACCEL]     = v  }
    fun setAspectRatio(v: String)   = savePref { it[ASPECT_RATIO] = v  }
    fun setBufferMode(v: String)    = savePref { it[BUFFER_MODE]  = v  }
    fun setDeinterlaceMode(v: String) = savePref { it[DEINTERLACE] = v }
    fun setAutoSubtitles(v: Boolean)  = savePref { it[AUTO_SUBTITLES] = v }
    fun setSubtitleSize(v: String)    = savePref { it[SUBTITLE_SIZE]  = v }
    fun setAutoRefreshEpg(v: Boolean) = savePref { it[AUTO_EPG]       = v }
    fun setEpgWindow(v: String)       = savePref { it[EPG_WINDOW]     = v }

    fun refreshPlaylist() {
        val url = _uiState.value.playlistUrl
        if (url.isNotEmpty()) {
            viewModelScope.launch { repo.loadPlaylistFromUrl(url) }
        }
    }

    private fun savePref(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            dataStore.edit { block(it) }
        }
    }
}
