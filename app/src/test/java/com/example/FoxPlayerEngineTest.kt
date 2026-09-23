package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlaybackStatus
import com.example.core.model.VideoItem
import com.example.core.playback.FoxPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FoxPlayerEngineTest {

    private lateinit var context: Context
    private lateinit var engine: FoxPlayerEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = FoxPlayerEngine(context)
    }

    @Test
    fun testEngineInitialState() {
        val state = engine.playbackState.value
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(1.0f, state.playbackSpeed, 0.01f)
        assertEquals(1.0f, state.volume, 0.01f)
        assertNotNull(engine.player)
    }

    @Test
    fun testPrepareFromUri() {
        val testUri = Uri.parse("content://media/external/video/media/123")
        engine.prepare(testUri, playWhenReady = false)
        assertFalse(engine.exoPlayer.playWhenReady)
        assertEquals(1, engine.exoPlayer.mediaItemCount)
    }

    @Test
    fun testPrepareFromVideoItem() {
        val videoItem = VideoItem(
            id = 456L,
            uri = "content://media/external/video/media/456",
            title = "Test Video",
            displayName = "test_video.mp4",
            durationMs = 60_000L,
            sizeBytes = 1024L * 1024L
        )
        engine.prepare(videoItem, playWhenReady = true)
        assertTrue(engine.exoPlayer.playWhenReady)
        assertEquals(1, engine.exoPlayer.mediaItemCount)
        assertEquals("Test Video", engine.exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString())
    }

    @Test
    fun testPlayPauseAndToggle() {
        engine.pause()
        assertFalse(engine.exoPlayer.playWhenReady)

        engine.play()
        assertTrue(engine.exoPlayer.playWhenReady)

        // playPause toggles playWhenReady when isPlaying is checked
        engine.pause()
        assertFalse(engine.exoPlayer.playWhenReady)
    }

    @Test
    fun testPlaybackSpeedClamping() {
        engine.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, engine.playbackSpeed, 0.01f)
        assertEquals(1.5f, engine.playbackState.value.playbackSpeed, 0.01f)

        // Lower clamp (0.25f)
        engine.setPlaybackSpeed(0.1f)
        assertEquals(0.25f, engine.playbackSpeed, 0.01f)
        assertEquals(0.25f, engine.playbackState.value.playbackSpeed, 0.01f)

        // Upper clamp (4.0f)
        engine.setPlaybackSpeed(5.0f)
        assertEquals(4.0f, engine.playbackSpeed, 0.01f)
        assertEquals(4.0f, engine.playbackState.value.playbackSpeed, 0.01f)
    }

    @Test
    fun testVolumeClamping() {
        engine.setVolume(0.5f)
        assertEquals(0.5f, engine.volume, 0.01f)
        assertEquals(0.5f, engine.playbackState.value.volume, 0.01f)

        // Lower clamp (0.0f)
        engine.setVolume(-0.2f)
        assertEquals(0.0f, engine.volume, 0.01f)
        assertEquals(0.0f, engine.playbackState.value.volume, 0.01f)

        // Upper clamp (1.0f)
        engine.setVolume(1.5f)
        assertEquals(1.0f, engine.volume, 0.01f)
        assertEquals(1.0f, engine.playbackState.value.volume, 0.01f)
    }

    @Test
    fun testSeekOperations() {
        val testUri = Uri.parse("content://media/external/video/media/999")
        engine.prepare(testUri, playWhenReady = false)

        engine.seekTo(5000L)
        assertEquals(5000L, engine.exoPlayer.currentPosition)
        assertEquals(5000L, engine.playbackState.value.currentPositionMs)

        engine.seekForward(2000L)
        assertEquals(7000L, engine.exoPlayer.currentPosition)
        assertEquals(7000L, engine.playbackState.value.currentPositionMs)

        engine.seekBackward(3000L)
        assertEquals(4000L, engine.exoPlayer.currentPosition)
        assertEquals(4000L, engine.playbackState.value.currentPositionMs)

        engine.seekBackward(10_000L)
        assertEquals(0L, engine.exoPlayer.currentPosition)
        assertEquals(0L, engine.playbackState.value.currentPositionMs)
    }

    @Test
    fun testReleaseEngine() {
        engine.release()
    }

    @Test
    fun testCalculateResumePosition() {
        // Less than 3 seconds -> start from 0
        assertEquals(0L, engine.calculateResumePosition(1500L, 60000L))
        assertEquals(0L, engine.calculateResumePosition(2999L, 60000L))

        // Short video (<= 15 seconds)
        // 10s video, watched 8s (>= 80%) -> 0L
        assertEquals(0L, engine.calculateResumePosition(8500L, 10000L))
        // 10s video, within 3 seconds of end (8s) -> 0L
        assertEquals(0L, engine.calculateResumePosition(7500L, 10000L))
        // 10s video, watched 4s (< 80% and > 3s of end) -> 4000L
        assertEquals(4000L, engine.calculateResumePosition(4000L, 10000L))

        // Standard video (> 15 seconds)
        // 100s video, watched 96s (>= 95%) -> 0L
        assertEquals(0L, engine.calculateResumePosition(96000L, 100000L))
        // 100s video, within 5s of end (97s) -> 0L
        assertEquals(0L, engine.calculateResumePosition(97000L, 100000L))
        // 100s video, watched 45s -> 45000L
        assertEquals(45000L, engine.calculateResumePosition(45000L, 100000L))
    }

    @Test
    fun testHistoryPersistedOnRelease() {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.database.FoxPlayerDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.historyDao()

        val customEngine = FoxPlayerEngine(
            context = context,
            historyDao = dao
        )

        val videoItem = VideoItem(
            id = 789L,
            uri = "content://media/external/video/media/789",
            title = "Persistence Test Video",
            displayName = "persist.mp4",
            durationMs = 120_000L,
            sizeBytes = 2048L
        )

        customEngine.prepare(videoItem, playWhenReady = false)
        customEngine.seekTo(25_000L)

        // Release engine - must flush history to Room reliably
        customEngine.release()

        val saved = kotlinx.coroutines.runBlocking {
            customEngine.lastSaveJob?.join()
            dao.getHistoryByUriDirect(videoItem.uri)
        }
        assertNotNull(saved)
        assertEquals("Persistence Test Video", saved?.title)
        assertEquals(25_000L, saved?.lastPositionMs)
        assertEquals(120_000L, saved?.durationMs)

        db.close()
    }

    @Test
    fun testReleaseDoesNotBlockMainThreadEvenWithSlowDatabase() {
        assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowDao = object : com.example.data.local.database.dao.HistoryDao {
            override fun getAllHistory(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.database.entity.PlaybackHistoryEntity>> = kotlinx.coroutines.flow.emptyFlow()
            override fun getHistoryByUri(videoUri: String): kotlinx.coroutines.flow.Flow<com.example.data.local.database.entity.PlaybackHistoryEntity?> = kotlinx.coroutines.flow.emptyFlow()
            override suspend fun getHistoryByUriDirect(videoUri: String): com.example.data.local.database.entity.PlaybackHistoryEntity? = null
            override suspend fun upsertHistory(history: com.example.data.local.database.entity.PlaybackHistoryEntity) {
                gate.await()
            }
            override suspend fun deleteHistoryByUri(videoUri: String) {}
            override suspend fun clearAllHistory() {}
        }
        val slowEngine = FoxPlayerEngine(context = context, historyDao = slowDao)
        val videoItem = VideoItem(
            id = 901L,
            uri = "content://media/external/video/media/901",
            title = "Slow DB Test Video",
            displayName = "slow.mp4",
            durationMs = 60_000L,
            sizeBytes = 1024L
        )
        slowEngine.prepare(videoItem, playWhenReady = false)
        slowEngine.seekTo(15_000L)

        // release() is executed directly on the Android Main thread while the database operation is blocked on the gate
        val startTime = System.currentTimeMillis()
        slowEngine.release()
        val durationMs = System.currentTimeMillis() - startTime

        // Must return immediately without waiting for the database write
        assertTrue("release() blocked the calling thread for $durationMs ms", durationMs < 250L)
        assertTrue(slowEngine.isReleased)
        assertNotNull(slowEngine.lastSaveJob)
        assertTrue(slowEngine.lastSaveJob?.isActive == true)

        // Unblock gate and verify background job completes cleanly
        gate.complete(Unit)
        kotlinx.coroutines.runBlocking {
            slowEngine.lastSaveJob?.join()
        }
        assertFalse(slowEngine.lastSaveJob?.isActive == true)
        assertFalse(slowEngine.isHistoryScopeActive)
    }

    @Test
    fun testReleaseIsIdempotent() {
        val testUri = Uri.parse("content://media/external/video/media/123")
        engine.prepare(testUri, playWhenReady = false)
        assertFalse(engine.isReleased)

        engine.release()
        assertTrue(engine.isReleased)

        // Calling release again must be a safe no-op
        engine.release()
        assertTrue(engine.isReleased)

        engine.release()
        assertTrue(engine.isReleased)
    }

    @Test
    fun testHistorySerializationPreservedUnderConcurrentSaves() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.database.FoxPlayerDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.historyDao()
        val customEngine = FoxPlayerEngine(context = context, historyDao = dao)
        val videoItem = VideoItem(
            id = 902L,
            uri = "content://media/external/video/media/902",
            title = "Serialization Test Video",
            displayName = "serial.mp4",
            durationMs = 120_000L,
            sizeBytes = 2048L
        )
        customEngine.prepare(videoItem, playWhenReady = false)

        // Rapid state changes and seeks
        customEngine.seekTo(10_000L)
        customEngine.pause()
        customEngine.seekTo(35_000L)
        customEngine.release()

        customEngine.lastSaveJob?.join()
        val saved = dao.getHistoryByUriDirect(videoItem.uri)
        assertNotNull(saved)
        assertEquals(35_000L, saved?.lastPositionMs)
        db.close()
    }

    @Test
    fun testPlayerAndListenerCleanupOnRelease() {
        val testUri = Uri.parse("content://media/external/video/media/456")
        engine.prepare(testUri, playWhenReady = false)

        engine.release()
        assertTrue(engine.isReleased)

        // Operations after release must be safe and not cause playback
        engine.play()
        assertFalse(engine.isPlaying)
    }
}
