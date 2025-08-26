package com.metrolist.music.db

import android.content.Context
import android.util.Log
import com.metrolist.music.db.entities.LocalMusicEntity
import com.metrolist.music.db.entities.LocalMusicPlaylistEntity
import com.metrolist.music.models.LocalMusicFile
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.LocalMusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepository @Inject constructor(
    private val database: MusicDatabase,
    private val context: Context
) {
    companion object {
        private const val TAG = "LocalMusicRepository"
    }

    private val scanner = LocalMusicScanner(context)

    /**
     * Scan device for local music and update database
     */
    suspend fun scanAndUpdateLocalMusic(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting local music scan...")
            val scannedFiles = scanner.scanForLocalMusic()
            Log.i(TAG, "Scanned ${scannedFiles.size} local music files")

            // Convert to entities
            val entities = scannedFiles.map { file ->
                LocalMusicEntity(
                    id = "local_${file.id}",
                    mediaStoreId = file.id,
                    title = file.title,
                    artist = file.artist,
                    album = file.album,
                    albumId = file.albumId,
                    duration = file.duration,
                    filePath = file.filePath,
                    mimeType = file.mimeType,
                    size = file.size,
                    dateModified = file.dateModified,
                    track = file.track,
                    year = file.year,
                    albumArtUri = file.albumArtUri?.toString()
                )
            }

            // Validate existing files and mark unavailable ones
            validateExistingFiles()

            // Insert new/updated files
            database.insertAll(entities)

            // Update hardcoded playlist
            updateHardcodedPlaylist()

            Log.i(TAG, "Local music scan completed. ${entities.size} files processed.")
            Result.success(entities.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error during local music scan", e)
            Result.failure(e)
        }
    }

    /**
     * Get all local music as Flow
     */
    fun getAllLocalMusic(): Flow<List<LocalMusicEntity>> = database.getAllLocalMusic()

    /**
     * Get all local music as MediaMetadata for playback
     */
    fun getAllLocalMusicAsMediaMetadata(): Flow<List<MediaMetadata>> = 
        database.getAllLocalMusic().map { entities ->
            entities.map { it.toMediaMetadata() }
        }

    /**
     * Get hardcoded local music playlist
     */
    fun getLocalMusicPlaylist(): Flow<List<LocalMusicEntity>> = database.getLocalMusicPlaylist()

    /**
     * Get hardcoded local music playlist as MediaMetadata
     */
    fun getLocalMusicPlaylistAsMediaMetadata(): Flow<List<MediaMetadata>> = 
        database.getLocalMusicPlaylist().map { entities ->
            entities.map { it.toMediaMetadata() }
        }

    /**
     * Get local music by artist
     */
    fun getLocalMusicByArtist(artist: String): Flow<List<LocalMusicEntity>> = 
        database.getLocalMusicByArtist(artist)

    /**
     * Get local music by album
     */
    fun getLocalMusicByAlbum(album: String): Flow<List<LocalMusicEntity>> = 
        database.getLocalMusicByAlbum(album)

    /**
     * Get all local artists
     */
    fun getAllLocalArtists(): Flow<List<String>> = database.getAllLocalArtists()

    /**
     * Get all local albums
     */
    fun getAllLocalAlbums(): Flow<List<String>> = database.getAllLocalAlbums()

    /**
     * Get liked local music
     */
    fun getLikedLocalMusic(): Flow<List<LocalMusicEntity>> = database.getLikedLocalMusic()

    /**
     * Get local music by ID
     */
    suspend fun getLocalMusicById(id: String): LocalMusicEntity? = database.getLocalMusicById(id)

    /**
     * Toggle like status for local music
     */
    suspend fun toggleLike(localMusicId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = database.getLocalMusicById(localMusicId)
            if (entity != null) {
                val updated = entity.toggleLike()
                database.update(updated)
                Result.success(updated.liked)
            } else {
                Result.failure(IllegalArgumentException("Local music not found: $localMusicId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling like for local music: $localMusicId", e)
            Result.failure(e)
        }
    }

    /**
     * Increment play count for local music
     */
    suspend fun incrementPlayCount(localMusicId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = database.getLocalMusicById(localMusicId)
            if (entity != null) {
                val updated = entity.incrementPlayCount()
                database.update(updated)
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Local music not found: $localMusicId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing play count for local music: $localMusicId", e)
            Result.failure(e)
        }
    }

    /**
     * Get playlist size
     */
    suspend fun getPlaylistSize(): Int = database.getLocalMusicPlaylistSize()

    /**
     * Validate existing files and mark unavailable ones
     */
    private suspend fun validateExistingFiles() {
        try {
            val allLocal = database.getAllLocalMusic().map { entities ->
                entities.forEach { entity ->
                    val file = File(entity.filePath)
                    if (!file.exists() || !file.canRead()) {
                        database.markLocalMusicUnavailable(entity.filePath)
                        Log.d(TAG, "Marked unavailable: ${entity.filePath}")
                    } else if (!entity.isAvailable) {
                        database.markLocalMusicAvailable(entity.filePath)
                        Log.d(TAG, "Marked available: ${entity.filePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating existing files", e)
        }
    }

    /**
     * Update the hardcoded playlist with all available local music
     */
    private suspend fun updateHardcodedPlaylist() {
        try {
            // Clear existing playlist
            database.clearLocalMusicPlaylist()

            // Get all available local music sorted for playlist
            val sortedFiles = scanner.createLocalMusicPlaylist()
            
            // Convert to playlist entries
            val playlistEntries = sortedFiles.mapIndexed { index, file ->
                LocalMusicPlaylistEntity(
                    localMusicId = "local_${file.id}",
                    position = index
                )
            }

            // Insert playlist entries
            database.insertAllPlaylistEntries(playlistEntries)
            
            Log.i(TAG, "Updated hardcoded playlist with ${playlistEntries.size} tracks")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating hardcoded playlist", e)
        }
    }

    /**
     * Clean up unavailable files from database
     */
    suspend fun cleanupUnavailableFiles(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.deleteUnavailableLocalMusic()
            Log.i(TAG, "Cleaned up unavailable local music files")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up unavailable files", e)
            Result.failure(e)
        }
    }

    /**
     * Check if local music feature is available (has scanned files)
     */
    suspend fun hasLocalMusic(): Boolean {
        return try {
            getPlaylistSize() > 0
        } catch (e: Exception) {
            false
        }
    }
}