package com.example.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity tracking video playback history, positions, and completion percentages.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_uri")
    val videoUri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "last_position_ms")
    val lastPositionMs: Long,

    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "watch_percentage")
    val watchPercentage: Float = 0f
)
