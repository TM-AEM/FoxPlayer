package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for video playback history and resumption.
 */
@Dao
interface HistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY last_played_timestamp DESC")
    fun getAllHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE video_uri = :videoUri LIMIT 1")
    fun getHistoryByUri(videoUri: String): Flow<PlaybackHistoryEntity?>

    @Query("SELECT * FROM playback_history WHERE video_uri = :videoUri LIMIT 1")
    suspend fun getHistoryByUriDirect(videoUri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE video_uri = :videoUri")
    suspend fun deleteHistoryByUri(videoUri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}
