package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "local_music",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["filePath"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["dateScanned"])
    ]
)
data class LocalMusicEntity(
    @PrimaryKey val id: String, // "local_" + mediaStoreId
    val mediaStoreId: Long, // MediaStore ID
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long, // MediaStore Album ID
    val duration: Long, // in milliseconds
    val filePath: String,
    val mimeType: String,
    val size: Long, // file size in bytes
    val dateModified: Long, // file modification timestamp
    val track: Int,
    val year: Int,
    val albumArtUri: String? = null,
    val dateScanned: LocalDateTime = LocalDateTime.now(), // when this file was last scanned
    val isAvailable: Boolean = true, // if the file still exists and is readable
    val playCount: Int = 0,
    val lastPlayed: LocalDateTime? = null,
    @ColumnInfo(defaultValue = "0")
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null
) {
    
    /**
     * Convert to SongEntity for compatibility with existing playback system
     */
    fun toSongEntity(): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            duration = (duration / 1000).toInt(), // Convert to seconds
            thumbnailUrl = albumArtUri,
            albumId = "local_album_$albumId",
            albumName = album,
            year = if (year > 0) year else null,
            dateModified = LocalDateTime.now(),
            liked = liked,
            likedDate = likedDate,
            isLocal = true
        )
    }
    
    /**
     * Toggle like status for local music
     */
    fun toggleLike(): LocalMusicEntity {
        return copy(
            liked = !liked,
            likedDate = if (!liked) LocalDateTime.now() else null
        )
    }
    
    /**
     * Increment play count
     */
    fun incrementPlayCount(): LocalMusicEntity {
        return copy(
            playCount = playCount + 1,
            lastPlayed = LocalDateTime.now()
        )
    }
    
    /**
     * Mark as unavailable (file no longer exists)
     */
    fun markUnavailable(): LocalMusicEntity {
        return copy(isAvailable = false)
    }
    
    /**
     * Mark as available (file exists and is readable)
     */
    fun markAvailable(): LocalMusicEntity {
        return copy(isAvailable = true)
    }
    
    /**
     * Convert to Song for compatibility with UI components
     */
    fun toSong(): Song {
        // Create minimal ArtistEntity for local music
        val artistEntity = ArtistEntity(
            id = "local_artist_${artist.hashCode()}",
            name = artist
        )
        
        // Create minimal AlbumEntity for local music if album exists
        val albumEntity = if (album.isNotBlank()) {
            AlbumEntity(
                id = "local_album_$albumId",
                title = album,
                songCount = 1, // We don't track album song counts for local music
                duration = (duration / 1000).toInt() // Convert milliseconds to seconds
            )
        } else null
        
        return Song(
            song = toSongEntity(),
            artists = listOf(artistEntity),
            album = albumEntity
        )
    }
}