/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 *
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * Provides the app-wide Coil [ImageLoader] with [VideoFrameDecoder] registered so that
 * AsyncImage can render thumbnails for `content://` videos (trash sheet, background deck cards).
 * Without this, video thumbnails render blank.
 */
class MakCleanApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
}
