package com.example.core.model

import androidx.compose.runtime.Immutable

/**
 * High-level playback status.
 */
enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR
}

/**
 * Immutable state model representing current media playback conditions.
 */
@Immutable
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
)
