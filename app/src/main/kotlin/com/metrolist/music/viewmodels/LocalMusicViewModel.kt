package com.metrolist.music.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.LocalMusicRepository
import com.metrolist.music.db.entities.LocalMusicEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "LocalMusicViewModel"
        
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermissions())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _scanResult = MutableStateFlow<Result<Int>?>(null)
    val scanResult: StateFlow<Result<Int>?> = _scanResult.asStateFlow()

    // Local music playlist from repository
    val localMusicPlaylist: StateFlow<List<LocalMusicEntity>> = 
        localMusicRepository.getLocalMusicPlaylist()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // All local music
    val allLocalMusic: StateFlow<List<LocalMusicEntity>> = 
        localMusicRepository.getAllLocalMusic()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Local artists
    val localArtists: StateFlow<List<String>> = 
        localMusicRepository.getAllLocalArtists()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Local albums
    val localAlbums: StateFlow<List<String>> = 
        localMusicRepository.getAllLocalAlbums()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Liked local music
    val likedLocalMusic: StateFlow<List<LocalMusicEntity>> = 
        localMusicRepository.getLikedLocalMusic()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        // Check if we have local music and scan if we have permissions but no music
        viewModelScope.launch {
            if (hasPermission.value) {
                val hasMusic = localMusicRepository.hasLocalMusic()
                if (!hasMusic) {
                    Log.i(TAG, "No local music found, starting initial scan...")
                    scanLocalMusic()
                }
            }
        }
    }

    /**
     * Check if we have the required permissions
     */
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Update permission status (called from UI after permission request)
     */
    fun updatePermissionStatus() {
        _hasPermission.value = checkPermissions()
        
        // If we got permissions, scan for music
        if (_hasPermission.value) {
            viewModelScope.launch {
                scanLocalMusic()
            }
        }
    }

    /**
     * Request permissions (UI should handle the actual permission request)
     */
    fun requestPermissions() {
        // This is a signal to the UI to request permissions
        // The actual permission request should be handled by the UI layer
        Log.i(TAG, "Permission request triggered")
    }

    /**
     * Scan for local music files
     */
    fun scanLocalMusic() {
        if (!checkPermissions()) {
            Log.w(TAG, "Cannot scan local music: missing permissions")
            _scanResult.value = Result.failure(SecurityException("Missing storage permissions"))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.i(TAG, "Starting local music scan...")
                val result = localMusicRepository.scanAndUpdateLocalMusic()
                _scanResult.value = result
                
                result.onSuccess { count ->
                    Log.i(TAG, "Local music scan completed: $count files found")
                }.onFailure { throwable ->
                    Log.e(TAG, "Local music scan failed", throwable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during local music scan", e)
                _scanResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Toggle like status for a local music file
     */
    fun toggleLike(localMusicId: String) {
        viewModelScope.launch {
            localMusicRepository.toggleLike(localMusicId)
                .onFailure { throwable ->
                    Log.e(TAG, "Error toggling like for $localMusicId", throwable)
                }
        }
    }

    /**
     * Increment play count for a local music file
     */
    fun incrementPlayCount(localMusicId: String) {
        viewModelScope.launch {
            localMusicRepository.incrementPlayCount(localMusicId)
                .onFailure { throwable ->
                    Log.e(TAG, "Error incrementing play count for $localMusicId", throwable)
                }
        }
    }

    /**
     * Get local music by artist
     */
    fun getLocalMusicByArtist(artist: String): StateFlow<List<LocalMusicEntity>> {
        return localMusicRepository.getLocalMusicByArtist(artist)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Get local music by album
     */
    fun getLocalMusicByAlbum(album: String): StateFlow<List<LocalMusicEntity>> {
        return localMusicRepository.getLocalMusicByAlbum(album)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Clean up unavailable files
     */
    fun cleanupUnavailableFiles() {
        viewModelScope.launch {
            localMusicRepository.cleanupUnavailableFiles()
                .onFailure { throwable ->
                    Log.e(TAG, "Error cleaning up unavailable files", throwable)
                }
        }
    }

    /**
     * Clear scan result
     */
    fun clearScanResult() {
        _scanResult.value = null
    }
}