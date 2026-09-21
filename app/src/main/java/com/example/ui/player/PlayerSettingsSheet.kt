package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern Material 3 Player Settings Panel supporting:
 * - Playback Speed (0.25x .. 4.0x)
 * - Aspect Ratio (Fit, Fill, Zoom)
 * - Audio Track Selection (Auto + explicit tracks)
 * - Subtitle Track Selection (Off, Auto + explicit tracks)
 */
@Composable
fun PlayerSettingsPanel(
    isOpen: Boolean,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    currentResizeMode: ResizeMode,
    onResizeModeSelected: (ResizeMode) -> Unit,
    audioTracks: List<AudioTrackItem>,
    isAudioAuto: Boolean,
    onAudioTrackSelected: (AudioTrackItem?) -> Unit,
    subtitleTracks: List<SubtitleTrackItem>,
    isSubtitleOff: Boolean,
    isSubtitleAuto: Boolean,
    onSubtitleModeSelected: (SubtitleSelectionMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Panel container
            Surface(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Consume clicks to prevent dismissing scrim */ }
                    )
                    .testTag("player_settings_panel"),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color(0xFF1E1E22),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: Title and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playback Settings",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_settings_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Section 1: Playback Speed
                    Text(
                        text = "Playback Speed (Current: ${String.format(java.util.Locale.US, "%.2f", currentSpeed).trimEnd('0').trimEnd('.')}x)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF90CAF9)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerTrackHelper.SUPPORTED_SPEEDS.forEach { speed ->
                            val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.05f
                            val speedTag = "speed_chip_${String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')}"
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSpeedSelected(speed) },
                                label = {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = Color(0xFF2C2C32),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag(speedTag)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Section 2: Aspect Ratio
                    Text(
                        text = "Aspect Ratio",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF90CAF9)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResizeMode.values().forEach { mode ->
                            val isSelected = currentResizeMode == mode
                            val tag = "aspect_ratio_${mode.name.lowercase()}"
                            FilterChip(
                                selected = isSelected,
                                onClick = { onResizeModeSelected(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = Color(0xFF2C2C32),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Section 3: Audio Tracks
                    Text(
                        text = "Audio Track",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF90CAF9)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto Option for Audio
                    TrackSelectionRow(
                        title = "Auto (System Default)",
                        subtitle = if (isAudioAuto) "Automatically selects best audio stream" else null,
                        isSelected = isAudioAuto,
                        onClick = { onAudioTrackSelected(null) },
                        testTag = "audio_track_auto"
                    )

                    if (audioTracks.isEmpty()) {
                        Text(
                            text = "No additional audio tracks found in stream",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                        )
                    } else {
                        audioTracks.forEach { track ->
                            val details = listOfNotNull(track.language, track.channels, track.codec).joinToString(" • ")
                            TrackSelectionRow(
                                title = track.label,
                                subtitle = details.ifBlank { null },
                                isSelected = !isAudioAuto && track.isSelected,
                                onClick = { onAudioTrackSelected(track) },
                                testTag = "audio_track_${track.id}"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Section 4: Subtitles
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF90CAF9)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Off Option for Subtitles
                    TrackSelectionRow(
                        title = "Off",
                        subtitle = "Disable subtitles",
                        isSelected = isSubtitleOff,
                        onClick = { onSubtitleModeSelected(SubtitleSelectionMode.Off) },
                        testTag = "subtitle_track_off"
                    )

                    // Auto Option for Subtitles
                    TrackSelectionRow(
                        title = "Auto",
                        subtitle = "Show subtitles matching system language",
                        isSelected = isSubtitleAuto,
                        onClick = { onSubtitleModeSelected(SubtitleSelectionMode.Auto) },
                        testTag = "subtitle_track_auto"
                    )

                    if (subtitleTracks.isEmpty()) {
                        Text(
                            text = "No embedded subtitle tracks found in stream",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                        )
                    } else {
                        subtitleTracks.forEach { track ->
                            val details = listOfNotNull(
                                track.language,
                                if (track.isForced) "Forced" else null
                            ).joinToString(" • ")
                            TrackSelectionRow(
                                title = track.label,
                                subtitle = details.ifBlank { null },
                                isSelected = !isSubtitleOff && !isSubtitleAuto && track.isSelected,
                                onClick = { onSubtitleModeSelected(SubtitleSelectionMode.Track(track)) },
                                testTag = "subtitle_track_${track.id}"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null, // Row is clickable
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
