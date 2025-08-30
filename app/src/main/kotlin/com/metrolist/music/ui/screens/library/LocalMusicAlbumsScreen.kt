package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.viewmodels.LocalMusicViewModel
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import coil3.compose.AsyncImage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class LocalAlbumSortType {
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT
}

enum class LocalAlbumFilter {
    ALL,
    REGULAR_ALBUMS,
    COMPILATIONS
}

/**
 * Normalize album names to handle slight variations
 * This helps group albums that might have minor differences in naming
 */
private fun normalizeAlbumName(albumName: String): String {
    return albumName.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ") // Normalize whitespace
        .replace(Regex("[()\\[\\]{}]"), "") // Remove brackets/parentheses
        .replace(Regex("[-_]"), " ") // Convert dashes/underscores to spaces
        .replace("remaster", "")
        .replace("remastered", "")
        .replace("deluxe edition", "")
        .replace("deluxe", "")
        .replace("special edition", "")
        .replace("expanded edition", "")
        .trim()
}

data class LocalMusicAlbum(
    val name: String,
    val artist: String,
    val songs: List<com.metrolist.music.db.entities.LocalMusicEntity>,
    val albumArtUri: String? = null,
    val isCompilation: Boolean = false
) {
    val songCount: Int get() = songs.size
    val totalDuration: Long get() = songs.sumOf { it.duration }
    val year: Int get() = songs.maxOfOrNull { it.year } ?: 0
    val uniqueArtists: List<String> get() = songs.map { it.artist }.distinct()
    val displayArtist: String get() = if (isCompilation) "Various Artists" else artist
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalMusicAlbumsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    onExitLocalAlbums: () -> Unit = {},
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allLocalMusic by viewModel.allLocalMusic.collectAsState()
    
    // Sorting preferences
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        stringPreferencesKey("local_album_sort_type"),
        LocalAlbumSortType.NAME
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        stringPreferencesKey("local_album_sort_descending"),
        false
    )
    
    // Filter preferences
    val (albumFilter, onAlbumFilterChange) = rememberEnumPreference(
        stringPreferencesKey("local_album_filter"),
        LocalAlbumFilter.ALL
    )

    // Group local music by album with enhanced discovery
    val localAlbums by remember(allLocalMusic, sortType, sortDescending, albumFilter) {
        derivedStateOf {
            val groupedAlbums = allLocalMusic
                .filter { it.album.isNotBlank() }
                .groupBy { normalizeAlbumName(it.album) } // Group by normalized album name
                .mapNotNull { (normalizedAlbumName, songs) ->
                    if (songs.isEmpty()) return@mapNotNull null
                    
                    val uniqueArtists = songs.map { it.artist.trim() }.distinct().filter { it.isNotBlank() }
                    val artistCounts = songs.groupBy { it.artist.trim() }.mapValues { it.value.size }
                    
                    // Enhanced compilation detection
                    val isCompilation = when {
                        uniqueArtists.size >= 4 -> true // 4+ artists is definitely compilation
                        uniqueArtists.size >= 3 && artistCounts.values.all { it <= 2 } -> true // 3+ artists with few tracks each
                        songs.any { it.artist.contains("feat.", ignoreCase = true) || 
                                   it.artist.contains("ft.", ignoreCase = true) } -> false // Featured artists don't make it compilation
                        else -> false
                    }
                    
                    // Determine the primary artist for the album
                    val primaryArtist = when {
                        isCompilation -> "Various Artists"
                        uniqueArtists.size == 1 -> uniqueArtists.first()
                        else -> {
                            // Find the artist with the most tracks in this album
                            artistCounts.maxByOrNull { it.value }?.key ?: uniqueArtists.first()
                        }
                    }
                    
                    // Use the original album name (not normalized) for display
                    val displayAlbumName = songs.first().album.trim()
                    
                    LocalMusicAlbum(
                        name = displayAlbumName,
                        artist = primaryArtist,
                        songs = songs.sortedBy { it.track.takeIf { it > 0 } ?: Int.MAX_VALUE },
                        albumArtUri = songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri 
                                    ?: songs.firstOrNull()?.albumArtUri,
                        isCompilation = isCompilation
                    )
                }
            
            // Apply filtering
            val filteredAlbums = when (albumFilter) {
                LocalAlbumFilter.ALL -> groupedAlbums
                LocalAlbumFilter.REGULAR_ALBUMS -> groupedAlbums.filter { !it.isCompilation }
                LocalAlbumFilter.COMPILATIONS -> groupedAlbums.filter { it.isCompilation }
            }
            
            // Apply sorting
            val sortedAlbums = when (sortType) {
                LocalAlbumSortType.NAME -> filteredAlbums.sortedBy { it.name.lowercase() }
                LocalAlbumSortType.ARTIST -> filteredAlbums.sortedBy { it.displayArtist.lowercase() }
                LocalAlbumSortType.YEAR -> filteredAlbums.sortedBy { it.year }
                LocalAlbumSortType.SONG_COUNT -> filteredAlbums.sortedBy { it.songCount }
            }
            
            if (sortDescending) sortedAlbums.reversed() else sortedAlbums
        }
    }

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
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.filter_local_music_albums)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { onExitLocalAlbums() },
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close)
                            )
                        },
                    )
                    ChipsRow(
                        chips = listOf(
                            LocalAlbumFilter.ALL to "All",
                            LocalAlbumFilter.REGULAR_ALBUMS to "Albums",
                            LocalAlbumFilter.COMPILATIONS to "Compilations"
                        ),
                        currentValue = albumFilter,
                        onValueUpdate = onAlbumFilterChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                LocalAlbumSortType.NAME -> R.string.sort_by_name
                                LocalAlbumSortType.ARTIST -> R.string.sort_by_artist
                                LocalAlbumSortType.YEAR -> R.string.sort_by_year
                                LocalAlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                            }
                        },
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(
                            R.plurals.n_album,
                            localAlbums.size,
                            localAlbums.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (localAlbums.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.album,
                        text = stringResource(R.string.no_local_music_found),
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                items(
                    items = localAlbums,
                    key = { "${it.name}_${it.artist}" }
                ) { album ->
                    LocalMusicAlbumItem(
                        album = album,
                        onClick = {
                            // Navigate to the local music album page with URL encoding
                            val encodedAlbumName = URLEncoder.encode(album.name, StandardCharsets.UTF_8.toString())
                            val encodedArtistName = URLEncoder.encode(album.artist, StandardCharsets.UTF_8.toString())
                            navController.navigate("local_album/$encodedAlbumName/$encodedArtistName")
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun LocalMusicAlbumItem(
    album: LocalMusicAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = album.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                placeholder = painterResource(R.drawable.album)
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = album.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = buildString {
                        append(pluralStringResource(R.plurals.n_song, album.songCount, album.songCount))
                        if (album.year > 0) append(" • ${album.year}")
                        if (album.isCompilation) {
                            append(" • ${album.uniqueArtists.size} artists")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}