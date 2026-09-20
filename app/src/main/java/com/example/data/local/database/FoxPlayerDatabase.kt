package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.database.dao.FavoritesDao
import com.example.data.local.database.dao.HistoryDao
import com.example.data.local.database.dao.PlaylistDao
import com.example.data.local.database.entity.FavoriteVideoEntity
import com.example.data.local.database.entity.PlaybackHistoryEntity
import com.example.data.local.database.entity.PlaylistEntity
import com.example.data.local.database.entity.PlaylistItemEntity

/**
 * Core Room database for FoxPlayer tracking playback history, favorites, and playlists.
 */
@Database(
    entities = [
        PlaybackHistoryEntity::class,
        FavoriteVideoEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FoxPlayerDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val DATABASE_NAME = "foxplayer.db"

        @Volatile
        private var INSTANCE: FoxPlayerDatabase? = null

        fun getInstance(context: Context): FoxPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoxPlayerDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
