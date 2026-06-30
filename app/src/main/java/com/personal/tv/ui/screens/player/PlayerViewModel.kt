package com.personal.tv.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.*
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.personal.tv.data.model.Channel
import com.personal.tv.data.model.DrmType
import com.personal.tv.data.model.Programme
import com.personal.tv.data.model.StreamInfo
import com.personal.tv.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val channelList: List<Channel> = emptyList(),
    val channelIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val showOverlay: Boolean = true,
    val showTrackMenu: Boolean = false,
    val showChannelBrowser: Boolean = false,
    val trackMenuPage: TrackMenuPage = TrackMenuPage.MAIN,
    val streamInfo: StreamInfo = StreamInfo(),
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val selectedSubtitleTrack: Int = -1,
    val currentProgramme: Programme? = null,
    val nextProgramme: Programme? = null,
    val error: String? = null,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val bufferMode: BufferMode = BufferMode.AUTO,
    val deinterlaceMode: DeinterlaceMode = DeinterlaceMode.AUTO,
    val hwAccelEnabled: Boolean = true,
    // Full playlist data for the channel browser (all groups, not just current group's list)
    val allGroups: List<String> = emptyList(),
    val channelsByGroup: Map<String, List<Channel>> = emptyMap()
)

data class TrackInfo(val index: Int, val label: String, val language: String = "")

