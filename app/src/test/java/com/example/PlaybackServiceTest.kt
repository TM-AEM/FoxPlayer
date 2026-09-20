package com.example

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
    fun testPlaybackServicePlayVideoIntent() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val playIntent = Intent(service, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VIDEO
            putExtra(PlaybackService.EXTRA_VIDEO_URI, "content://media/external/video/media/777")
            putExtra(PlaybackService.EXTRA_VIDEO_TITLE, "Wildlife Document")
            putExtra(PlaybackService.EXTRA_VIDEO_ID, 777L)
        }

        service.onStartCommand(playIntent, 0, 1)

        val currentEngine = PlaybackService.currentEngine
        assertNotNull(currentEngine)
        val currentMediaItem = currentEngine?.player?.currentMediaItem
        assertNotNull(currentMediaItem)
        assertEquals("content://media/external/video/media/777", currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals("Wildlife Document", currentMediaItem?.mediaMetadata?.title?.toString())

        controller.destroy()
    }

    @Test
    fun testStartPlaybackHelper() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val videoItem = VideoItem(
            id = 888L,
            uri = "content://media/external/video/media/888",
            title = "Forest River",
            displayName = "Forest River.mp4"
        )

        PlaybackService.startPlayback(service, videoItem)

        val currentEngine = PlaybackService.currentEngine
        assertNotNull(currentEngine)
        val currentMediaItem = currentEngine?.player?.currentMediaItem
        assertNotNull(currentMediaItem)
        assertEquals("content://media/external/video/media/888", currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals("Forest River", currentMediaItem?.mediaMetadata?.title?.toString())

        controller.destroy()
    }
}

