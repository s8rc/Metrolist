package com.metrolist.music.models

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class LocalMusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long, // in milliseconds
    val filePath: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long, // in bytes
    val dateModified: Long, // timestamp
    val track: Int,
    val year: Int,
    val albumArtUri: Uri? = null,
    val isLocal: Boolean = true
) : Serializable {
    
    /**
     * Convert LocalMusicFile to MediaMetadata for playback
     */
    fun toMediaMetadata(): MediaMetadata {
        // Generate a unique ID for local files by prefixing with "local_"
        val localId = "local_$id"
        
        return MediaMetadata(
            id = localId,
            title = title,
            artists = listOf(
                MediaMetadata.Artist(
                    id = null, // Local artists don't have IDs
                    name = artist
                )
            ),
            duration = (duration / 1000).toInt(), // Convert to seconds
            thumbnailUrl = albumArtUri?.toString(),
            album = MediaMetadata.Album(
                id = "local_album_$albumId",
                title = album
            ),
            isLocal = true,
            localPath = if (filePath.startsWith("file://")) filePath else "file://$filePath"
        )
    }
    
    /**
     * Get a display name for the file
     */
    fun getDisplayName(): String {
        return if (title.isNotBlank() && title != "Unknown Title") {
            title
        } else {
            // Extract filename without extension as fallback
            filePath.substringAfterLast("/").substringBeforeLast(".")
        }
    }
    
    /**
     * Get formatted duration string
     */
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    
    /**
     * Get formatted file size string
     */
    fun getFormattedSize(): String {
        return when {
            size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> "$size B"
        }
    }
    
    /**
     * Check if this is a valid audio file
     */
    fun isValid(): Boolean {
        return title.isNotBlank() && 
               artist.isNotBlank() && 
               duration > 0 && 
               filePath.isNotBlank()
    }
}