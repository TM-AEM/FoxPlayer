package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppSettings
import com.example.core.model.VideoItem
import com.example.core.playback.FoxPlayerEngine
import com.example.data.local.database.dao.HistoryDao
import com.example.data.local.database.entity.PlaybackHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression protection for the two production prepare paths.
 *
 * [FoxPlayerEngine.prepare] is implemented once for a bare [Uri] and once for a
 * [VideoItem]. Both decide the resume position from the same history row; a
 * change made to only one of them would make a video resume differently
 * depending on which entry point opened it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FoxPlayerEngineResumeParityTest {

    private lateinit var context: Context
    private val mediaUri = "content://media/external/video/media/777"
    private val history = PlaybackHistoryEntity(
        videoUri = mediaUri,
        title = "Parity History",
        durationMs = 120_000L,
        lastPositionMs = 60_000L,
        watchPercentage = 0.5f
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Engines are released inside each test so their async resume work
        // cannot outlive Robolectric's SQLite/looper teardown.
    }

    @Test
    fun bothPreparePathsSelectTheSameMediaAndResumePosition() {
        val uriEngine = newEngine()
        val itemEngine = newEngine()

        try {
            uriEngine.prepare(Uri.parse(mediaUri), playWhenReady = false)
            itemEngine.prepare(
                VideoItem(
                    id = 777L,
                    uri = mediaUri,
                    title = "Parity Video",
                    displayName = "parity.mp4",
                    durationMs = 120_000L
                ),
                playWhenReady = false
            )

            assertEquals(
                "Both prepare paths must select the same media URI",
                mediaUri,
                uriEngine.exoPlayer.currentMediaItem?.localConfiguration?.uri.toString()
            )
            assertEquals(
                "Both prepare paths must select the same media URI",
                mediaUri,
                itemEngine.exoPlayer.currentMediaItem?.localConfiguration?.uri.toString()
            )
            assertEquals(
                "Both prepare paths must resume to the stored position",
                60_000L,
                uriEngine.exoPlayer.currentPosition
            )
            assertEquals(
                "Both prepare paths must resume to the stored position",
                60_000L,
                itemEngine.exoPlayer.currentPosition
            )
            assertFalse(uriEngine.exoPlayer.playWhenReady)
            assertFalse(itemEngine.exoPlayer.playWhenReady)

            // This is a genuine difference that must be preserved: only the
            // VideoItem path has metadata for the session notification.
            assertNull(
                "The bare URI path has no caller-supplied title",
                uriEngine.exoPlayer.currentMediaItem?.mediaMetadata?.title
            )
            assertEquals(
                "The VideoItem path must keep its title metadata",
                "Parity Video",
                itemEngine.exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString()
            )
        } finally {
            uriEngine.release()
            itemEngine.release()
        }
    }

    @Test
    fun bothPreparePathsHonourTheResumeSetting() {
        val uriEngine = newEngine(resumePlayback = false)
        val itemEngine = newEngine(resumePlayback = false)

        try {
            uriEngine.prepare(Uri.parse(mediaUri), playWhenReady = true)
            itemEngine.prepare(
                VideoItem(
                    id = 777L,
                    uri = mediaUri,
                    title = "Parity Video",
                    displayName = "parity.mp4",
                    durationMs = 120_000L
                ),
                playWhenReady = true
            )

            assertEquals(
                "Resume disabled must start the bare URI path at the beginning",
                0L,
                uriEngine.exoPlayer.currentPosition
            )
            assertEquals(
                "Resume disabled must start the VideoItem path at the beginning",
                0L,
                itemEngine.exoPlayer.currentPosition
            )
            assertEquals(true, uriEngine.exoPlayer.playWhenReady)
            assertEquals(true, itemEngine.exoPlayer.playWhenReady)
        } finally {
            uriEngine.release()
            itemEngine.release()
        }
    }

    private fun newEngine(resumePlayback: Boolean = true): FoxPlayerEngine =
        FoxPlayerEngine(
            context = context,
            historyDao = FixedHistoryDao(history),
            appSettingsRepository = FixedAppSettingsRepository(
                AppSettings(resumePlayback = resumePlayback)
            ),
            // The resume lookup is launched on a dispatcher. An unconfined
            // dispatcher makes the lookup and the resulting seek complete before
            // prepare() returns, so the assertion observes the outcome rather
            // than racing it with a sleep.
            mainDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined
        )

    private class FixedHistoryDao(
        private val history: PlaybackHistoryEntity
    ) : HistoryDao {

        override fun getAllHistory(): Flow<List<PlaybackHistoryEntity>> =
            flowOf(listOf(history))

        override fun getHistoryByUri(videoUri: String): Flow<PlaybackHistoryEntity?> =
            flowOf(history)

        override suspend fun getHistoryByUriDirect(videoUri: String): PlaybackHistoryEntity =
            history

        override suspend fun upsertHistory(history: PlaybackHistoryEntity) = Unit
        override suspend fun deleteHistoryByUri(videoUri: String) = Unit
        override suspend fun clearAllHistory() = Unit
    }
}
