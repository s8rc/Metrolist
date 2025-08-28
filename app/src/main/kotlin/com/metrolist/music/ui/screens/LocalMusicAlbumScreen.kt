package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.AutoResizeText
import com.metrolist.music.ui.component.FontSizeRange
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.ItemWrapper
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.LocalMusicAlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalMusicAlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumInfo by viewModel.albumInfo.collectAsState()
    val albumSongs by viewModel.albumSongs.collectAsState()

    val wrappedSongs = remember(albumSongs) {
        albumSongs.map { song -> ItemWrapper(song.toSong()) }.toMutableStateList()
    }
    
    var selection by remember {
        mutableStateOf(false)
    }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item {
            albumInfo?.let { info ->
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = info.albumArtUri,
                            contentDescription = null,
                            placeholder = painterResource(R.drawable.album),
                            modifier = Modifier
                                .size(AlbumThumbnailSize)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AutoResizeText(
                                text = info.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSizeRange = FontSizeRange(16.sp, 22.sp),
                            )

                            Text(
                                text = info.artist,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            if (info.year > 0) {
                                Text(
                                    text = info.year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }

                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    info.songCount,
                                    info.songCount
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = info.name,
                                        items = albumSongs.map { it.toSong().toMediaItem() }
                                    )
                                )
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.play),
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = info.name,
                                        items = albumSongs.shuffled().map { it.toSong().toMediaItem() }
                                    )
                                )
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.shuffle))
                        }
                    }
                }
            }
        }

        if (wrappedSongs.isNotEmpty()) {
            itemsIndexed(
                items = wrappedSongs,
                key = { _, song -> song.item.id },
            ) { index, songWrapper ->
                SongListItem(
                    song = songWrapper.item,
                    albumIndex = index + 1,
                    isActive = songWrapper.item.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    showInLibraryIcon = true,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = songWrapper.item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    isSelected = songWrapper.isSelected && selection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (!selection) {
                                    if (songWrapper.item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = albumInfo?.name ?: "",
                                                items = albumSongs.map { it.toSong().toMediaItem() },
                                                startIndex = index
                                            )
                                        )
                                    }
                                } else {
                                    songWrapper.isSelected = !songWrapper.isSelected
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!selection) {
                                    selection = true
                                }
                                wrappedSongs.forEach {
                                    it.isSelected = false
                                } // Clear previous selections
                                songWrapper.isSelected = true // Select the current item
                            },
                        ),
                )
            }
        }
    }

    TopAppBar(
        title = {
            if (selection) {
                val count = wrappedSongs.count { it.isSelected }
                Text(
                    text = pluralStringResource(R.plurals.n_song, count, count),
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                Text(
                    text = albumInfo?.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (selection) {
                        selection = false
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!selection) {
                        navController.backToMain()
                    }
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (selection) R.drawable.close else R.drawable.arrow_back
                    ),
                    contentDescription = null
                )
            }
        },
        actions = {
            if (selection) {
                val count = wrappedSongs.count { it.isSelected }
                IconButton(
                    onClick = {
                        if (count == wrappedSongs.size) {
                            wrappedSongs.forEach { it.isSelected = false }
                        } else {
                            wrappedSongs.forEach { it.isSelected = true }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all
                        ),
                        contentDescription = null
                    )
                }

                IconButton(
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = wrappedSongs.filter { it.isSelected }
                                    .map { it.item },
                                onDismiss = menuState::dismiss,
                                clearAction = { selection = false }
                            )
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}