package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.LocalMusicRepository
import com.metrolist.music.db.entities.LocalMusicEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@HiltViewModel
class LocalMusicAlbumViewModel @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    
    private val albumName = URLDecoder.decode(savedStateHandle.get<String>("albumName")!!, StandardCharsets.UTF_8.toString())
    private val artistName = URLDecoder.decode(savedStateHandle.get<String>("artistName")!!, StandardCharsets.UTF_8.toString())
    
    val albumSongs: StateFlow<List<LocalMusicEntity>> = 
        localMusicRepository.getAllLocalMusic()
            .map { allMusic ->
                allMusic.filter { 
                    it.album == albumName && it.artist == artistName 
                }.sortedBy { it.track }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    val albumInfo: StateFlow<LocalMusicAlbumInfo?> = 
        albumSongs.map { songs ->
            if (songs.isNotEmpty()) {
                LocalMusicAlbumInfo(
                    name = albumName,
                    artist = artistName,
                    year = songs.maxOfOrNull { it.year } ?: 0,
                    songCount = songs.size,
                    totalDuration = songs.sumOf { it.duration },
                    albumArtUri = songs.firstOrNull()?.albumArtUri
                )
            } else null
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}

data class LocalMusicAlbumInfo(
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val totalDuration: Long,
    val albumArtUri: String?
)