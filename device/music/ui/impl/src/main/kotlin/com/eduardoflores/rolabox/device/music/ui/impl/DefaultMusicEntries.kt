package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.AlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistAlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreAlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistAlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenresKey
import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.music.ui.api.MusicMenuKey
import com.eduardoflores.rolabox.device.music.ui.api.SongsKey
import javax.inject.Inject

/**
 * The entries of the music screens. This is the one place that names their concrete ViewModels, since
 * Hilt creates a ViewModel by its class. The routes take the abstract ones (ADR-021). A ViewModel
 * that needs the id of its key gets it here, from the key. A genre's screens are the same screens, given the genre.
 */
@Suppress("LongMethod") // One entry for each key, and a genre's screens are most of them.
internal class DefaultMusicEntries @Inject constructor() : MusicEntries {
    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onArtistsClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onSongsClick: () -> Unit,
        onArtistClick: (artistId: Long) -> Unit,
        onAllSongsClick: (artistId: Long) -> Unit,
        onAlbumClick: (albumId: Long) -> Unit,
        onSongClick: (songId: Long) -> Unit,
        onGenresClick: () -> Unit,
        onGenreClick: (genreId: Long) -> Unit,
        onGenreArtistClick: (genreId: Long, artistId: Long) -> Unit,
        onGenreAllSongsClick: (genreId: Long, artistId: Long) -> Unit,
        onGenreAlbumClick: (genreId: Long, albumId: Long) -> Unit,
    ) = with(scope) {
        entry<MusicMenuKey> {
            MusicMenuRoute(
                viewModel = hiltViewModel<MusicMenuViewModelImpl>(),
                onArtistsClick = onArtistsClick,
                onAlbumsClick = onAlbumsClick,
                onSongsClick = onSongsClick,
                onGenresClick = onGenresClick,
            )
        }
        entry<ArtistsKey> {
            ArtistsRoute(
                viewModel = hiltViewModel<ArtistsViewModelImpl, ArtistsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(ArtistsSource.All) },
                ),
                onArtistClick = onArtistClick,
            )
        }
        entry<AlbumsKey> {
            AlbumsRoute(viewModel = hiltViewModel<AlbumsViewModelImpl>(), onAlbumClick = onAlbumClick)
        }
        entry<SongsKey> {
            val source = SongsSource.All
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(source) },
                ),
                sortedByName = source.isSortedByName,
                onSongClick = onSongClick,
            )
        }
        entry<ArtistAlbumsKey> { key ->
            ArtistAlbumsRoute(
                viewModel = hiltViewModel<ArtistAlbumsViewModelImpl, ArtistAlbumsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.artistId, genreId = null) },
                ),
                artistId = key.artistId,
                onAllSongsClick = onAllSongsClick,
                onAlbumClick = onAlbumClick,
            )
        }
        entry<AlbumSongsKey> { key ->
            val source = SongsSource.Album(AlbumId(key.albumId))
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(source) },
                ),
                sortedByName = source.isSortedByName,
                onSongClick = onSongClick,
            )
        }
        entry<ArtistSongsKey> { key ->
            val source = SongsSource.Artist(ArtistId(key.artistId))
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(source) },
                ),
                sortedByName = source.isSortedByName,
                onSongClick = onSongClick,
            )
        }
        entry<GenresKey> {
            GenresRoute(viewModel = hiltViewModel<GenresViewModelImpl>(), onGenreClick = onGenreClick)
        }
        entry<GenreArtistsKey> { key ->
            ArtistsRoute(
                viewModel = hiltViewModel<ArtistsViewModelImpl, ArtistsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(ArtistsSource.Genre(GenreId(key.genreId))) },
                ),
                onArtistClick = { artistId -> onGenreArtistClick(key.genreId, artistId) },
            )
        }
        entry<GenreArtistAlbumsKey> { key ->
            ArtistAlbumsRoute(
                viewModel = hiltViewModel<ArtistAlbumsViewModelImpl, ArtistAlbumsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.artistId, key.genreId) },
                ),
                artistId = key.artistId,
                onAllSongsClick = { artistId -> onGenreAllSongsClick(key.genreId, artistId) },
                onAlbumClick = { albumId -> onGenreAlbumClick(key.genreId, albumId) },
            )
        }
        entry<GenreArtistSongsKey> { key ->
            val source = SongsSource.GenreArtist(GenreId(key.genreId), ArtistId(key.artistId))
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(source) },
                ),
                sortedByName = source.isSortedByName,
                onSongClick = onSongClick,
            )
        }
        entry<GenreAlbumSongsKey> { key ->
            val source = SongsSource.GenreAlbum(GenreId(key.genreId), AlbumId(key.albumId))
            SongsRoute(
                viewModel = hiltViewModel<SongsViewModelImpl, SongsViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(source) },
                ),
                sortedByName = source.isSortedByName,
                onSongClick = onSongClick,
            )
        }
    }
}
