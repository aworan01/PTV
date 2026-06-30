package com.personal.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personal.tv.data.db.dao.*
import com.personal.tv.data.db.entity.*

@Database(
    entities = [
        ChannelEntity::class,
        ProgrammeEntity::class,
        PlaylistEntity::class,
        ChannelPrefEntity::class,
        GroupPrefEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelPrefDao(): ChannelPrefDao
    abstract fun groupPrefDao(): GroupPrefDao
}
