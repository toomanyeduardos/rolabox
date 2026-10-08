package com.eduardoflores.rolabox.device.playback.impl

import arrow.core.left
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryPlayerTest {
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000)
    private val otherAlbum = Album(AlbumId(2), artist.id, "Other album", 2001)
    private val library = FakeLibraryRepository().apply {
        artists = listOf(artist)
        albums = listOf(album, otherAlbum)
        songs = (1..5).map { song(it.toLong(), album, duration = (100 + it).seconds) } +
            song(6, otherAlbum, duration = 200.seconds)
    }
    private val player = InMemoryPlayer(library)

    @Test
    fun beforeAnythingIsPlayed_nothingIsLoaded() = runTest {
        assertNull(player.currentSong.first())
        assertNull(player.queueIndex.first())
        assertEquals(emptyList<Any>(), player.queue.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun beforeAnythingIsPlayed_actionsDoNothing() = runTest {
        player.seekTo(30.seconds)
        player.next()
        player.previous()

        assertNull(player.currentSong.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun play_fromTheMiddleQueuesTheWholeListWithTheIndexOnTheSong() = runTest {
        val result = player.play(PlaybackSource.ByAlbum(album.id), SongId(3))

        assertTrue(result.isRight())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), player.queue.first().map { it.id.value })
        assertEquals(2, player.queueIndex.first())
        assertEquals(SongId(3), player.currentSong.first()?.id)
    }

    @Test
    fun play_usesNamesAndTheRealDurationOfTheSong() = runTest {
        player.play(PlaybackSource.AllSongs, SongId(2))

        val song = player.currentSong.first()
        assertEquals("Artist", song?.artist)
        assertEquals("Album", song?.album)
        assertEquals(102.seconds, song?.duration)
    }

    @Test
    fun play_followsTheOrderOfTheSourceItWasSelectedFrom() = runTest {
        player.play(PlaybackSource.ByArtist(artist.id), SongId(6))

        assertEquals(
            listOf("Song 1", "Song 2", "Song 3", "Song 4", "Song 5", "Song 6"),
            player.queue.first().map {
                it.title
            },
        )
        assertEquals(5, player.queueIndex.first())
    }

    @Test
    fun play_resetsThePositionAndReplacesWhatWasLoaded() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.seekTo(50.seconds)

        player.play(PlaybackSource.ByAlbum(otherAlbum.id), SongId(6))

        assertEquals(listOf(SongId(6)), player.queue.first().map { it.id })
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun play_aSongOutsideTheSourceFailsAndChangesNothing() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        val result = player.play(PlaybackSource.ByAlbum(album.id), SongId(6))

        assertEquals(PlaybackError.SongNotInSource(SongId(6), PlaybackSource.ByAlbum(album.id)).left(), result)
        assertEquals(SongId(1), player.currentSong.first()?.id)
    }

    @Test
    fun play_anUnknownSourceFailsWithTheLibraryError() = runTest {
        val result = player.play(PlaybackSource.ByAlbum(AlbumId(99)), SongId(1))

        assertEquals(PlaybackError.Library(LibraryError.AlbumNotFound(AlbumId(99))).left(), result)
        assertNull(player.currentSong.first())
    }

    @Test
    fun play_aReadErrorFailsWithTheLibraryError() = runTest {
        val cause = StorageError.Unavailable
        library.setReadError(cause)

        val result = player.play(PlaybackSource.AllSongs, SongId(1))

        assertEquals(PlaybackError.Library(LibraryError.Storage(cause)).left(), result)
        assertNull(player.currentSong.first())
    }

    @Test
    fun next_inTheMiddleMovesOnAndResetsThePosition() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        player.seekTo(40.seconds)

        player.next()

        assertEquals(SongId(4), player.currentSong.first()?.id)
        assertEquals(3, player.queueIndex.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun previous_inTheMiddleMovesBackAndResetsThePosition() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        player.seekTo(40.seconds)

        player.previous()

        assertEquals(SongId(2), player.currentSong.first()?.id)
        assertEquals(1, player.queueIndex.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun next_onTheLastSongDoesNothing() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(5))
        player.seekTo(40.seconds)

        player.next()

        assertEquals(SongId(5), player.currentSong.first()?.id)
        assertEquals(40.seconds, player.position.first())
    }

    @Test
    fun previous_onTheFirstSongDoesNothing() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.seekTo(40.seconds)

        player.previous()

        assertEquals(SongId(1), player.currentSong.first()?.id)
        assertEquals(40.seconds, player.position.first())
    }

    @Test
    fun seekTo_movesThePosition() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo(30.seconds)

        assertEquals(30.seconds, player.position.first())
    }

    @Test
    fun seekTo_clampsToTheStart() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo((-5).seconds)

        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun seekTo_clampsToTheDurationOfTheSong() = runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo(10_000.seconds)

        assertEquals(101.seconds, player.position.first())
    }

    @Test
    fun setVolume_setsTheLevel() = runTest {
        player.setVolume(70)

        assertEquals(70, player.volume.first())
    }

    @Test
    fun setVolume_clampsBetween0And100() = runTest {
        player.setVolume(140)
        assertEquals(100, player.volume.first())

        player.setVolume(-3)
        assertEquals(0, player.volume.first())
    }

    @Test
    fun theVolumeSurvivesPlayingAnotherSong() = runTest {
        player.setVolume(20)

        player.play(PlaybackSource.AllSongs, SongId(1))

        assertEquals(20, player.volume.first())
    }

    private fun song(id: Long, album: Album, duration: Duration) = Song(
        id = SongId(id),
        albumId = album.id,
        artistId = artist.id,
        title = "Song $id",
        trackNumber = id.toInt(),
        duration = duration,
    )
}
