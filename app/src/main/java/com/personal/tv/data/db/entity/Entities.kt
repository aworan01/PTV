package com.personal.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val groupTitle: String,
    val logoUrl: String,
    val tvgId: String,
    val tvgName: String,
    val tvgChno: String = "",
    val drmType: String,
    val drmLicenseUrl: String,
    val drmKeyId: String,
    val manifestType: String = "",
    val userAgent: String,
    val referrer: String,
    val isHidden: Boolean,
    val isFavourite: Boolean,
    val sortOrder: Int,
    val playlistId: String
)

@Entity(tableName = "programmes")
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val category: String,
    val icon: String,
    val rating: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String?,           // remote URL (nullable)
    val localUri: String?,      // local file URI (nullable)
    val epgUrl: String,
    val lastUpdated: Long,
    val isActive: Boolean
)

@Entity(tableName = "channel_prefs")
data class ChannelPrefEntity(
    @PrimaryKey val channelId: String,
    val customName: String = "",
    val isHidden: Boolean = false,
    val isFavourite: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "group_prefs")
data class GroupPrefEntity(
    @PrimaryKey val groupName: String,
    val customName: String = "",
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val sortOrder: Int = 0
)
