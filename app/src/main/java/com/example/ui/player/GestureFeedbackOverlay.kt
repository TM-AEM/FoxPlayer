package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.util.FormatUtils

/**
 * Visual feedback models for Checkpoint 8 Player Gestures.
 */
sealed class GestureFeedback {
    data class DoubleTapSeek(val deltaSeconds: Int, val isForward: Boolean) : GestureFeedback()
    data class SwipeSeek(val targetPositionMs: Long, val deltaMs: Long, val durationMs: Long) : GestureFeedback()
    data class Volume(val volumePercent: Int) : GestureFeedback()
    data class Brightness(val brightnessPercent: Int) : GestureFeedback()
}

/**
 * Overlay component rendering transient, animated visual indicators for:
 * - Double Tap ±10s Seek (Left / Right side animated pill)
 * - Horizontal Swipe Seek preview (Target time, Delta ±MM:SS)
 * - Vertical Right Swipe Volume level HUD
 * - Vertical Left Swipe Brightness level HUD
 */
@Composable
fun GestureFeedbackOverlay(
    feedback: GestureFeedback?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Double-Tap Seek Pill (Left or Right)
        AnimatedVisibility(
            visible = feedback is GestureFeedback.DoubleTapSeek,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.85f),
            modifier = Modifier
                .align(
                    if ((feedback as? GestureFeedback.DoubleTapSeek)?.isForward == true) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    }
                )
                .padding(horizontal = 48.dp)
        ) {
            val doubleTap = feedback as? GestureFeedback.DoubleTapSeek
            if (doubleTap != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag(if (doubleTap.isForward) "feedback_double_tap_forward" else "feedback_double_tap_rewind"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (doubleTap.isForward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                            contentDescription = if (doubleTap.isForward) "+10s" else "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (doubleTap.isForward) "+${doubleTap.deltaSeconds}s" else "-${doubleTap.deltaSeconds}s",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Horizontal Swipe Seek (Center HUD)
        AnimatedVisibility(
            visible = feedback is GestureFeedback.SwipeSeek,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val swipeSeek = feedback as? GestureFeedback.SwipeSeek
            if (swipeSeek != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .testTag("feedback_swipe_seek"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val sign = if (swipeSeek.deltaMs >= 0) "+" else "-"
                        val absDeltaSeconds = Math.abs(swipeSeek.deltaMs) / 1000L
                        Text(
                            text = "$sign${absDeltaSeconds}s",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${FormatUtils.formatDuration(swipeSeek.targetPositionMs)} / ${FormatUtils.formatDuration(swipeSeek.durationMs)}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Volume Indicator (Right Vertical Swipe)
        AnimatedVisibility(
            visible = feedback is GestureFeedback.Volume,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val vol = feedback as? GestureFeedback.Volume
            if (vol != null) {
                LevelFeedbackCard(
                    title = "Volume",
                    percent = vol.volumePercent,
                    testTag = "feedback_volume_indicator"
                )
            }
        }

        // Brightness Indicator (Left Vertical Swipe)
        AnimatedVisibility(
            visible = feedback is GestureFeedback.Brightness,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val bright = feedback as? GestureFeedback.Brightness
            if (bright != null) {
                LevelFeedbackCard(
                    title = "Brightness",
                    percent = bright.brightnessPercent,
                    testTag = "feedback_brightness_indicator"
                )
            }
        }
    }
}

@Composable
private fun LevelFeedbackCard(
    title: String,
    percent: Int,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .width(160.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (percent.coerceIn(0, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}
