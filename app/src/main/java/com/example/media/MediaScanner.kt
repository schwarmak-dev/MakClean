/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 * 
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object MediaScanner {
    private const val TAG = "MediaScanner"

    // Below this Laplacian variance an image is considered out-of-focus / blurry.
    // Tuned for the downscaled analysis bitmap below; adjust on real devices if needed.
    private const val BLUR_VARIANCE_THRESHOLD = 120.0

    // Longest edge (px) the bitmap is downscaled to before blur analysis. Smaller = faster.
    private const val BLUR_ANALYSIS_MAX_EDGE = 160

    // Max number of fresh photo decodes per blur scan, so "Borrosas" finishes fast even on huge
    // galleries. The most recent photos are analyzed first; cached results are reused for free.
    private const val MAX_BLUR_ANALYSIS = 400

    // Hard time budget (ms) for a single blur scan. Whatever has been analyzed when this elapses
    // is used, and the scan returns immediately — so the "Analizando" screen never hangs.
    private const val BLUR_TIME_BUDGET_MS = 3500L

    // Content-based results are cached by MediaStore id: a given id's bytes never change,
    // so repeated scans (filter scan, empty check, analytics) reuse work instead of re-reading files.
    private val signatureCache = ConcurrentHashMap<Long, String>()
    private val blurCache = ConcurrentHashMap<Long, Boolean>()

    // Real scanner utilizing Android MediaStore
    fun scanDeviceGallery(context: Context, filter: MediaFilterType): List<MediaItem> {
        val allItems = mutableListOf<MediaItem>()
        val resolver: ContentResolver = context.contentResolver

        // Define queries for Images and Videos
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA
        )

        // Read Images
        try {
            resolver.query(
                imageUri,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image-$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val dataPath = cursor.getString(dataCol) ?: ""
                    val contentUri = ContentUris.withAppendedId(imageUri, id)

                    val isScreenshot = name.contains("Screenshot", ignoreCase = true) ||
                            dataPath.contains("Screenshots", ignoreCase = true)
                    val isGif = name.endsWith(".gif", ignoreCase = true) || dataPath.endsWith(".gif", ignoreCase = true)

                    val item = MediaItem(
                        id = id,
                        uri = contentUri,
                        title = name,
                        sizeBytes = size,
                        dateAdded = dateAdded,
                        isVideo = false,
                        type = if (isScreenshot) {
                            MediaFilterType.SCREENSHOTS
                        } else if (isGif) {
                            MediaFilterType.GIFS
                        } else {
                            MediaFilterType.ALL
                        },
                        filePath = dataPath
                    )

                    // Collect everything; duplicates are detected by content after the full scan.
                    allItems.add(item)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning images: ${e.message}")
        }

        // Read Videos
        try {
            resolver.query(
                videoUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video-$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durCol)
                    val dataPath = cursor.getString(dataCol) ?: ""
                    val contentUri = ContentUris.withAppendedId(videoUri, id)

                    val isHeavy = size > 15 * 1024 * 1024 // > 15MB is heavy for rapid clearing

                    val item = MediaItem(
                        id = id + 1_000_000_000L, // offset to avoid colliding with image IDs
                        uri = contentUri,
                        title = name,
                        sizeBytes = size,
                        dateAdded = dateAdded,
                        isVideo = true,
                        durationMs = duration,
                        type = if (isHeavy) MediaFilterType.HEAVY_VIDEOS else MediaFilterType.LIGHT_VIDEOS,
                        filePath = dataPath
                    )

                    // Collect everything; duplicates are detected by content after the full scan.
                    allItems.add(item)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning videos: ${e.message}")
        }

        // Deep, per-file analysis (content hashing for duplicates, bitmap decoding for blur)
        // is expensive on large galleries, so it ONLY runs when the user explicitly opens that
        // filter's deck — never during the lightweight analytics/metadata scan on the filter
        // screen. This keeps opening the app and the analytics panel instant.
        // Wrapped so a failure can never leave the caller stuck on the "Analizando" screen.
        val analyzed = try {
            when (filter) {
                MediaFilterType.DUPLICATES -> detectDuplicates(resolver, allItems)
                MediaFilterType.BLURRY -> detectBlur(resolver, allItems)
                else -> allItems // metadata-only categories (videos, screenshots, gifs) are instant
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Deep analysis failed for $filter, returning unanalyzed items: ${t.message}")
            allItems
        }

        return analyzed
            .filter { shouldInclude(it, filter) }
            .sortedByDescending { it.dateAdded }
    }

    /**
     * Flags real duplicates by comparing file contents.
     *
     * Strategy (cheap first, expensive only when needed):
     *  1. Group by exact byte size — true copies always share the same size, and most
     *     files have a unique size, so the vast majority are ruled out for free.
     *  2. Only for size-collision groups, compute a content signature (SHA-256 over the
     *     first chunk of bytes, mixed with the size). Identical copies produce identical
     *     signatures; distinct files do not.
     *  3. Any signature shared by 2+ items marks every member as a duplicate.
     */
    private fun detectDuplicates(resolver: ContentResolver, items: List<MediaItem>): List<MediaItem> {
        if (items.size < 2) return items

        val signatureByItemId = HashMap<Long, String>()
        val sizeGroups = items.groupBy { it.sizeBytes }

        for ((size, group) in sizeGroups) {
            if (size <= 0L || group.size < 2) continue // unique size cannot be a duplicate
            for (item in group) {
                val sig = signatureCache[item.id]
                    ?: computeContentSignature(resolver, item.uri, size)?.also { signatureCache[item.id] = it }
                if (sig != null) signatureByItemId[item.id] = sig
            }
        }

        if (signatureByItemId.isEmpty()) return items

        val signatureCounts = signatureByItemId.values.groupingBy { it }.eachCount()

        return items.map { item ->
            val signature = signatureByItemId[item.id]
            if (signature != null && (signatureCounts[signature] ?: 0) > 1) {
                item.copy(
                    isDuplicate = true,
                    duplicateGroupId = "dup_${signature.take(16)}"
                )
            } else {
                item
            }
        }
    }

    // SHA-256 of the size plus up to the first 1 MB of the file. Two byte-identical copies
    // always hash to the same value; the size prefix prevents collisions across file sizes.
    private fun computeContentSignature(resolver: ContentResolver, uri: Uri, size: Long): String? {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(size.toString().toByteArray())
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                val maxBytes = 1L * 1024 * 1024 // 1 MB is enough to fingerprint identical copies
                while (totalRead < maxBytes) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (t: Throwable) {
            Log.e(TAG, "computeContentSignature failed for $uri: ${t.message}")
            null
        }
    }

    /**
     * Flags real out-of-focus photos using the variance of the Laplacian — the standard
     * cheap focus metric: a sharp image has lots of high-frequency edge energy (high variance),
     * a blurry one has little (low variance). Only regular photos are analyzed (screenshots,
     * GIFs and videos are skipped), each image is downscaled first, and results are cached.
     *
     * Decoding every photo in a big gallery would take minutes, so we bound the number of FRESH
     * decodes per scan to the most recent [MAX_BLUR_ANALYSIS] photos. Already-cached results are
     * always reused (free), so coverage grows as the user keeps scanning.
     */
    private fun detectBlur(resolver: ContentResolver, items: List<MediaItem>): List<MediaItem> {
        val photos = items
            .filter { !it.isVideo && it.type == MediaFilterType.ALL }
            .sortedByDescending { it.dateAdded }

        var freshDecodes = 0
        val deadline = System.currentTimeMillis() + BLUR_TIME_BUDGET_MS
        for (item in photos) {
            if (blurCache.containsKey(item.id)) continue // already known — reuse, costs nothing
            if (freshDecodes >= MAX_BLUR_ANALYSIS) break // bound the work so the scan finishes fast
            if (System.currentTimeMillis() > deadline) break // hard stop — never hang the scan
            val variance = computeLaplacianVariance(resolver, item.uri)
            blurCache[item.id] = variance != null && variance < BLUR_VARIANCE_THRESHOLD
            freshDecodes++
        }

        return items.map { item ->
            if (blurCache[item.id] == true) {
                item.copy(isBlurred = true, type = MediaFilterType.BLURRY)
            } else {
                item
            }
        }
    }

    // Decodes a downscaled grayscale view of the image and returns the variance of its
    // Laplacian. Returns null if the image cannot be decoded.
    private fun computeLaplacianVariance(resolver: ContentResolver, uri: Uri): Double? {
        return try {
            // First pass: read bounds only to pick a downsample factor.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sample = 1
            val largestEdge = maxOf(srcW, srcH)
            while (largestEdge / sample > BLUR_ANALYSIS_MAX_EDGE) sample *= 2

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val w = bitmap.width
            val h = bitmap.height
            if (w < 3 || h < 3) {
                bitmap.recycle()
                return null
            }

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()

            // Convert to grayscale luminance once.
            val gray = DoubleArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
            }

            // Apply 3x3 Laplacian kernel (edge detector) and accumulate mean/variance.
            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    val lap = 4.0 * gray[idx] -
                        gray[idx - 1] - gray[idx + 1] -
                        gray[idx - w] - gray[idx + w]
                    sum += lap
                    sumSq += lap * lap
                    count++
                }
            }
            if (count == 0) return null
            val mean = sum / count
            sumSq / count - mean * mean
        } catch (t: Throwable) {
            Log.e(TAG, "computeLaplacianVariance failed for $uri: ${t.message}")
            null
        }
    }

    private fun shouldInclude(item: MediaItem, filter: MediaFilterType): Boolean {
        return when (filter) {
            MediaFilterType.ALL -> true
            MediaFilterType.SCREENSHOTS -> item.type == MediaFilterType.SCREENSHOTS
            MediaFilterType.HEAVY_VIDEOS -> item.type == MediaFilterType.HEAVY_VIDEOS
            MediaFilterType.DUPLICATES -> item.isDuplicate
            MediaFilterType.BLURRY -> item.isBlurred
            MediaFilterType.GIFS -> item.type == MediaFilterType.GIFS || item.title.endsWith(".gif", ignoreCase = true)
            MediaFilterType.LIGHT_VIDEOS -> item.type == MediaFilterType.LIGHT_VIDEOS
        }
    }

    // High fidelity beautiful mocked cards for a premium preview
    fun getMockMediaItems(): List<MediaItem> {
        val now = System.currentTimeMillis() / 1000
        val dummyUri = Uri.parse("https://images.unsplash.com")

        return listOf(
            // Duplicates Pair 1
            MediaItem(
                id = 101L,
                uri = Uri.parse("https://images.unsplash.com/photo-1501854140801-50d01698950b?auto=format&fit=crop&w=800&q=80"),
                title = "Fuji Mountain Peak.jpg",
                sizeBytes = 14200000L, // 14.2 MB
                dateAdded = now - 3600 * 2, // 2h ago
                isVideo = false,
                isDuplicate = true,
                duplicateGroupId = "group_fuji",
                type = MediaFilterType.DUPLICATES
            ),
            MediaItem(
                id = 102L,
                uri = Uri.parse("https://images.unsplash.com/photo-1501854140801-50d01698950b?auto=format&fit=crop&w=800&q=80"),
                title = "Fuji Mountain Peak (Copy).jpg",
                sizeBytes = 14200000L, // 14.2 MB
                dateAdded = now - 3600 * 2 + 5,
                isVideo = false,
                isDuplicate = true,
                duplicateGroupId = "group_fuji",
                type = MediaFilterType.DUPLICATES
            ),

            // Heavy Video
            MediaItem(
                id = 201L,
                uri = Uri.parse("https://assets.mixkit.co/videos/preview/mixkit-cyberpunk-neon-city-street-at-night-40348-large.mp4"),
                title = "Neon Tokyo Cinematic Drone.mp4",
                sizeBytes = 154600000L, // 154.6 MB
                dateAdded = now - 3600 * 12, // 12h ago
                isVideo = true,
                durationMs = 45000L,
                type = MediaFilterType.HEAVY_VIDEOS
            ),

            // Blurry
            MediaItem(
                id = 301L,
                uri = Uri.parse("https://images.unsplash.com/photo-1557682250-33bd709cbe85?auto=format&fit=crop&w=800&q=80"),
                title = "Blurry Neon Party Night.jpg",
                sizeBytes = 8400000L, // 8.4 MB
                dateAdded = now - 3600 * 24 * 1, // 1 day ago
                isVideo = false,
                isBlurred = true,
                type = MediaFilterType.BLURRY
            ),

            // Screenshots
            MediaItem(
                id = 401L,
                uri = Uri.parse("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80"),
                title = "Screenshot_20260524_Cryptocurrency_Wallet.png",
                sizeBytes = 1100000L, // 1.1 MB
                dateAdded = now - 3600 * 24 * 2, // 2 days ago
                isVideo = false,
                type = MediaFilterType.SCREENSHOTS
            ),
            MediaItem(
                id = 402L,
                uri = Uri.parse("https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?auto=format&fit=crop&w=800&q=80"),
                title = "Screenshot_20260523_Hotel_Receipt.png",
                sizeBytes = 2300000L, // 2.3 MB
                dateAdded = now - 3600 * 24 * 3, // 3 days ago
                isVideo = false,
                type = MediaFilterType.SCREENSHOTS
            ),

            // Duplicates Pair 2
            MediaItem(
                id = 501L,
                uri = Uri.parse("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=800&q=80"),
                title = "Swiss Alps Green Meadow.jpg",
                sizeBytes = 11900000L, // 11.9 MB
                dateAdded = now - 3600 * 24 * 5,
                isVideo = false,
                isDuplicate = true,
                duplicateGroupId = "group_alps",
                type = MediaFilterType.DUPLICATES
            ),
            MediaItem(
                id = 502L,
                uri = Uri.parse("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=800&q=80"),
                title = "Swiss Alps Green Meadow copy-2.jpg",
                sizeBytes = 11900000L, // 11.9 MB
                dateAdded = now - 3600 * 24 * 5 + 10,
                isVideo = false,
                isDuplicate = true,
                duplicateGroupId = "group_alps",
                type = MediaFilterType.DUPLICATES
            ),

            // Video Heavy 2
            MediaItem(
                id = 202L,
                uri = Uri.parse("https://assets.mixkit.co/videos/preview/mixkit-stars-in-space-background-1611-large.mp4"),
                title = "Deep_Space_Hyperdrive_4K.mp4",
                sizeBytes = 289400000L, // 289.4 MB
                dateAdded = now - 3600 * 24 * 8,
                isVideo = true,
                durationMs = 28000L,
                type = MediaFilterType.HEAVY_VIDEOS
            ),

            // General Photo
            MediaItem(
                id = 601L,
                uri = Uri.parse("https://images.unsplash.com/photo-1472214222541-d510753a4707?auto=format&fit=crop&w=800&q=80"),
                title = "Golden Hour Sunset Valley.jpg",
                sizeBytes = 5600000L, // 5.6 MB
                dateAdded = now - 3600 * 24 * 10,
                isVideo = false,
                type = MediaFilterType.ALL
            ),

            // GIFs
            MediaItem(
                id = 701L,
                uri = Uri.parse("https://media.giphy.com/media/Vekcn97L7VpY7Am327/giphy.gif"),
                title = "Meme_Cat_Wiggle_Dance.gif",
                sizeBytes = 2200000L, // 2.2 MB
                dateAdded = now - 3600 * 1, // 1h ago
                isVideo = false,
                type = MediaFilterType.GIFS
            ),
            MediaItem(
                id = 702L,
                uri = Uri.parse("https://media.giphy.com/media/3o72EX5QZ9N9d51dqo/giphy.gif"),
                title = "Retro_Cyberpunk_Synthwave.gif",
                sizeBytes = 1800000L, // 1.8 MB
                dateAdded = now - 3600 * 6, // 6h ago
                isVideo = false,
                type = MediaFilterType.GIFS
            ),

            // Light Videos
            MediaItem(
                id = 801L,
                uri = Uri.parse("https://assets.mixkit.co/videos/preview/mixkit-funny-cat-reaction-to-toy-41221-large.mp4"),
                title = "Short_Funny_Meme_Reaction.mp4",
                sizeBytes = 1400000L, // 1.4 MB
                dateAdded = now - 3600 * 24, // 1 day ago
                isVideo = true,
                durationMs = 4000L,
                type = MediaFilterType.LIGHT_VIDEOS
            ),
            MediaItem(
                id = 802L,
                uri = Uri.parse("https://assets.mixkit.co/videos/preview/mixkit-coffee-pour-morning-motivation-short-41981-large.mp4"),
                title = "Morning_Vibe_Short_Story.mp4",
                sizeBytes = 3200000L, // 3.2 MB
                dateAdded = now - 3600 * 24 * 4, // 4 days ago
                isVideo = true,
                durationMs = 7000L,
                type = MediaFilterType.LIGHT_VIDEOS
            )
        )
    }
}
