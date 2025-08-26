package com.metrolist.music.ui.screens.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.db.entities.LocalMusicEntity
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.LocalMusicQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.menu.SelectionSongMenu
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

enum class LocalMusicFilter {
    ALL,
    RECENT,
    FAVORITES
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalMusicScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val localMusic by viewModel.localMusicPlaylist.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    // Enhanced state management
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var selection by remember { mutableStateOf(false) }
    
    // Sorting and filtering preferences
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        key = stringPreferencesKey("local_music_sort_type"),
        defaultValue = LocalMusicSortType.TITLE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        key = booleanPreferencesKey("local_music_sort_descending"), 
        defaultValue = false
    )
    val (filter, onFilterChange) = rememberEnumPreference(
        key = stringPreferencesKey("local_music_filter"),
        defaultValue = LocalMusicFilter.ALL
    )

    // Focus management for search
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    // Filtered and sorted songs
    val filteredSongs = remember(localMusic, query, filter) {
        var songs = when (filter) {
            LocalMusicFilter.ALL -> localMusic
            LocalMusicFilter.FAVORITES -> localMusic.filter { it.liked }
            LocalMusicFilter.RECENT -> localMusic.sortedByDescending { it.dateScanned }.take(50)
        }
        
        if (query.text.isNotEmpty()) {
            songs = songs.filter { song ->
                song.title.contains(query.text, ignoreCase = true) ||
                song.artist.contains(query.text, ignoreCase = true) ||
                song.album.contains(query.text, ignoreCase = true)
            }
        }
        
        songs
    }

    val sortedSongs = remember(filteredSongs, sortType, sortDescending) {
        val sorted = when (sortType) {
            LocalMusicSortType.TITLE -> filteredSongs.sortedBy { it.title }
            LocalMusicSortType.ARTIST -> filteredSongs.sortedBy { it.artist }
            LocalMusicSortType.ALBUM -> filteredSongs.sortedBy { it.album }
            LocalMusicSortType.DURATION -> filteredSongs.sortedBy { it.duration }
            LocalMusicSortType.DATE_ADDED -> filteredSongs.sortedBy { it.dateScanned }
            LocalMusicSortType.TRACK_NUMBER -> filteredSongs.sortedBy { it.track }
        }
        if (sortDescending) sorted.reversed() else sorted
    }

    val wrappedSongs = remember(sortedSongs) {
        sortedSongs.map { ItemWrapper(it) }
    }.toMutableList()

    // Back handler for search
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
            wrappedSongs.forEach { it.isSelected = false }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        filterContent()
                    }

                    // Filter chips for local music
                    item(
                        key = "localFilter", 
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            FilterChip(
                                selected = filter == LocalMusicFilter.ALL,
                                onClick = { onFilterChange(LocalMusicFilter.ALL) },
                                label = { Text("All") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = filter == LocalMusicFilter.FAVORITES,
                                onClick = { onFilterChange(LocalMusicFilter.FAVORITES) },
                                label = { Text("Favorites") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = filter == LocalMusicFilter.RECENT,
                                onClick = { onFilterChange(LocalMusicFilter.RECENT) },
                                label = { Text("Recent") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Search field (when searching)
                    if (isSearching) {
                        item(
                            key = "search",
                            contentType = CONTENT_TYPE_HEADER
                        ) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Search local music...") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (query.text.isNotEmpty()) {
                                        IconButton(
                                            onClick = { query = TextFieldValue() }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = null
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .focusRequester(focusRequester)
                            )
                        }
                    }

                    // Header with controls and sort
                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        if (selection) {
                            // Selection mode header
                            val count = wrappedSongs.count { it.isSelected }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                IconButton(
                                    onClick = { 
                                        selection = false
                                        wrappedSongs.forEach { it.isSelected = false }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null
                                    )
                                }
                                Text(
                                    text = pluralStringResource(R.plurals.n_song, count, count),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (count == wrappedSongs.size) {
                                            wrappedSongs.forEach { it.isSelected = false }
                                        } else {
                                            wrappedSongs.forEach { it.isSelected = true }
                                        }
                                    }
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
                                                    .map { it.item.toSong() },
                                                onDismiss = menuState::dismiss,
                                                clearAction = { selection = false }
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null
                                    )
                                }
                            }
                        } else {
                            // Normal header with play controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Play all button
                                IconButton(
                                    onClick = {
                                        android.util.Log.d("LocalMusicScreen", "Playing ${sortedSongs.size} local music files")
                                        sortedSongs.forEach { entity ->
                                            android.util.Log.d("LocalMusicScreen", "Local music: id=${entity.id}, title=${entity.title}, filePath=${entity.filePath}")
                                        }
                                        val mediaMetadataList = sortedSongs.map { it.toMediaMetadata() }
                                        mediaMetadataList.forEach { metadata ->
                                            android.util.Log.d("LocalMusicScreen", "MediaMetadata: id=${metadata.id}, title=${metadata.title}, isLocal=${metadata.isLocal}, localPath=${metadata.localPath}")
                                        }
                                        playerConnection.playQueue(
                                            LocalMusicQueue(
                                                title = "Local Music",
                                                items = mediaMetadataList
                                            )
                                        )
                                    },
                                    enabled = sortedSongs.isNotEmpty()
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null
                                    )
                                }

                                // Shuffle play button
                                IconButton(
                                    onClick = {
                                        android.util.Log.d("LocalMusicScreen", "Shuffling ${sortedSongs.size} local music files")
                                        val mediaMetadataList = sortedSongs.map { it.toMediaMetadata() }.shuffled()
                                        playerConnection.playQueue(
                                            LocalMusicQueue(
                                                title = "Local Music",
                                                items = mediaMetadataList
                                            )
                                        )
                                    },
                                    enabled = sortedSongs.isNotEmpty()
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    text = "${sortedSongs.size} ${stringResource(R.string.songs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                // Search button
                                if (!isSearching) {
                                    IconButton(
                                        onClick = { isSearching = true }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.search),
                                            contentDescription = null
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            isSearching = false
                                            query = TextFieldValue()
                                            keyboardController?.hide()
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = null
                                        )
                                    }
                                }

                                // Scan button
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
                            }
                        }
                    }

                    // Sort header
                    if (!isSearching && !selection) {
                        item(
                            key = "sort",
                            contentType = CONTENT_TYPE_HEADER
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
                                }
                            )
                        }
                    }

                    // Song list
                    itemsIndexed(
                        items = wrappedSongs,
                        key = { _, song -> song.item.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG }
                    ) { index, song ->
                        SongListItem(
                            song = song.item.toSong(),
                            isActive = song.item.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            isSelected = song.isSelected && selection,
                            trailingContent = {
                                if (!selection) {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.item.toSong(),
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selection) {
                                            song.isSelected = !song.isSelected
                                        } else {
                                            if (song.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                android.util.Log.d("LocalMusicScreen", "Playing song: ${song.item.title}")
                                                val mediaMetadataList = sortedSongs.map { it.toMediaMetadata() }
                                                playerConnection.playQueue(
                                                    LocalMusicQueue(
                                                        title = "Local Music",
                                                        items = mediaMetadataList,
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!selection) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selection = true
                                            wrappedSongs.forEach { it.isSelected = false }
                                            song.isSelected = true
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
}