package com.example.media

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val sizeBytes: Long,
    val dateAdded: Long, // seconds
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val type: MediaFilterType = MediaFilterType.ALL,
    val isBlurred: Boolean = false,
    val isDuplicate: Boolean = false,
    val duplicateGroupId: String? = null,
    val filePath: String? = null
) {
    val sizeText: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return if (mb < 1.0) {
                String.format("%.1f KB", sizeBytes.toDouble() / 1024)
            } else {
                String.format("%.1f MB", mb)
            }
        }

    val durationText: String
        get() {
            if (!isVideo) return ""
            val totalSecs = durationMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            return String.format("%02d:%02d", mins, secs)
        }
}

enum class MediaFilterType {
    ALL,
    SCREENSHOTS,
    HEAVY_VIDEOS, // > 100MB
    DUPLICATES, // Same content, duplicates, plus blurry
    BLURRY, // photos with blurry status
    GIFS, // GIF files
    LIGHT_VIDEOS // light (small/meme) videos
}
