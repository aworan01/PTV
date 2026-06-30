package com.personal.tv.data.repository

import android.content.Context
import android.net.Uri
import com.personal.tv.data.db.dao.*
import com.personal.tv.data.db.entity.*
import com.personal.tv.data.model.*
import com.personal.tv.data.parser.M3UParser
import com.personal.tv.data.parser.XmltvParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val playlistDao: PlaylistDao,
    private val groupPrefDao: GroupPrefDao,
    private val channelPrefDao: ChannelPrefDao,
    private val httpClient: OkHttpClient
) {

    // ── Playlist-scoped channel queries ───────────────────────────────

    fun getChannelsByPlaylist(playlistId: String): Flow<List<Channel>> =
        channelDao.getChannelsByPlaylist(playlistId).map { it.map { e -> e.toChannel() } }

    fun getChannelsByPlaylistAndGroup(playlistId: String, group: String): Flow<List<Channel>> =
        channelDao.getChannelsByPlaylistAndGroup(playlistId, group).map { it.map { e -> e.toChannel() } }

    fun getGroupsForPlaylist(playlistId: String): Flow<List<String>> =
        channelDao.getGroups(playlistId)

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
        playlistDao.getAllPlaylists()

    fun getActivePlaylistFlow(): Flow<PlaylistEntity?> =
        playlistDao.getActivePlaylistFlow()

    fun getAllGroupPrefs(): Flow<List<GroupPrefEntity>> =
        groupPrefDao.getAllGroupPrefs()

    // ── Load from URL ─────────────────────────────────────────────────

    suspend fun loadPlaylistFromUrl(
        url: String,
        name: String = "My Playlist",
        epgUrl: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = fetchUrl(url)
            if (!content.contains("#EXTM3U", ignoreCase = true) && !content.contains("#EXTINF", ignoreCase = true)) {
                return@withContext Result.failure(Exception("File doesn't look like a valid M3U playlist"))
            }
            val (channels, detectedEpgUrl) = M3UParser.parse(content)
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No channels found in playlist — check the file format"))
            }
            val finalEpgUrl = epgUrl.ifEmpty { detectedEpgUrl }
            val playlistId = savePlaylist(url = url, localUri = null, name = name, epgUrl = finalEpgUrl, channels = channels)
            if (finalEpgUrl.isNotEmpty()) loadEpgFromUrl(finalEpgUrl, channels)
            Result.success(playlistId)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to load playlist: ${e.message}", e))
        }
    }

    // ── Load from local file URI ──────────────────────────────────────

    suspend fun loadPlaylistFromUri(
        uri: Uri,
        name: String = "Local Playlist",
        epgUrl: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = readUri(uri)
            if (!content.contains("#EXTM3U", ignoreCase = true) && !content.contains("#EXTINF", ignoreCase = true)) {
                return@withContext Result.failure(Exception("File doesn't look like a valid M3U playlist"))
            }
            val (channels, detectedEpgUrl) = M3UParser.parse(content)
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No channels found in file — check the format"))
            }
            val finalEpgUrl = epgUrl.ifEmpty { detectedEpgUrl }
            val playlistId = savePlaylist(url = null, localUri = uri.toString(), name = name, epgUrl = finalEpgUrl, channels = channels)
            if (finalEpgUrl.isNotEmpty()) loadEpgFromUrl(finalEpgUrl, channels)
            Result.success(playlistId)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to load local playlist: ${e.message}", e))
        }
    }

    // ── Refresh existing playlist ─────────────────────────────────────

    suspend fun refreshPlaylist(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = playlistDao.getById(playlistId)
                ?: return@withContext Result.failure(Exception("Playlist not found"))

            val content = when {
                entity.url?.isNotEmpty() == true -> fetchUrl(entity.url)
                entity.localUri?.isNotEmpty() == true -> readUri(Uri.parse(entity.localUri))
                else -> return@withContext Result.failure(Exception("No source URL or file"))
            }

            val (channels, _) = M3UParser.parse(content)
            channelDao.deleteByPlaylist(playlistId)
            channelDao.insertAll(channels.map { it.toEntity(playlistId) })

            playlistDao.update(entity.copy(lastUpdated = System.currentTimeMillis()))

            if (entity.epgUrl.isNotEmpty()) {
                loadEpgFromUrl(entity.epgUrl, channels)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Refresh failed: ${e.message}", e))
        }
    }

    // ── Switch active playlist ────────────────────────────────────────

    suspend fun setActivePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        playlistDao.deactivateAll()
        playlistDao.activate(playlistId)
    }

    // ── Delete playlist ───────────────────────────────────────────────

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        programmeDao.deleteByPlaylist(playlistId)
        channelDao.deleteByPlaylist(playlistId)
        playlistDao.deleteById(playlistId)
    }

    // ── EPG ──────────────────────────────────────────────────────────

    suspend fun loadEpgFromUrl(url: String, channels: List<Channel> = emptyList()) {
        withContext(Dispatchers.IO) {
            try {
                val content = fetchUrl(url)
                val programmes = XmltvParser.parse(content)
                val tvgIdMap = channels.associate { it.tvgId to it.id }
                programmeDao.deleteOlderThan(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                programmeDao.insertAll(programmes.map { prog ->
                    val channelId = tvgIdMap[prog.channelId] ?: prog.channelId
                    prog.toEntity(channelId)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getProgrammesForChannels(channelIds: List<String>, from: Long, to: Long): List<Programme> =
        withContext(Dispatchers.IO) {
            if (channelIds.isEmpty()) return@withContext emptyList()
            programmeDao.getProgrammesForChannels(channelIds, from, to).map { it.toProgramme() }
        }

    // ── Group / Channel prefs ─────────────────────────────────────────

    suspend fun setGroupHidden(groupName: String, hidden: Boolean) {
        withContext(Dispatchers.IO) {
            val existing = groupPrefDao.getPrefs(groupName) ?: GroupPrefEntity(groupName)
            groupPrefDao.upsert(existing.copy(isHidden = hidden))
        }
    }

    suspend fun renameGroup(groupName: String, newName: String) {
        withContext(Dispatchers.IO) {
            val existing = groupPrefDao.getPrefs(groupName) ?: GroupPrefEntity(groupName)
            groupPrefDao.upsert(existing.copy(customName = newName))
        }
    }

    suspend fun setChannelFavourite(channelId: String, fav: Boolean) {
        withContext(Dispatchers.IO) { channelDao.setFavourite(channelId, fav) }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private fun savePlaylist(
        url: String?,
        localUri: String?,
        name: String,
        epgUrl: String,
        channels: List<Channel>
    ): String {
        val playlistId = UUID.randomUUID().toString()
        val entity = PlaylistEntity(
            id = playlistId,
            name = name,
            url = url,
            localUri = localUri,
            epgUrl = epgUrl,
            lastUpdated = System.currentTimeMillis(),
            isActive = true
        )
        playlistDao.deactivateAllSync()
        playlistDao.insertSync(entity)
        channelDao.insertAllSync(channels.map { it.toEntity(playlistId) })
        return playlistId
    }

    private fun fetchUrl(url: String): String {
        val client = httpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (PersonalTV)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Empty response body")

            // Stream-read with a sane upper bound (50MB) to avoid OOM on large playlists
            val maxBytes = 50L * 1024 * 1024
            val contentLength = body.contentLength()
            if (contentLength > maxBytes) {
                throw Exception("Playlist too large (${contentLength / 1024 / 1024}MB) — exceeds 50MB limit")
            }

            val text = body.source().use { source ->
                val buffer = okio.Buffer()
                var totalRead = 0L
                while (true) {
                    val read = source.read(buffer, 8192)
                    if (read == -1L) break
                    totalRead += read
                    if (totalRead > maxBytes) {
                        throw Exception("Playlist exceeded 50MB while downloading")
                    }
                }
                buffer.readString(Charsets.UTF_8)
            }

            if (text.isBlank()) throw Exception("Playlist response was empty")
            return text
        }
    }

    private fun readUri(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw Exception("Could not read file")
    }

    // ── Mappers ───────────────────────────────────────────────────────

    private fun ChannelEntity.toChannel() = Channel(
        id = id, name = name, url = url, groupTitle = groupTitle,
        logoUrl = logoUrl, tvgId = tvgId, tvgName = tvgName,
        tvgChno = tvgChno,
        drmType = DrmType.valueOf(drmType), drmLicenseUrl = drmLicenseUrl,
        drmKeyId = drmKeyId, manifestType = manifestType,
        userAgent = userAgent, referrer = referrer,
        isHidden = isHidden, isFavourite = isFavourite, sortOrder = sortOrder
    )

    private fun Channel.toEntity(playlistId: String) = ChannelEntity(
        id = id, name = name, url = url, groupTitle = groupTitle,
        logoUrl = logoUrl, tvgId = tvgId, tvgName = tvgName,
        tvgChno = tvgChno,
        drmType = drmType.name, drmLicenseUrl = drmLicenseUrl,
        drmKeyId = drmKeyId, manifestType = manifestType,
        userAgent = userAgent, referrer = referrer,
        isHidden = isHidden, isFavourite = isFavourite, sortOrder = sortOrder,
        playlistId = playlistId
    )

    private fun ProgrammeEntity.toProgramme() = Programme(
        channelId = channelId, title = title, description = description,
        startTime = startTime, endTime = endTime, category = category,
        icon = icon, rating = rating
    )

    private fun Programme.toEntity(overrideChannelId: String = channelId) = ProgrammeEntity(
        channelId = overrideChannelId, title = title, description = description,
        startTime = startTime, endTime = endTime, category = category,
        icon = icon, rating = rating
    )
}
