package com.example

import com.example.core.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatDuration_zeroAndNegative() {
        assertEquals("00:00", FormatUtils.formatDuration(0L))
        assertEquals("00:00", FormatUtils.formatDuration(-1000L))
    }

    @Test
    fun testFormatDuration_secondsOnly() {
        assertEquals("00:05", FormatUtils.formatDuration(5_000L))
        assertEquals("00:45", FormatUtils.formatDuration(45_000L))
    }

    @Test
    fun testFormatDuration_minutesAndSeconds() {
        assertEquals("03:24", FormatUtils.formatDuration(204_000L))
        assertEquals("12:05", FormatUtils.formatDuration(725_000L))
    }

    @Test
    fun testFormatDuration_hoursMinutesSeconds() {
        assertEquals("1:05:32", FormatUtils.formatDuration(3_932_000L))
        assertEquals("2:00:00", FormatUtils.formatDuration(7_200_000L))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", FormatUtils.formatFileSize(0L))
        assertEquals("0 B", FormatUtils.formatFileSize(-10L))
        assertEquals("500 B", FormatUtils.formatFileSize(500L))
        assertEquals("1.0 KB", FormatUtils.formatFileSize(1024L))
        assertEquals("10.5 MB", FormatUtils.formatFileSize((10.5 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB", FormatUtils.formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatDate() {
        assertEquals("", FormatUtils.formatDate(0L))
        // Valid timestamp
        val dateString = FormatUtils.formatDate(1710000000L) // in seconds
        assertTrue("Date string should not be blank", dateString.isNotBlank())
    }
}
