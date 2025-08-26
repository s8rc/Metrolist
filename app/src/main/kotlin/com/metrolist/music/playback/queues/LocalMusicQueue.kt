package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata

class LocalMusicQueue(
    val title: String = "Local Music",
    val items: List<MediaMetadata>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(
        title = title,
        items = items.map { it.toMediaItem() },
        mediaItemIndex = startIndex,
        position = position
    )

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
    
    companion object {
        /**
         * Create a LocalMusicQueue from a list of local music files
         */
        fun fromLocalMusic(
            title: String = "Local Music",
            localMusic: List<MediaMetadata>,
            startIndex: Int = 0,
            position: Long = 0L
        ) = LocalMusicQueue(
            title = title,
            items = localMusic,
            startIndex = startIndex,
            position = position
        )
    }
}