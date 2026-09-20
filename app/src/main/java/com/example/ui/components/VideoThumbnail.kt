package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.thumbnail.ThumbnailEngine
import com.example.core.thumbnail.ThumbnailEngineImpl
import com.example.core.util.FormatUtils

/**
 * Reusable Compose component for asynchronously displaying a video thumbnail with an optional duration badge.
 * Features:
 * - Lazy-loading only when visible.
 * - Instant cache hit if present in memory.
 * - Battery-efficient background decoding via [ThumbnailEngine].
 * - Duration pill overlay (YouTube style).
 */
@Composable
fun VideoThumbnail(
    uri: String,
    durationMs: Long,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    thumbnailEngine: ThumbnailEngine = rememberThumbnailEngine()
) {
    var bitmap by remember(uri) {
        mutableStateOf(thumbnailEngine.getCachedThumbnail(uri))
    }

    LaunchedEffect(uri) {
        if (bitmap == null) {
            bitmap = thumbnailEngine.loadThumbnail(uri)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("video_thumbnail_image")
            )
        } else {
            // Elegant placeholder
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = contentDescription ?: "Video Thumbnail Placeholder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(36.dp)
                    .testTag("video_thumbnail_placeholder")
            )
        }

        // Duration Badge (Bottom-Right, e.g., "04:15")
        if (durationMs > 0L) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .testTag("video_duration_badge")
            ) {
                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun rememberThumbnailEngine(): ThumbnailEngine {
    val context = LocalContext.current
    return remember { ThumbnailEngineImpl.getInstance(context) }
}
