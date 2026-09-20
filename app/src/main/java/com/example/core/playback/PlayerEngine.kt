package com.example.core.playback

import android.net.Uri
import androidx.media3.common.Player
import com.example.core.model.PlaybackState
import com.example.core.model.VideoItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing media playback, seeking, speed, volume, and state observation.
 */
interface PlayerEngine {
    /**
     * Observable reactive state flow for the UI or other consumers.
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Direct reference to the underlying Media3 Player instance.
     */
    val player: Player

    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val playbackSpeed: Float
    val volume: Float

    /**
     * Prepares media from a Content URI directly.
     */
    fun prepare(uri: Uri, playWhenReady: Boolean = true)

    /**
     * Prepares media from a [VideoItem] model with metadata.
     */
    fun prepare(videoItem: VideoItem, playWhenReady: Boolean = true)

    /**
     * Basic playback controls.
     */
    fun play()
    fun pause()
    fun playPause()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10_000L)
    fun seekBackward(offsetMs: Long = 10_000L)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun stop()
    fun release()
}
