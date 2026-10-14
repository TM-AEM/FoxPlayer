package com.example.ui.player

import androidx.compose.runtime.Immutable

/**
 * Sensitivity preset/threshold level for vertical swipe gestures (Volume and Brightness).
 *
 * [touchSlop]: The initial drag distance (in pixels/density units) required to lock into vertical swipe mode.
 * [multiplier]: The scaling factor applied to vertical swipe drag displacement when adjusting percent.
 * A higher multiplier means higher sensitivity (faster change with less swipe distance).
 * A lower multiplier means higher threshold/lower sensitivity (smoother, more gradual changes).
 */
enum class GestureSensitivity(
    val label: String,
    val touchSlop: Float,
    val multiplier: Float,
    val description: String
) {
    LOW(
        label = "Low",
        touchSlop = 26f,
        multiplier = 0.65f,
        description = "Requires longer swipes, fine-grained control"
    ),
    MEDIUM(
        label = "Medium",
        touchSlop = 18f,
        multiplier = 1.0f,
        description = "Standard balanced response"
    ),
    HIGH(
        label = "High",
        touchSlop = 12f,
        multiplier = 1.45f,
        description = "Quick response, less swipe distance needed"
    );

    companion object {
        val DEFAULT = MEDIUM
    }
}

/**
 * Configurable duration options for double-tap seek (rewind on left half, fast-forward on right half).
 *
 * Supported options: 5s, 10s (default), 15s, 30s.
 */
enum class DoubleTapSeekDuration(
    val seconds: Int,
    val label: String
) {
    FIVE(5, "5s"),
    TEN(10, "10s"),
    FIFTEEN(15, "15s"),
    THIRTY(30, "30s");

    val durationMillis: Long get() = seconds * 1000L

    companion object {
        val DEFAULT = TEN

        fun fromSeconds(seconds: Int?): DoubleTapSeekDuration {
            return values().firstOrNull { it.seconds == seconds } ?: DEFAULT
        }
    }
}

/**
 * Settings state for controlling user-configurable gesture sensitivity thresholds
 * and double-tap seek duration.
 */
@Immutable
data class GestureSettingsState(
    val volumeSensitivity: GestureSensitivity = GestureSensitivity.DEFAULT,
    val brightnessSensitivity: GestureSensitivity = GestureSensitivity.DEFAULT,
    val doubleTapSeekDuration: DoubleTapSeekDuration = DoubleTapSeekDuration.DEFAULT
) {
    constructor(
        volumeSensitivity: GestureSensitivity,
        brightnessSensitivity: GestureSensitivity,
        doubleTapSeekSeconds: Int
    ) : this(
        volumeSensitivity = volumeSensitivity,
        brightnessSensitivity = brightnessSensitivity,
        doubleTapSeekDuration = DoubleTapSeekDuration.fromSeconds(doubleTapSeekSeconds)
    )

    // Backward-compatible properties for callers expecting seconds as Int
    val doubleTapSeekSeconds: Int
        get() = doubleTapSeekDuration.seconds

    val doubleTapSeekDurationSeconds: Int
        get() = doubleTapSeekDuration.seconds

    companion object {
        const val DEFAULT_SEEK_SECONDS = 10
        val DEFAULT_SEEK_DURATION = DoubleTapSeekDuration.DEFAULT
        val SEEK_DURATION_OPTIONS: List<Int> = listOf(5, 10, 15, 30)
    }
}

