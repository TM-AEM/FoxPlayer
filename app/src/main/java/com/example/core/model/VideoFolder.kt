package com.example.core.model

import androidx.compose.runtime.Immutable

/**
 * Immutable model representing a grouped video folder/album.
 */
@Immutable
data class VideoFolder(
    val bucketId: String,
    val name: String,
    val path: String,
    val videoCount: Int
)
