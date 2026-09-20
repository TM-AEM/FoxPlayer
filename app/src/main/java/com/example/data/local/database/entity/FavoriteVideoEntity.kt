package com.example.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity tracking user bookmarked/favorited videos.
 */
@Entity(tableName = "favorite_videos")
data class FavoriteVideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_uri")
    val videoUri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long = System.currentTimeMillis()
)
