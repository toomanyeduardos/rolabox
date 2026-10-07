package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistAlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.music.ui.api.MusicMenuKey
import javax.inject.Inject

/**
 * The entries of the music screens. This is the one place that names their concrete ViewModels, since
 * Hilt creates a ViewModel by its class. The routes take the abstract ones (ADR-021). A ViewModel
 * that needs the id of its key gets it here, from the key.
 */
internal class DefaultMusicEntries @Inject constructor() : MusicEntries {
    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onArtistsClick: () -> Unit,
        onArtistClick: (artistId: Long) -> Unit,
        onAllSongsClick: (artistId: Long) -> Unit,
        onAlbumClick: (albumId: Long) -> Unit,
        onSongClick: (songId: Long) -> Unit,
    ) = with(scope) {
        entry<MusicMenuKey> { MusicMenuRoute(onArtistsClick = onArtistsClick) }
        entry<ArtistsKey> {
            ArtistsRoute(viewModel = hiltViewModel<ArtistsViewModelImpl>(), onArtistClick = onArtistClick)
        }
        entry<ArtistAlbumsKey> { key ->
            ArtistAlbumsRoute(
                viewModel = hiltViewModel<ArtistAlbumsViewModelImpl, ArtistAlbumsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.artistId) },
                ),
                artistId = key.artistId,
                onAllSongsClick = onAllSongsClick,
                onAlbumClick = onAlbumClick,
            )
        }
        entry<AlbumSongsKey> { key ->
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(SongsSource.Album(AlbumId(key.albumId))) },
                ),
                onSongClick = onSongClick,
            )
        }
        entry<ArtistSongsKey> { key ->
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(SongsSource.Artist(ArtistId(key.artistId))) },
                ),
                onSongClick = onSongClick,
            )
        }
    }
}
