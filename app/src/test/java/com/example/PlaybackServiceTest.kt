package com.example

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.VideoItem
import com.example.core.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackServiceTest {

    @Test
    fun testPlaybackServiceLifecycle() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        assertNotNull(service)
        assertNotNull(PlaybackService.currentEngine)
        assertNotNull(PlaybackService.currentEngine?.player)

        // Bind service with MediaSessionService intent
        val intent = Intent("androidx.media3.session.MediaSessionService")
        val binder = service.onBind(intent)
        assertNotNull(binder)

        // Destroy service and verify clean shutdown
        controller.destroy()
        assertNull(PlaybackService.currentEngine)
    }

    @Test
    fun testStartPlaybackDoesNotCallPrepareDirectly() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val videoItem = VideoItem(
            id = 555L,
            uri = "content://media/external/video/media/555",
            title = "Direct Prepare Check",
            displayName = "direct.mp4",
            durationMs = 60000L,
            sizeBytes = 2048000L
        )

        // Call startPlayback: it must ONLY build and send the Intent, not call prepare() directly
        PlaybackService.startPlayback(service, videoItem)

        val engine = PlaybackService.currentEngine
        assertNotNull(engine)
        // Verify engine player has NOT been prepared directly by startPlayback
        assertNull("startPlayback must not prepare the player directly", engine?.player?.currentMediaItem)

        // Verify that startPlayback sent the service Intent with all expected extras
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        val startedIntent = shadowApp.nextStartedService
        assertNotNull("startPlayback must dispatch intent to start the service", startedIntent)
        assertEquals(PlaybackService.ACTION_PLAY_VIDEO, startedIntent.action)
        assertEquals("content://media/external/video/media/555", startedIntent.getStringExtra(PlaybackService.EXTRA_VIDEO_URI))
        assertEquals(555L, startedIntent.getLongExtra(PlaybackService.EXTRA_VIDEO_ID, -1L))
        assertEquals("Direct Prepare Check", startedIntent.getStringExtra(PlaybackService.EXTRA_VIDEO_TITLE))
        assertEquals("direct.mp4", startedIntent.getStringExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME))
        assertEquals(60000L, startedIntent.getLongExtra(PlaybackService.EXTRA_VIDEO_DURATION, 0L))
        assertEquals(2048000L, startedIntent.getLongExtra(PlaybackService.EXTRA_VIDEO_SIZE, 0L))

        controller.destroy()
    }

    @Test
    fun testOnStartCommandPreparesVideoOnce() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val playIntent = Intent(service, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/777")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Wildlife Document")
            putExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME, "wildlife.mp4")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 777L)
            putExtra(PlaybackService.EXTRA_VIDEO_DURATION, 120000L)
            putExtra(PlaybackService.EXTRA_VIDEO_SIZE, 5000000L)
        }

        service.onStartCommand(playIntent, 0, 1)

        val currentEngine = PlaybackService.currentEngine
        assertNotNull(currentEngine)
        val currentMediaItem = currentEngine?.player?.currentMediaItem
        assertNotNull(currentMediaItem)
        assertEquals("content://media/external/video/media/777", currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals("Wildlife Document", currentMediaItem?.mediaMetadata?.title?.toString())
        assertEquals("777", currentMediaItem?.mediaId)

        controller.destroy()
    }

    @Test
    fun testSequentialVideoPlaybackPreparation() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val videoA = Intent(service, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/100")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Video A")
            putExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME, "videoA.mp4")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 100L)
        }

        val videoB = Intent(service, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/200")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Video B")
            putExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME, "videoB.mp4")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 200L)
        }

        // Deliver Video A
        service.onStartCommand(videoA, 0, 1)
        val engine = PlaybackService.currentEngine
        assertNotNull(engine)
        assertEquals(
            "content://media/external/video/media/100",
            engine?.player?.currentMediaItem?.localConfiguration?.uri.toString()
        )
        assertEquals("Video A", engine?.player?.currentMediaItem?.mediaMetadata?.title?.toString())

        // Deliver Video B
        service.onStartCommand(videoB, 0, 2)
        assertEquals(
            "content://media/external/video/media/200",
            engine?.player?.currentMediaItem?.localConfiguration?.uri.toString()
        )
        assertEquals("Video B", engine?.player?.currentMediaItem?.mediaMetadata?.title?.toString())

        controller.destroy()
    }

    @Test
    fun testMetadataPreservedInVideoItemPreparation() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val playIntent = Intent(service, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/999")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Metadata Test")
            putExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME, "metadata_test.mp4")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 999L)
            putExtra(PlaybackService.EXTRA_VIDEO_DURATION, 45000L)
            putExtra(PlaybackService.EXTRA_VIDEO_SIZE, 12345678L)
        }

        service.onStartCommand(playIntent, 0, 1)

        val currentEngine = PlaybackService.currentEngine
        assertNotNull(currentEngine)
        val mediaItem = currentEngine?.player?.currentMediaItem
        assertNotNull(mediaItem)

        // Verify ID, URI, and Title
        assertEquals("999", mediaItem?.mediaId)
        assertEquals("content://media/external/video/media/999", mediaItem?.localConfiguration?.uri.toString())
        assertEquals("Metadata Test", mediaItem?.mediaMetadata?.title?.toString())

        controller.destroy()
    }

    @Test
    fun testColdStartPlaybackService() {
        val playIntent = Intent(ApplicationProvider.getApplicationContext(), PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/111")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Cold Start Video")
            putExtra(PlaybackService.EXTRA_VIDEO_DISPLAY_NAME, "cold_start.mp4")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 111L)
            putExtra(PlaybackService.EXTRA_VIDEO_DURATION, 90000L)
            putExtra(PlaybackService.EXTRA_VIDEO_SIZE, 3000000L)
        }

        val controller = Robolectric.buildService(PlaybackService::class.java, playIntent)
        controller.create().startCommand(0, 1)
        val service = controller.get()
        assertNotNull(service)

        val engine = PlaybackService.currentEngine
        assertNotNull("Engine must be initialized on cold start", engine)
        assertNotNull("MediaItem must be prepared on cold start", engine?.player?.currentMediaItem)
        assertEquals("content://media/external/video/media/111", engine?.player?.currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals("Cold Start Video", engine?.player?.currentMediaItem?.mediaMetadata?.title?.toString())

        controller.destroy()
    }
}

