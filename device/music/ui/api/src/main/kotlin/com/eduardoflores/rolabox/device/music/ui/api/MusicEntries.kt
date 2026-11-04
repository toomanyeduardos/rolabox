package com.eduardoflores.rolabox.device.music.ui.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of the music screens (ADR-020): it adds their entries, and takes every way out
 * of them as a lambda with ids. The device injects it and maps each exit to a key and a push, as the
 * part's parent, and it is the only module that names these screens' keys outside the part.
 *
 * These are device screens: they only receive the wheel's turn and center (ADR-018, rule 3). The
 * part has no full screens, so it has no entries for the app stack.
 */
interface MusicEntries {
    /**
     * Adds the entries of [MusicMenuKey], [ArtistsKey], [AlbumsKey], [SongsKey], [ArtistAlbumsKey],
     * [AlbumSongsKey] and [ArtistSongsKey], for the screen stack inside the display.
     *
     * The exits: center on Artists, Albums or Songs in the Music menu is [onArtistsClick],
     * [onAlbumsClick] or [onSongsClick]; on an artist, [onArtistClick]; on "All Songs" in an artist's
     * albums, [onAllSongsClick] with that artist; on an album, in an artist's albums or in Albums,
     * [onAlbumClick]; on a song, [onSongClick]. Genres and Playlists do nothing yet.
     */
    @Suppress("LongParameterList") // One lambda per exit, so the contract lists how its screens are left.
    fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onArtistsClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onSongsClick: () -> Unit,
        onArtistClick: (artistId: Long) -> Unit,
        onAllSongsClick: (artistId: Long) -> Unit,
        onAlbumClick: (albumId: Long) -> Unit,
        onSongClick: (songId: Long) -> Unit,
    )
}
