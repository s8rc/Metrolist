package com.metrolist.music.viewmodels

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongAlbumMap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        _hasPermission.value = hasPermission
        
        if (hasPermission) {
            loadLocalSongs()
        }
    }

    private fun loadLocalSongs() {
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) {
                database.localSongs()
            }
            _localSongs.value = songs
        }
    }

    fun scanLocalMusic() {
        if (!_hasPermission.value) return
        
        viewModelScope.launch {
            _isScanning.value = true
            
            try {
                val scannedSongs = withContext(Dispatchers.IO) {
                    scanMusicFiles()
                }
                
                withContext(Dispatchers.IO) {
                    saveScannedSongs(scannedSongs)
                }
                
                loadLocalSongs()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun scanMusicFiles(): List<LocalSongInfo> {
        val songs = mutableListOf<LocalSongInfo>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn) / 1000 // Convert to seconds
                val filePath = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                
                songs.add(
                    LocalSongInfo(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration.toInt(),
                        filePath = filePath,
                        contentUri = contentUri.toString(),
                        albumArtUri = albumArtUri.toString()
                    )
                )
            }
        }
        
        return songs
    }

    private suspend fun saveScannedSongs(scannedSongs: List<LocalSongInfo>) {
        database.transaction {
            // Clear existing local songs
            clearLocalSongs()
            
            scannedSongs.forEach { songInfo ->
                // Insert artist
                val artistEntity = ArtistEntity(
                    id = "local_artist_${songInfo.artist.hashCode()}",
                    name = songInfo.artist,
                    thumbnailUrl = null,
                    bookmarkedAt = null,
                    lastUpdateTime = null
                )
                upsert(artistEntity)
                
                // Insert album
                val albumEntity = AlbumEntity(
                    id = "local_album_${songInfo.album.hashCode()}",
                    title = songInfo.album,
                    year = null,
                    thumbnailUrl = songInfo.albumArtUri,
                    songCount = 0,
                    duration = 0,
                    bookmarkedAt = null,
                    lastUpdateTime = null
                )
                upsert(albumEntity)
                
                // Insert song
                val songEntity = SongEntity(
                    id = songInfo.id,
                    title = songInfo.title,
                    duration = songInfo.duration,
                    thumbnailUrl = songInfo.albumArtUri,
                    albumId = albumEntity.id,
                    albumName = songInfo.album,
                    liked = false,
                    totalPlayTime = 0,
                    isLocal = true,
                    localPath = songInfo.contentUri
                )
                upsert(songEntity)
                
                // Insert song-artist mapping
                val songArtistMap = SongArtistMap(
                    songId = songInfo.id,
                    artistId = artistEntity.id,
                    position = 0
                )
                insert(songArtistMap)
                
                // Insert song-album mapping
                val songAlbumMap = SongAlbumMap(
                    songId = songInfo.id,
                    albumId = albumEntity.id,
                    position = 0
                )
                insert(songAlbumMap)
            }
        }
    }

    private data class LocalSongInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Int,
        val filePath: String,
        val contentUri: String,
        val albumArtUri: String
    )
}