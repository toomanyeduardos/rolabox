package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.AlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreAlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistAlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenreArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.GenresKey
import com.eduardoflores.rolabox.device.music.ui.api.SongsKey
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSourceTest {
    @Test
    fun anAlbumsSongs_areByAlbum() {
        assertEquals(PlaybackSource.ByAlbum(AlbumId(4)), playbackSourceOf(AlbumSongsKey(4)))
    }

    @Test
    fun anArtistsSongs_areByArtist() {
        assertEquals(PlaybackSource.ByArtist(ArtistId(2)), playbackSourceOf(ArtistSongsKey(2)))
    }

    @Test
    fun everySong_isAllSongs() {
        assertEquals(PlaybackSource.AllSongs, playbackSourceOf(SongsKey))
    }

    @Test
    fun anArtistsSongsInAGenre_areByGenreAndArtist() {
        assertEquals(
            PlaybackSource.ByGenreAndArtist(GenreId(5), ArtistId(2)),
            playbackSourceOf(GenreArtistSongsKey(genreId = 5, artistId = 2)),
        )
    }

    @Test
    fun anAlbumsSongsInAGenre_areByGenreAndAlbum() {
        assertEquals(
            PlaybackSource.ByGenreAndAlbum(GenreId(5), AlbumId(4)),
            playbackSourceOf(GenreAlbumSongsKey(genreId = 5, albumId = 4)),
        )
    }

    @Test
    fun anyOtherScreen_hasNoSource() {
        assertNull(playbackSourceOf(ArtistsKey))
        assertNull(playbackSourceOf(AlbumsKey))
        assertNull(playbackSourceOf(GenresKey))
        assertNull(playbackSourceOf(GenreArtistsKey(5)))
        assertNull(playbackSourceOf(GenreArtistAlbumsKey(5, 2)))
    }
}
