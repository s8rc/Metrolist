package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import coil3.compose.AsyncImage
import com.metrolist.music.constants.AlbumThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.ItemWrapper
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LocalMusicViewModel
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.launch

enum class LocalMusicSortType {
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    DATE_ADDED,
    TRACK_NUMBER
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalMusicScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val localMusic by viewModel.localMusicPlaylist.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        key = stringPreferencesKey("local_music_sort_type"),
        defaultValue = LocalMusicSortType.TITLE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        key = booleanPreferencesKey("local_music_sort_descending"), 
        defaultValue = false
    )

    val wrappedSongs = remember(localMusic, sortType, sortDescending) {
        val sortedSongs = when (sortType) {
            LocalMusicSortType.TITLE -> localMusic.sortedBy { it.title }
            LocalMusicSortType.ARTIST -> localMusic.sortedBy { it.artist }
            LocalMusicSortType.ALBUM -> localMusic.sortedBy { it.album }
            LocalMusicSortType.DURATION -> localMusic.sortedBy { it.duration }
            LocalMusicSortType.DATE_ADDED -> localMusic.sortedBy { it.dateScanned }
            LocalMusicSortType.TRACK_NUMBER -> localMusic.sortedBy { it.track }
        }.let { if (sortDescending) it.reversed() else it }
        
        sortedSongs.map { song -> ItemWrapper(song) }
    }.toMutableStateList()

    var selection by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "filter",
                contentType = "header"
            ) {
                filterContent()
            }
            
            if (!hasPermission) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.local_music_permission_required)
                    )
                }
            } else if (wrappedSongs.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.no_local_music_found)
                    )
                }
            } else {
                if (wrappedSongs.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                ) {
                                    AsyncImage(
                                        model = wrappedSongs.firstOrNull()?.item?.albumArtUri ?: R.drawable.music_note,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                    )
                                }
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.local_music),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = pluralStringResource(
                                            id = R.plurals.n_song,
                                            count = wrappedSongs.size,
                                            wrappedSongs.size
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.local_music),
                                                items = wrappedSongs.map { it.item.toSong().toMediaItem() },
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
                                    Text(stringResource(R.string.play))
                                }

                                OutlinedButton(
                                    onClick = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.local_music),
                                                items = wrappedSongs.shuffled()
                                                    .map { it.item.toSong().toMediaItem() },
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
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        LocalMusicSortType.TITLE -> R.string.sort_by_name
                                        LocalMusicSortType.ARTIST -> R.string.sort_by_artist
                                        LocalMusicSortType.ALBUM -> R.string.albums
                                        LocalMusicSortType.DURATION -> R.string.sort_by_length
                                        LocalMusicSortType.DATE_ADDED -> R.string.sort_by_create_date
                                        LocalMusicSortType.TRACK_NUMBER -> R.string.sort_by_song_count
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                itemsIndexed(wrappedSongs, key = { _, song -> song.item.id }) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item.toSong(),
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSelected = songWrapper.isSelected && selection,
                        showInLibraryIcon = true,
                        trailingContent = {
                            IconButton(onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = songWrapper.item.toSong(),
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }) {
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
                                    if (!selection) {
                                        if (songWrapper.item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.local_music),
                                                    items = localMusic.map { it.toSong().toMediaItem() },
                                                    startIndex = localMusic.indexOfFirst { it.id == songWrapper.item.id }
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
                                        wrappedSongs.forEach { it.isSelected = false }
                                        songWrapper.isSelected = true
                                    }
                                }
                            )
                            .animateItem()
                    )
                }
            }
        }
    }
}