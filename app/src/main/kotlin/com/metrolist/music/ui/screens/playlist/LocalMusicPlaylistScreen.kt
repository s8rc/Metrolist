package com.metrolist.music.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.LocalMusicQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem

import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.viewmodels.LocalMusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalMusicPlaylistScreen(
    navController: NavController,
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val localMusic by viewModel.localMusicPlaylist.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    var isScanning by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.local_music),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = { /* No action for long click */ }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                Material3IconButton(
                    onClick = {
                        isScanning = true
                        coroutineScope.launch {
                            viewModel.scanLocalMusic()
                            isScanning = false
                        }
                    },
                    enabled = hasPermission && !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = stringResource(R.string.scan_local_music)
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior
        )

        when {
            !hasPermission -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.local_music_permission_required),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = { viewModel.requestPermissions() }
                        ) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }
            
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            localMusic.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_music_found),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = {
                                isScanning = true
                                coroutineScope.launch {
                                    viewModel.scanLocalMusic()
                                    isScanning = false
                                }
                            },
                            enabled = !isScanning
                        ) {
                            Text(stringResource(R.string.scan_local_music))
                        }
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    playerConnection.playQueue(
                                        LocalMusicQueue(
                                            title = "Local Music",
                                            items = localMusic.map { it.toMediaMetadata() }
                                        )
                                    )
                                },
                                onLongClick = { /* No action for long click */ }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null
                                )
                            }

                            IconButton(
                                onClick = {
                                    playerConnection.playQueue(
                                        LocalMusicQueue(
                                            title = "Local Music",
                                            items = localMusic.map { it.toMediaMetadata() }.shuffled()
                                        )
                                    )
                                },
                                onLongClick = { /* No action for long click */ }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = "${localMusic.size} ${stringResource(R.string.songs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        items = localMusic,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG }
                    ) { index, localMusicEntity ->
                        val metadata = localMusicEntity.toMediaMetadata()
                        val song = localMusicEntity.toSong()
                        
                        SongListItem(
                            song = song,
                            isActive = metadata.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = false,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    },
                                    onLongClick = { /* No action for long click */ }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (metadata.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            val mediaMetadataList = localMusic.map { it.toMediaMetadata() }
                                            playerConnection.playQueue(
                                                LocalMusicQueue(
                                                    title = "Local Music",
                                                    items = mediaMetadataList,
                                                    startIndex = index
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}