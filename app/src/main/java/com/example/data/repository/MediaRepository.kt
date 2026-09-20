package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.core.model.VideoFolder
import com.example.core.model.VideoItem
import com.example.core.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository interface for discovering and retrieving local video media from Android MediaStore.
 */
interface MediaRepository {
    /**
     * Emits the current list of local videos as a reactive Flow.
     * Automatically updates whenever the underlying MediaStore content changes.
     */
    fun getVideosFlow(): Flow<List<VideoItem>>

    /**
     * Direct one-shot query to fetch all local video items.
     */
    suspend fun queryVideos(): List<VideoItem>

    /**
     * Retrieves metadata for a specific video by its content URI.
     */
    suspend fun getVideoByUri(contentUriString: String): VideoItem?

    /**
     * Groups discovered local videos into folders/albums.
     */
    suspend fun getVideoFolders(): List<VideoFolder>
}

/**
 * Implementation of [MediaRepository] accessing Android's MediaStore.
 */
class MediaRepositoryImpl(
    private val context: Context,
    private val contentResolver: ContentResolver = context.contentResolver
) : MediaRepository {

    override fun getVideosFlow(): Flow<List<VideoItem>> = callbackFlow {
        // Query and emit initial data
        trySend(queryVideos())

        // Register ContentObserver to listen for MediaStore additions, edits, and deletions
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                trySend(queryVideosDirect())
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun queryVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        queryVideosDirect()
    }

    private fun queryVideosDirect(): List<VideoItem> {
        if (!PermissionUtils.hasVideoPermission(context)) {
            return emptyList()
        }

        val videoList = mutableListOf<VideoItem>()

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Video.Media.DATA)
            }
        }.toTypedArray()

        // Filter: Must have valid size and be a video mime-type
        val selection = "${MediaStore.Video.Media.SIZE} > 0 AND ${MediaStore.Video.Media.MIME_TYPE} LIKE 'video/%'"
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val displayNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                val dataColumn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    if (id <= 0) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val displayName = if (displayNameColumn != -1) {
                        cursor.getString(displayNameColumn) ?: "Video_$id"
                    } else "Video_$id"

                    val title = if (titleColumn != -1) {
                        cursor.getString(titleColumn) ?: displayName
                    } else displayName

                    val durationMs = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val sizeBytes = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                    val mimeType = if (mimeTypeColumn != -1) {
                        cursor.getString(mimeTypeColumn) ?: "video/*"
                    } else "video/*"

                    val relativePath = when {
                        relativePathColumn != -1 -> cursor.getString(relativePathColumn) ?: ""
                        dataColumn != -1 -> {
                            val dataPath = cursor.getString(dataColumn) ?: ""
                            dataPath.substringBeforeLast('/', "")
                        }
                        else -> ""
                    }

                    val bucketFromColumn = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else null
                    val bucketName = when {
                        !bucketFromColumn.isNullOrBlank() -> bucketFromColumn
                        relativePath.isNotBlank() -> {
                            relativePath.trim('/').split('/').lastOrNull()?.takeIf { it.isNotBlank() } ?: "Videos"
                        }
                        else -> "Videos"
                    }

                    // Exclude invalid non-video entries or corrupted zero-byte files
                    if (sizeBytes > 0) {
                        videoList.add(
                            VideoItem(
                                id = id,
                                uri = contentUri,
                                title = title,
                                displayName = displayName,
                                durationMs = durationMs,
                                sizeBytes = sizeBytes,
                                width = width,
                                height = height,
                                dateModified = dateModified,
                                relativePath = relativePath,
                                bucketName = bucketName,
                                mimeType = mimeType
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Log or handle gracefully without throwing uncaught exceptions to caller
            return emptyList()
        }

        return videoList
    }

    override suspend fun getVideoByUri(contentUriString: String): VideoItem? = withContext(Dispatchers.IO) {
        val targetUri = try {
            Uri.parse(contentUriString)
        } catch (e: Exception) {
            return@withContext null
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        try {
            contentResolver.query(
                targetUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val displayNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val titleColumn = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    } else -1

                    val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                    val displayName = if (displayNameColumn != -1) cursor.getString(displayNameColumn) ?: "" else ""
                    val title = if (titleColumn != -1) cursor.getString(titleColumn) ?: displayName else displayName
                    val durationMs = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val sizeBytes = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                    val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                    val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) ?: "video/*" else "video/*"
                    val relativePath = if (relativePathColumn != -1) cursor.getString(relativePathColumn) ?: "" else ""
                    val bucketFromColumn = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else null
                    val bucketName = when {
                        !bucketFromColumn.isNullOrBlank() -> bucketFromColumn
                        relativePath.isNotBlank() -> {
                            relativePath.trim('/').split('/').lastOrNull()?.takeIf { it.isNotBlank() } ?: "Videos"
                        }
                        else -> "Videos"
                    }

                    return@withContext VideoItem(
                        id = id,
                        uri = contentUriString,
                        title = title,
                        displayName = displayName,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        width = width,
                        height = height,
                        dateModified = dateModified,
                        relativePath = relativePath,
                        bucketName = bucketName,
                        mimeType = mimeType
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext null
        }

        null
    }

    override suspend fun getVideoFolders(): List<VideoFolder> = withContext(Dispatchers.IO) {
        val videos = queryVideosDirect()
        val folderMap = mutableMapOf<String, MutableList<VideoItem>>()

        for (video in videos) {
            val key = video.bucketName.ifBlank { "Videos" }
            folderMap.getOrPut(key) { mutableListOf() }.add(video)
        }

        folderMap.map { (bucketName, videoList) ->
            VideoFolder(
                bucketId = bucketName,
                name = bucketName,
                path = videoList.firstOrNull()?.relativePath ?: "",
                videoCount = videoList.size
            )
        }.sortedBy { it.name.lowercase() }
    }
}
