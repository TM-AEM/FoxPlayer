package com.example.core.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Interface defining the thumbnail extraction and caching engine for local videos.
 */
interface ThumbnailEngine {
    /**
     * Retrieves a thumbnail bitmap for the given video URI.
     * Returns from memory cache if present; otherwise extracts via Android MediaStore/MediaMetadataRetriever.
     */
    suspend fun loadThumbnail(uri: String, width: Int = 320, height: Int = 180): Bitmap?

    /**
     * Synchronously checks if a thumbnail is already available in the memory cache.
     */
    fun getCachedThumbnail(uri: String, width: Int = 320, height: Int = 180): Bitmap?

    /**
     * Evicts all entries from the in-memory thumbnail cache.
     */
    fun clearCache()

    /**
     * Returns the current number of cached thumbnails.
     */
    fun getCacheCount(): Int
}

/**
 * High-performance, battery-efficient ThumbnailEngine implementation.
 *
 * Uses:
 * 1. An in-memory [LruCache] sized to 1/8th of application heap to prevent OutOfMemoryError.
 * 2. Hardware-accelerated [android.content.ContentResolver.loadThumbnail] on Android 10+ (API 29+).
 * 3. [MediaMetadataRetriever] with scaled frames on older Android versions.
 * 4. IO dispatching and request deduplication to prevent duplicate concurrent decodes.
 */
class ThumbnailEngineImpl(
    private val context: Context,
    maxMemoryCacheKb: Int = ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4096)
) : ThumbnailEngine {

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxMemoryCacheKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            val bytes = bitmap.byteCount
            return (bytes / 1024).coerceAtLeast(1)
        }
    }

    private val inFlightMutex = Mutex()
    private val inFlightRequests = mutableMapOf<String, CompletableDeferred<Bitmap?>>()

    override fun getCachedThumbnail(uri: String, width: Int, height: Int): Bitmap? {
        val cacheKey = buildCacheKey(uri, width, height)
        return memoryCache.get(cacheKey)
    }

    override suspend fun loadThumbnail(uri: String, width: Int, height: Int): Bitmap? {
        val cacheKey = buildCacheKey(uri, width, height)

        // 1. Check in-memory cache first (instant)
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Thread-safe in-flight deduplication
        var isLeader = false
        val deferred = inFlightMutex.withLock {
            // Double-check cache after acquiring lock
            memoryCache.get(cacheKey)?.let { return it }

            val existing = inFlightRequests[cacheKey]
            if (existing != null) {
                existing
            } else {
                val newDeferred = CompletableDeferred<Bitmap?>()
                inFlightRequests[cacheKey] = newDeferred
                isLeader = true
                newDeferred
            }
        }

        if (!isLeader) {
            // Follower: await existing extraction
            return try {
                deferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }

        // Leader: perform the extraction on Dispatchers.IO
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                extractThumbnailInternal(uri, width, height)
            }
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            deferred.complete(bitmap)
            bitmap
        } catch (t: Throwable) {
            deferred.complete(null)
            if (t is CancellationException) throw t
            null
        } finally {
            inFlightMutex.withLock {
                inFlightRequests.remove(cacheKey)
            }
        }
    }

    private fun extractThumbnailInternal(uriString: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val parsedUri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return null
        }

        // On Android 10+ (API 29+), use ContentResolver.loadThumbnail which utilizes OS-level caches
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(
                    parsedUri,
                    Size(targetWidth, targetHeight),
                    null
                )
            } catch (_: Exception) {
                // Fall back to MediaMetadataRetriever if ContentResolver.loadThumbnail fails
            }
        }

        // Fallback for Android 9 and below or if loadThumbnail threw an exception
        return extractWithMetadataRetriever(parsedUri, targetWidth, targetHeight)
    }

    private fun extractWithMetadataRetriever(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000L, // 1 second in to skip potential black opening frames
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    override fun clearCache() {
        memoryCache.evictAll()
    }

    override fun getCacheCount(): Int {
        return memoryCache.size()
    }

    private fun buildCacheKey(uri: String, width: Int, height: Int): String {
        return "$uri@${width}x$height"
    }

    companion object {
        @Volatile
        private var INSTANCE: ThumbnailEngine? = null

        fun getInstance(context: Context): ThumbnailEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThumbnailEngineImpl(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
