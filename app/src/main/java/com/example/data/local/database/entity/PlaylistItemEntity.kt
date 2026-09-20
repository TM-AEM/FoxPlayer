package com.example.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an individual video entry within a playlist, ordered by item_order.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["playlist_id", "video_uri"])
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "video_uri")
    val videoUri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "item_order")
    val itemOrder: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
