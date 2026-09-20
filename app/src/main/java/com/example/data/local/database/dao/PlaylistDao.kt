package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.PlaylistEntity
import com.example.data.local.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom playlists and ordered playlist items.
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistByIdDirect(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY item_order ASC, added_at ASC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY item_order ASC, added_at ASC")
    suspend fun getPlaylistItemsDirect(playlistId: Long): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deletePlaylistItemById(itemId: Long)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND video_uri = :videoUri")
    suspend fun deletePlaylistItemByUri(playlistId: Long, videoUri: String)

    @Query("UPDATE playlist_items SET item_order = :newOrder WHERE id = :itemId")
    suspend fun updateItemOrder(itemId: Long, newOrder: Int)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)
}
