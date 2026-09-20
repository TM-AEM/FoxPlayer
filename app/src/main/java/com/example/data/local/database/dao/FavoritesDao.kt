package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.FavoriteVideoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user-bookmarked / favorited videos.
 */
@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorite_videos ORDER BY added_timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE video_uri = :videoUri)")
    fun isFavorite(videoUri: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE video_uri = :videoUri)")
    suspend fun isFavoriteDirect(videoUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteVideoEntity)

    @Query("DELETE FROM favorite_videos WHERE video_uri = :videoUri")
    suspend fun deleteFavoriteByUri(videoUri: String)

    @Query("DELETE FROM favorite_videos")
    suspend fun clearAllFavorites()
}
