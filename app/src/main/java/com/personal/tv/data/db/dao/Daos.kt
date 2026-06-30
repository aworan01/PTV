package com.personal.tv.data.db.dao

import androidx.room.*
import com.personal.tv.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group ORDER BY sortOrder ASC")
    fun getChannelsByPlaylistAndGroup(playlistId: String, group: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavourite = 1 ORDER BY sortOrder ASC")
    fun getFavouritesByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getGroups(playlistId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    // Sync version for use inside withContext(IO) blocks in repository
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("UPDATE channels SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: String, fav: Boolean)
}

@Dao
interface ProgrammeDao {
    @Query("SELECT * FROM programmes WHERE channelId IN (:channelIds) AND endTime > :from AND startTime < :to ORDER BY startTime ASC")
    suspend fun getProgrammesForChannels(channelIds: List<String>, from: Long, to: Long): List<ProgrammeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM programmes WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)")
    suspend fun deleteByPlaylist(playlistId: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun getActivePlaylistFlow(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlaylist(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    // Sync versions for use inside withContext(IO)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(playlist: PlaylistEntity)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAll()

    // Sync version
    @Query("UPDATE playlists SET isActive = 0")
    fun deactivateAllSync()

    @Query("UPDATE playlists SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)
}

@Dao
interface ChannelPrefDao {
    @Query("SELECT * FROM channel_prefs WHERE channelId = :channelId")
    suspend fun getPrefs(channelId: String): ChannelPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: ChannelPrefEntity)
}

@Dao
interface GroupPrefDao {
    @Query("SELECT * FROM group_prefs")
    fun getAllGroupPrefs(): Flow<List<GroupPrefEntity>>

    @Query("SELECT * FROM group_prefs WHERE groupName = :name")
    suspend fun getPrefs(name: String): GroupPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: GroupPrefEntity)
}
