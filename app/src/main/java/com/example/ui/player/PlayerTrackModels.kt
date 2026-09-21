package com.example.ui.player

import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import java.util.Locale

/**
 * Aspect ratio / video resize modes mapped to screen surface scaling logic.
 */
enum class ResizeMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom")
}

/**
 * Audio track representation exposed to the UI.
 */
@Immutable
data class AudioTrackItem(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val channels: String?,
    val codec: String?,
    val isSelected: Boolean,
    val trackGroup: TrackGroup
)

/**
 * Subtitle / text track representation exposed to the UI.
 */
@Immutable
data class SubtitleTrackItem(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isForced: Boolean,
    val isSelected: Boolean,
    val trackGroup: TrackGroup
)

/**
 * Subtitle selection state modes: Off, Auto, or a specific track.
 */
sealed class SubtitleSelectionMode {
    object Off : SubtitleSelectionMode()
    object Auto : SubtitleSelectionMode()
    data class Track(val track: SubtitleTrackItem) : SubtitleSelectionMode()
}

object PlayerTrackHelper {

    val SUPPORTED_SPEEDS: List<Float> = listOf(
        0.25f,
        0.5f,
        0.75f,
        1.0f,
        1.25f,
        1.5f,
        1.75f,
        2.0f,
        2.5f,
        3.0f,
        4.0f
    )

    fun getAudioTracks(tracks: Tracks, parameters: TrackSelectionParameters): List<AudioTrackItem> {
        val result = mutableListOf<AudioTrackItem>()
        var audioCounter = 0

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    audioCounter++
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    val languageName = format.language?.let { lang ->
                        try {
                            val loc = Locale.forLanguageTag(lang)
                            loc.getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
                        } catch (_: Throwable) {
                            lang
                        }
                    }

                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: languageName?.takeIf { it.isNotBlank() }
                        ?: "Audio #$audioCounter"

                    val channels = when (format.channelCount) {
                        1 -> "Mono (1 ch)"
                        2 -> "Stereo (2 ch)"
                        6 -> "5.1 Surround (6 ch)"
                        8 -> "7.1 Surround (8 ch)"
                        in 3..Int.MAX_VALUE -> "${format.channelCount} ch"
                        else -> null
                    }

                    val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase()

                    result.add(
                        AudioTrackItem(
                            id = "${groupIndex}_$trackIndex",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = languageName,
                            channels = channels,
                            codec = codec,
                            isSelected = isSelected,
                            trackGroup = group.mediaTrackGroup
                        )
                    )
                }
            }
        }
        return result
    }

    fun getSubtitleTracks(tracks: Tracks, parameters: TrackSelectionParameters): List<SubtitleTrackItem> {
        val result = mutableListOf<SubtitleTrackItem>()
        val isTextDisabled = parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        var subtitleCounter = 0

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    subtitleCounter++
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = !isTextDisabled && group.isTrackSelected(trackIndex)

                    val languageName = format.language?.let { lang ->
                        try {
                            val loc = Locale.forLanguageTag(lang)
                            loc.getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
                        } catch (_: Throwable) {
                            lang
                        }
                    }

                    val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0

                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: languageName?.takeIf { it.isNotBlank() }
                        ?: "Subtitle #$subtitleCounter"

                    result.add(
                        SubtitleTrackItem(
                            id = "${groupIndex}_$trackIndex",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = languageName,
                            isForced = isForced,
                            isSelected = isSelected,
                            trackGroup = group.mediaTrackGroup
                        )
                    )
                }
            }
        }
        return result
    }

    fun isAudioAuto(parameters: TrackSelectionParameters): Boolean {
        return parameters.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }
    }

    fun isSubtitleOff(parameters: TrackSelectionParameters): Boolean {
        return parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }

    fun isSubtitleAuto(parameters: TrackSelectionParameters): Boolean {
        return !parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
                parameters.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_TEXT }
    }

    fun selectAudioTrack(player: Player, track: AudioTrackItem?) {
        val currentParams = player.trackSelectionParameters
        val newParams = if (track == null) {
            // Revert to Auto selection
            currentParams.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
        } else {
            // Select explicit track
            currentParams.buildUpon()
                .setOverrideForType(TrackSelectionOverride(track.trackGroup, track.trackIndex))
                .build()
        }
        player.trackSelectionParameters = newParams
    }

    fun selectSubtitleTrack(player: Player, mode: SubtitleSelectionMode) {
        val currentParams = player.trackSelectionParameters
        val newParams = when (mode) {
            SubtitleSelectionMode.Off -> {
                currentParams.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            }
            SubtitleSelectionMode.Auto -> {
                currentParams.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            }
            is SubtitleSelectionMode.Track -> {
                currentParams.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(mode.track.trackGroup, mode.track.trackIndex))
                    .build()
            }
        }
        player.trackSelectionParameters = newParams
    }
}
