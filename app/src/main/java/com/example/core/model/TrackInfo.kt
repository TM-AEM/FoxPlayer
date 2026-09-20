package com.example.core.model

import androidx.compose.runtime.Immutable

/**
 * Type of media stream track.
 */
enum class TrackType {
    AUDIO,
    SUBTITLE,
    VIDEO
}

/**
 * Immutable model representing an audio, subtitle, or video track.
 */
@Immutable
data class TrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val type: TrackType,
    val isSelected: Boolean = false
)
