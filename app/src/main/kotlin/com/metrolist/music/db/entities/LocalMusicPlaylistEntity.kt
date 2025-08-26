package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "local_music_playlist",
    foreignKeys = [
        ForeignKey(
            entity = LocalMusicEntity::class,
            parentColumns = ["id"],
            childColumns = ["localMusicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["localMusicId"]),
        Index(value = ["position"]),
        Index(value = ["dateAdded"])
    ]
)
data class LocalMusicPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localMusicId: String, // References LocalMusicEntity.id
    val position: Int, // Position in the hardcoded playlist
    val dateAdded: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val HARDCODED_PLAYLIST_NAME = "Local Music"
        const val HARDCODED_PLAYLIST_ID = "local_playlist_hardcoded"
    }
}