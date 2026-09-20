package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.FoxPlayerDatabase
import com.example.data.local.database.dao.FavoritesDao
import com.example.data.local.database.dao.HistoryDao
import com.example.data.local.database.dao.PlaylistDao
import com.example.data.local.database.entity.FavoriteVideoEntity
import com.example.data.local.database.entity.PlaybackHistoryEntity
import com.example.data.local.database.entity.PlaylistEntity
import com.example.data.local.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FoxPlayerDatabaseTest {

    private lateinit var database: FoxPlayerDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var favoritesDao: FavoritesDao
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FoxPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
        favoritesDao = database.favoritesDao()
        playlistDao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testHistoryInsertUpdateAndQuery() = runBlocking {
        val uri = "content://media/external/video/media/101"
        val initialEntry = PlaybackHistoryEntity(
            videoUri = uri,
            title = "Test Nature Video",
            durationMs = 120_000L,
            lastPositionMs = 30_000L,
            lastPlayedTimestamp = 1_000L,
            watchPercentage = 0.25f
        )

        historyDao.upsertHistory(initialEntry)

        val retrieved = historyDao.getHistoryByUriDirect(uri)
        assertNotNull(retrieved)
        assertEquals("Test Nature Video", retrieved?.title)
        assertEquals(30_000L, retrieved?.lastPositionMs)
        assertEquals(0.25f, retrieved?.watchPercentage)

        // Update position and timestamp
        val updatedEntry = initialEntry.copy(
            lastPositionMs = 90_000L,
            lastPlayedTimestamp = 2_000L,
            watchPercentage = 0.75f
        )
        historyDao.upsertHistory(updatedEntry)

        val updatedRetrieved = historyDao.getHistoryByUriDirect(uri)
        assertEquals(90_000L, updatedRetrieved?.lastPositionMs)
        assertEquals(0.75f, updatedRetrieved?.watchPercentage)
        assertEquals(2_000L, updatedRetrieved?.lastPlayedTimestamp)

        // Test delete
        historyDao.deleteHistoryByUri(uri)
        val afterDelete = historyDao.getHistoryByUriDirect(uri)
        assertNull(afterDelete)
    }

    @Test
    fun testFavoriteAddAndRemove() = runBlocking {
        val uri = "content://media/external/video/media/202"
        val favorite = FavoriteVideoEntity(
            videoUri = uri,
            title = "Favorite Clip",
            durationMs = 45_000L,
            addedTimestamp = System.currentTimeMillis()
        )

        assertFalse(favoritesDao.isFavoriteDirect(uri))

        favoritesDao.insertFavorite(favorite)
        assertTrue(favoritesDao.isFavoriteDirect(uri))

        val allFavorites = favoritesDao.getAllFavorites().first()
        assertEquals(1, allFavorites.size)
        assertEquals("Favorite Clip", allFavorites[0].title)

        favoritesDao.deleteFavoriteByUri(uri)
        assertFalse(favoritesDao.isFavoriteDirect(uri))
        assertTrue(favoritesDao.getAllFavorites().first().isEmpty())
    }

    @Test
    fun testPlaylistCreateAddItemsReorderAndCascadeDelete() = runBlocking {
        val playlist = PlaylistEntity(
            name = "Workout Mix",
            createdAt = 100L,
            updatedAt = 100L
        )

        val playlistId = playlistDao.insertPlaylist(playlist)
        assertTrue(playlistId > 0)

        // Add 3 items in order
        val item1 = PlaylistItemEntity(
            playlistId = playlistId,
            videoUri = "content://media/1",
            title = "Warmup",
            durationMs = 60_000L,
            itemOrder = 0
        )
        val item2 = PlaylistItemEntity(
            playlistId = playlistId,
            videoUri = "content://media/2",
            title = "High Intensity",
            durationMs = 180_000L,
            itemOrder = 1
        )
        val item3 = PlaylistItemEntity(
            playlistId = playlistId,
            videoUri = "content://media/3",
            title = "Cooldown",
            durationMs = 120_000L,
            itemOrder = 2
        )

        val item1Id = playlistDao.insertPlaylistItem(item1)
        val item2Id = playlistDao.insertPlaylistItem(item2)
        val item3Id = playlistDao.insertPlaylistItem(item3)

        val items = playlistDao.getPlaylistItemsDirect(playlistId)
        assertEquals(3, items.size)
        assertEquals("Warmup", items[0].title)
        assertEquals("High Intensity", items[1].title)
        assertEquals("Cooldown", items[2].title)

        // Reorder item 1 to end (order = 3)
        playlistDao.updateItemOrder(item1Id, 3)
        val reorderedItems = playlistDao.getPlaylistItemsDirect(playlistId)
        assertEquals("High Intensity", reorderedItems[0].title)
        assertEquals("Cooldown", reorderedItems[1].title)
        assertEquals("Warmup", reorderedItems[2].title)

        // Delete item 2
        playlistDao.deletePlaylistItemById(item2Id)
        val itemsAfterOneDeleted = playlistDao.getPlaylistItemsDirect(playlistId)
        assertEquals(2, itemsAfterOneDeleted.size)

        // Delete entire playlist, verify cascade deletion of remaining playlist_items
        playlistDao.deletePlaylistById(playlistId)
        val itemsAfterCascade = playlistDao.getPlaylistItemsDirect(playlistId)
        assertTrue(itemsAfterCascade.isEmpty())
    }
}
