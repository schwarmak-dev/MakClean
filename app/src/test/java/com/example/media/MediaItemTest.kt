package com.example.media

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemTest {

    private fun item(
        sizeBytes: Long = 0L,
        isVideo: Boolean = false,
        durationMs: Long = 0L
    ) = MediaItem(
        id = 1L,
        uri = Uri.parse("content://media/external/images/media/1"),
        title = "test",
        sizeBytes = sizeBytes,
        dateAdded = 0L,
        isVideo = isVideo,
        durationMs = durationMs
    )

    @Test
    fun `sizeText shows KB under one megabyte`() {
        assertEquals("500.0 KB", item(sizeBytes = 512_000L).sizeText)
    }

    @Test
    fun `sizeText shows MB at or above one megabyte`() {
        assertEquals("5.0 MB", item(sizeBytes = 5L * 1024 * 1024).sizeText)
    }

    @Test
    fun `durationText is empty for photos`() {
        assertEquals("", item(isVideo = false, durationMs = 33_000L).durationText)
    }

    @Test
    fun `durationText formats minutes and seconds for videos`() {
        assertEquals("00:33", item(isVideo = true, durationMs = 33_000L).durationText)
        assertEquals("01:35", item(isVideo = true, durationMs = 95_000L).durationText)
    }
}
