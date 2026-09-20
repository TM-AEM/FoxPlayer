package com.example.core.model

import androidx.compose.runtime.Immutable

/**
 * Immutable model representing a video file and its metadata.
 */
@Immutable
data class VideoItem(
    val id: Long,
    val uri: String,
    val title: String,
    val displayName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val dateModified: Long = 0L,
    val relativePath: String = "",
    val bucketName: String = "",
    val mimeType: String = "video/*"
)
