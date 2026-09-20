package com.example.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.pow

/**
 * Utility functions for formatting duration, file sizes, and dates.
 */
object FormatUtils {

    /**
     * Formats duration in milliseconds into a user-friendly timestamp:
     * - Less than 1 hour: `MM:SS` (e.g., "04:15")
     * - 1 hour or more: `H:MM:SS` (e.g., "1:22:45")
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"

        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats bytes into human-readable data size (e.g., "45.2 MB", "1.2 GB").
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            String.format(Locale.US, "%d %s", bytes, units[digitGroups])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    /**
     * Formats UNIX timestamp in seconds or milliseconds to readable date string.
     */
    fun formatDate(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        // Determine whether timestamp is in seconds or ms
        val millis = if (timestampSeconds < 100_000_000_000L) {
            timestampSeconds * 1000L
        } else {
            timestampSeconds
        }
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}
