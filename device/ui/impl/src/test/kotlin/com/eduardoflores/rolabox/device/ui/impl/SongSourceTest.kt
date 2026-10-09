package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistsKey
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
    fun anyOtherScreen_hasNoSource() {
        assertNull(playbackSourceOf(ArtistsKey))
    }
}
