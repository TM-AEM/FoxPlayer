package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.util.FormatUtils

/**
 * HUD Overlay for [PlayerScreen] containing TopBar, Center Play/Pause, and Bottom Timeline controls.
 *
 * Architecture rules:
 * - Pure Presentation/Interaction layer; decoupled from hardware SurfaceView.
 * - Dispatches play/pause and seek events upward without directly holding ExoPlayer.
 * - Handles local scrubbing state gracefully so progress updates from playback do not jitter slider while dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHud(
    isVisible: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onUserInteraction: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onPipClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Local scrubbing state so slider moves smoothly during drag without jumpy external updates
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val displayPositionMs = if (isScrubbing) {
        scrubPositionMs.toLong().coerceIn(0L, safeDuration)
    } else {
        currentPositionMs.coerceIn(0L, if (safeDuration > 0L) safeDuration else Long.MAX_VALUE)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_hud_overlay")
        ) {
            // Gradient scrim for top bar readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top Bar: Back button and Video Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onUserInteraction()
                        onBack()
                    },
                    modifier = Modifier.testTag("player_hud_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = title.ifBlank { "Video" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag("player_hud_title")
                )

                if (onPipClick != null) {
                    IconButton(
                        onClick = {
                            onUserInteraction()
                            onPipClick()
                        },
                        modifier = Modifier.testTag("player_hud_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onSettingsClick()
                    },
                    modifier = Modifier.testTag("player_hud_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Playback settings",
                        tint = Color.White
                    )
                }
            }

            // Center Control: Play / Pause Button
            Surface(
                onClick = {
                    onUserInteraction()
                    onPlayPauseClick()
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .testTag("player_hud_play_pause_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) {
                            // Using standard pause representation (two bars via drawable or Pause icon)
                            PauseIcon
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Gradient scrim for bottom timeline readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Bottom Bar: Timeline Slider, Timestamps
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("player_hud_bottom_bar")
            ) {
                // Slider
                val sliderValue = if (safeDuration > 0L) {
                    (displayPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { fraction ->
                        onUserInteraction()
                        isScrubbing = true
                        scrubPositionMs = (fraction * safeDuration)
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onUserInteraction()
                        if (safeDuration > 0L) {
                            onSeek(scrubPositionMs.toLong().coerceIn(0L, safeDuration))
                        }
                    },
                    enabled = safeDuration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_hud_timeline_slider")
                )

                // Time labels: Current position and Total duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDuration(displayPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.testTag("player_hud_current_position")
                    )

                    Text(
                        text = FormatUtils.formatDuration(safeDuration),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.testTag("player_hud_duration")
                    )
                }
            }
        }
    }
}

/**
 * Simple Pause Icon Vector to avoid extra icon dependencies.
 */
private val PauseIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.White)
        )
    }.build()
}