enum class TrackMenuPage { MAIN, AUDIO, SUBTITLES, SETTINGS, ASPECT, BUFFER, DEINTERLACE, HW }
enum class AspectRatioMode { FIT, FILL, STRETCH, FOUR_THREE, SIXTEEN_NINE }
enum class BufferMode { AUTO, LOW, MEDIUM, HIGH }
enum class DeinterlaceMode { AUTO, OFF, ON }

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val httpClient: OkHttpClient,
    private val repo: ChannelRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var overlayJob: kotlinx.coroutines.Job? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(application)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(httpClient)))
            .build()
            .also { setupListeners(it) }
    }

    // ── Channel loading ───────────────────────────────────────────────

    fun initWithChannel(channel: Channel, channelList: List<Channel>) {
        val index = channelList.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        _uiState.update { it.copy(channel = channel, channelList = channelList, channelIndex = index) }
        loadChannel(channel)
        loadProgrammes(channel)
        scheduleHideOverlay()
        loadFullPlaylistForBrowser()
    }

    private fun loadFullPlaylistForBrowser() {
        viewModelScope.launch {
            val playlist = repo.getActivePlaylistFlow().first() ?: return@launch
            val allChannels = repo.getChannelsByPlaylist(playlist.id).first()
            val grouped = allChannels.groupBy { it.groupTitle.ifEmpty { "Uncategorised" } }
            val groups = grouped.keys.sortedBy { it.lowercase() }
            _uiState.update { it.copy(allGroups = groups, channelsByGroup = grouped) }
        }
    }

    fun playChannelFromBrowser(channel: Channel) {
        // Rebuild the active channel list to be the channels in the channel's own group,
        // so subsequent browsing/back behaviour stays scoped sensibly.
        val groupChannels = _uiState.value.channelsByGroup[channel.groupTitle.ifEmpty { "Uncategorised" }]
            ?: listOf(channel)
        val index = groupChannels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                channel = channel, channelList = groupChannels, channelIndex = index,
                showChannelBrowser = false, error = null, isBuffering = true,
                currentProgramme = null, nextProgramme = null, streamInfo = StreamInfo()
            )
        }
        loadChannel(channel)
        loadProgrammes(channel)
        showOverlay()
    }

    fun showChannelBrowser() {
        overlayJob?.cancel()
        _uiState.update { it.copy(showChannelBrowser = true, showOverlay = false, showTrackMenu = false) }
    }

    fun hideChannelBrowser() {
        _uiState.update { it.copy(showChannelBrowser = false) }
        scheduleHideOverlay()
    }

    fun loadChannel(channel: Channel) {
        viewModelScope.launch {
            try {
                val headers = mutableMapOf<String, String>()
                if (channel.userAgent.isNotEmpty()) headers["User-Agent"] = channel.userAgent
                if (channel.referrer.isNotEmpty()) headers["Referer"] = channel.referrer

                val dataSourceFactory = OkHttpDataSource.Factory(httpClient).apply {
                    if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(channel.url))
                    .build()

                val drmSessionManager = buildDrmSessionManagerSafe(channel)

                val mediaSource = when (channel.manifestType.lowercase()) {
                    "dash" -> DashMediaSource.Factory(dataSourceFactory)
                        .apply { drmSessionManager?.let { setDrmSessionManagerProvider { _ -> it } } }
                        .createMediaSource(mediaItem)
                    "hls"  -> HlsMediaSource.Factory(dataSourceFactory)
                        .apply { drmSessionManager?.let { setDrmSessionManagerProvider { _ -> it } } }
                        .createMediaSource(mediaItem)
                    "rtsp" -> RtspMediaSource.Factory().createMediaSource(mediaItem)
                    else   -> when {
                        channel.url.contains(".mpd", ignoreCase = true)  ->
                            DashMediaSource.Factory(dataSourceFactory)
                                .apply { drmSessionManager?.let { setDrmSessionManagerProvider { _ -> it } } }
                                .createMediaSource(mediaItem)
                        channel.url.contains(".m3u8", ignoreCase = true) ->
                            HlsMediaSource.Factory(dataSourceFactory)
                                .apply { drmSessionManager?.let { setDrmSessionManagerProvider { _ -> it } } }
                                .createMediaSource(mediaItem)
                        channel.url.startsWith("rtsp://", ignoreCase = true) ->
                            RtspMediaSource.Factory().createMediaSource(mediaItem)
                        else ->
                            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
                    }
                }

                player.stop()
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load: ${e.message}", isBuffering = false) }
            }
        }
    }

    /**
     * Builds a DRM session manager safely. Any failure here (unsupported DRM scheme on this
     * device, malformed key data, etc.) is caught and surfaced as a playback error instead of
     * crashing the player — this was the cause of freezes/crashes with ClearKey content.
     */
    private fun buildDrmSessionManagerSafe(channel: Channel): DrmSessionManager? {
        if (channel.drmType == DrmType.NONE || channel.drmLicenseUrl.isEmpty()) return null

        return try {
            val uuid = when (channel.drmType) {
                DrmType.WIDEVINE  -> C.WIDEVINE_UUID
                DrmType.PLAYREADY -> C.PLAYREADY_UUID
                DrmType.CLEARKEY  -> C.CLEARKEY_UUID
                else -> return null
            }

            val licenseValue = channel.drmLicenseUrl
            val isUrl = licenseValue.startsWith("http://", ignoreCase = true) ||
                        licenseValue.startsWith("https://", ignoreCase = true)

            val callback: MediaDrmCallback = if (channel.drmType == DrmType.CLEARKEY && !isUrl) {
                // Inline ClearKey JSON (built by M3UParser from hex kid:key pairs, or raw base64
                // JSON) — must use LocalMediaDrmCallback, NOT a license URI, or ExoPlayer will
                // try to open the JSON string as a network request and crash.
                LocalMediaDrmCallback(licenseValue.toByteArray(Charsets.UTF_8))
            } else {
                // Widevine / PlayReady / ClearKey-via-URL — real license server
                HttpMediaDrmCallback(licenseValue, OkHttpDataSource.Factory(httpClient))
            }

            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(callback)

        } catch (e: Exception) {
            // Device may not support this DRM scheme at all (common on some Android TV OEM
            // builds for ClearKey). Surface as an error rather than letting it crash later.
            _uiState.update { it.copy(error = "DRM not supported on this device: ${e.message}") }
            null
        }
    }

    // ── EPG ───────────────────────────────────────────────────────────

    private fun loadProgrammes(channel: Channel) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val programmes = repo.getProgrammesForChannels(
                listOf(channel.id), now - 60_000, now + 4 * 60 * 60 * 1000L
            )
            val current = programmes.firstOrNull { it.isLive }
            val next = programmes.firstOrNull { it.startTime > now }
            _uiState.update { it.copy(currentProgramme = current, nextProgramme = next) }
        }
    }

    // ── Player listeners ──────────────────────────────────────────────

    private fun setupListeners(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.update { it.copy(isBuffering = state == Player.STATE_BUFFERING, isPlaying = player.isPlaying) }
                if (state == Player.STATE_READY) {
                    try { extractTrackInfo(player) } catch (e: Exception) { /* non-fatal — info display only */ }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _uiState.update { ui ->
                    ui.copy(streamInfo = ui.streamInfo.copy(
                        width = videoSize.width, height = videoSize.height,
                        resolution = "${videoSize.width}x${videoSize.height}"
                    ))
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _uiState.update { it.copy(error = error.message ?: "Playback error", isBuffering = false) }
            }
        })
    }

    private fun extractTrackInfo(player: ExoPlayer) {
        val tracks = player.currentTracks
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        tracks.groups.forEachIndexed { groupIdx, group ->
            val format = if (group.length > 0) group.getTrackFormat(0) else return@forEachIndexed
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    val label = buildString {
                        format.language?.uppercase()?.let { append(it) } ?: append("Audio ${audioTracks.size + 1}")
                        val ch = when (format.channelCount) {
                            1 -> " Mono"; 2 -> " Stereo"; 6 -> " 5.1"; 8 -> " 7.1"; else -> ""
                        }
                        append(ch)
                    }
                    audioTracks.add(TrackInfo(groupIdx, label, format.language ?: ""))
                }
                C.TRACK_TYPE_TEXT -> {
                    val label = format.language?.uppercase() ?: "Sub ${subtitleTracks.size + 1}"
                    subtitleTracks.add(TrackInfo(groupIdx, label, format.language ?: ""))
                }
                C.TRACK_TYPE_VIDEO -> {
                    val fps = if (format.frameRate > 0f) format.frameRate else 0f
                    val hdr = when (format.colorInfo?.colorTransfer) {
                        C.COLOR_TRANSFER_ST2084 -> "HDR10"
                        C.COLOR_TRANSFER_HLG    -> "HLG"
                        else -> "SDR"
                    }
                    // Detect HW vs SW decoder
                    val vDec = if (group.isTrackSupported(0)) "HW" else "SW"
                    val aCodec = format.codecs ?: ""
                    val aCh = when (format.channelCount) {
                        1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> ""
                    }
                    _uiState.update { ui ->
                        ui.copy(streamInfo = ui.streamInfo.copy(
                            fps = fps, videoCodec = aCodec, hdrType = hdr, videoDecoder = vDec
                        ))
                    }
                }
            }
        }

        // Audio decoder info
        val audioFormat = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        val aDec = if (audioFormat?.isTrackSupported(0) == true) "HW" else "SW"
        val aCh = audioFormat?.getTrackFormat(0)?.let {
            when (it.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "" }
        } ?: ""

        _uiState.update { ui ->
            ui.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                streamInfo = ui.streamInfo.copy(audioDecoder = aDec, audioChannels = aCh)
            )
        }
    }

    // ── Controls ──────────────────────────────────────────────────────

    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play(); showOverlay() }

    fun showOverlay() {
        _uiState.update { it.copy(showOverlay = true) }
        scheduleHideOverlay()
    }

    fun hideOverlay() {
        overlayJob?.cancel()
        _uiState.update { it.copy(showOverlay = false, showTrackMenu = false, showChannelBrowser = false) }
    }

    private fun scheduleHideOverlay() {
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(showOverlay = false, showTrackMenu = false) }
        }
    }

    fun toggleTrackMenu() {
        val showing = !_uiState.value.showTrackMenu
        _uiState.update { it.copy(showTrackMenu = showing, trackMenuPage = TrackMenuPage.MAIN, showOverlay = true) }
        if (showing) overlayJob?.cancel() else scheduleHideOverlay()
    }

    fun navigateTrackMenu(page: TrackMenuPage) {
        _uiState.update { it.copy(trackMenuPage = page) }
        overlayJob?.cancel() // keep visible while in menu
    }

    fun selectAudioTrack(index: Int) {
        val group = player.currentTracks.groups.getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0)).build()
        _uiState.update { it.copy(selectedAudioTrack = index, trackMenuPage = TrackMenuPage.MAIN) }
        scheduleHideOverlay()
    }

    fun selectSubtitleTrack(index: Int) {
        if (index == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT)).build()
        } else {
            val group = player.currentTracks.groups.getOrNull(index) ?: return
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0)).build()
        }
        _uiState.update { it.copy(selectedSubtitleTrack = index, trackMenuPage = TrackMenuPage.MAIN) }
        scheduleHideOverlay()
    }

    fun setAspectRatio(mode: AspectRatioMode) { _uiState.update { it.copy(aspectRatio = mode) }; scheduleHideOverlay() }
    fun setBufferMode(mode: BufferMode)        { _uiState.update { it.copy(bufferMode = mode) }; scheduleHideOverlay() }
    fun setDeinterlaceMode(mode: DeinterlaceMode) { _uiState.update { it.copy(deinterlaceMode = mode) }; scheduleHideOverlay() }
    fun setHwAccel(enabled: Boolean)           { _uiState.update { it.copy(hwAccelEnabled = enabled) }; scheduleHideOverlay() }

    fun refresh() {
        player.stop(); player.prepare(); player.playWhenReady = true
        _uiState.update { it.copy(error = null, isBuffering = true) }
    }

    override fun onCleared() { super.onCleared(); player.release() }
}
