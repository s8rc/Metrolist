package com.metrolist.music.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresPermission
import com.metrolist.music.models.LocalMusicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class LocalMusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "LocalMusicScanner"
        private const val MIN_DURATION_MS = 30000 // Minimum 30 seconds
        private const val MAX_DURATION_MS = 3600000 // Maximum 1 hour
        
        // Supported audio formats
        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/mpeg",
            "audio/mp4",
            "audio/x-m4a",
            "audio/aac",
            "audio/ogg",
            "audio/flac",
            "audio/wav",
            "audio/x-wav"
        )
        
        private val AUDIO_PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )
    }

    @RequiresPermission(anyOf = ["android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE"])
    suspend fun scanForLocalMusic(): List<LocalMusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<LocalMusicFile>()
        
        try {
            val selection = buildSelection()
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                AUDIO_PROJECTION,
                selection,
                null,
                "${MediaStore.Audio.Media.ARTIST} ASC, ${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
            )
            
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                
                while (c.moveToNext()) {
                    try {
                        val id = c.getLong(idColumn)
                        val title = c.getString(titleColumn) ?: "Unknown Title"
                        val artist = c.getString(artistColumn) ?: "Unknown Artist"
                        val album = c.getString(albumColumn) ?: "Unknown Album"
                        val albumId = c.getLong(albumIdColumn)
                        val duration = c.getLong(durationColumn)
                        val filePath = c.getString(dataColumn)
                        val mimeType = c.getString(mimeTypeColumn)
                        val size = c.getLong(sizeColumn)
                        val dateModified = c.getLong(dateModifiedColumn)
                        val track = c.getInt(trackColumn)
                        val year = c.getInt(yearColumn)
                        
                        // Validate the file
                        if (isValidAudioFile(filePath, mimeType, duration, size)) {
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            
                            val albumArtUri = getAlbumArtUri(albumId)
                            
                            val musicFile = LocalMusicFile(
                                id = id,
                                title = title.trim(),
                                artist = artist.trim(),
                                album = album.trim(),
                                albumId = albumId,
                                duration = duration,
                                filePath = filePath,
                                uri = uri,
                                mimeType = mimeType,
                                size = size,
                                dateModified = dateModified,
                                track = track,
                                year = year,
                                albumArtUri = albumArtUri
                            )
                            
                            musicFiles.add(musicFile)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing audio file at cursor position ${c.position}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for local music", e)
        }
        
        Log.i(TAG, "Found ${musicFiles.size} local music files")
        musicFiles
    }
    
    private fun buildSelection(): String {
        val mimeTypeSelection = SUPPORTED_MIME_TYPES.joinToString(" OR ") { 
            "${MediaStore.Audio.Media.MIME_TYPE} = '$it'"
        }
        
        return "($mimeTypeSelection) AND ${MediaStore.Audio.Media.DURATION} >= $MIN_DURATION_MS AND ${MediaStore.Audio.Media.DURATION} <= $MAX_DURATION_MS"
    }
    
    private fun isValidAudioFile(
        filePath: String?,
        mimeType: String?,
        duration: Long,
        size: Long
    ): Boolean {
        // Check if file path is not null and file exists
        if (filePath.isNullOrEmpty()) return false
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return false
        
        // Check mime type
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType)) return false
        
        // Check duration (between 30 seconds and 1 hour)
        if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS) return false
        
        // Check file size (minimum 1MB, maximum 500MB)
        if (size < 1024 * 1024 || size > 500 * 1024 * 1024) return false
        
        return true
    }
    
    private fun getAlbumArtUri(albumId: Long): Uri? {
        return try {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Scans for music files in specific directories (similar to Auxio's folder-aware scanning)
     */
    suspend fun scanSpecificDirectories(directories: List<String>): List<LocalMusicFile> = withContext(Dispatchers.IO) {
        val allFiles = scanForLocalMusic()
        
        if (directories.isEmpty()) return@withContext allFiles
        
        // Filter files that are in the specified directories
        allFiles.filter { musicFile ->
            directories.any { directory ->
                musicFile.filePath.startsWith(directory)
            }
        }
    }
    
    /**
     * Creates a hardcoded "Local Music" playlist with all scanned files
     */
    suspend fun createLocalMusicPlaylist(): List<LocalMusicFile> = withContext(Dispatchers.IO) {
        val allFiles = scanForLocalMusic()
        
        // Sort by artist, then album, then track number for a good playlist experience
        allFiles.sortedWith(compareBy<LocalMusicFile> { it.artist.lowercase() }
            .thenBy { it.album.lowercase() }
            .thenBy { it.track }
            .thenBy { it.title.lowercase() })
    }
}