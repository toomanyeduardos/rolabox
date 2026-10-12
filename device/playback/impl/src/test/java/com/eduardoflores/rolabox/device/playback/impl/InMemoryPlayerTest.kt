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
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val scope = TestScope()
    private val player = InMemoryPlayer(library, scope.backgroundScope, scope.testScheduler.timeSource)

    @Test
    fun beforeAnythingIsPlayed_nothingIsLoaded() = scope.runTest {
        assertNull(player.currentSong.first())
        assertNull(player.queueIndex.first())
        assertEquals(emptyList<Any>(), player.queue.first())
        assertEquals(Duration.ZERO, player.position.first())
        assertFalse(player.isPlaying.first())
    }

    @Test
    fun beforeAnythingIsPlayed_actionsDoNothing() = scope.runTest {
        player.seekTo(30.seconds)
        player.next()
        player.previous()
        player.pause()
        player.resume()
        player.startSeeking(SeekDirection.Forward)
        after(10.seconds)
        player.stopSeeking()

        assertNull(player.currentSong.first())
        assertEquals(Duration.ZERO, player.position.first())
        assertFalse(player.isPlaying.first())
    }

    @Test
    fun play_fromTheMiddleQueuesTheWholeListWithTheIndexOnTheSong() = scope.runTest {
        val result = player.play(PlaybackSource.ByAlbum(album.id), SongId(3))

        assertTrue(result.isRight())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), player.queue.first().map { it.id.value })
        assertEquals(2, player.queueIndex.first())
        assertEquals(SongId(3), player.currentSong.first()?.id)
    }

    @Test
    fun play_usesNamesAndTheRealDurationOfTheSong() = scope.runTest {
        player.play(PlaybackSource.AllSongs, SongId(2))

        val song = player.currentSong.first()
        assertEquals("Artist", song?.artist)
        assertEquals("Album", song?.album)
        assertEquals(102.seconds, song?.duration)
    }

    @Test
    fun play_followsTheOrderOfTheSourceItWasSelectedFrom() = scope.runTest {
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
    fun play_resetsThePositionAndReplacesWhatWasLoaded() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.seekTo(50.seconds)

        player.play(PlaybackSource.ByAlbum(otherAlbum.id), SongId(6))

        assertEquals(listOf(SongId(6)), player.queue.first().map { it.id })
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun play_aSongOutsideTheSourceFailsAndChangesNothing() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        val result = player.play(PlaybackSource.ByAlbum(album.id), SongId(6))

        assertEquals(PlaybackError.SongNotInSource(SongId(6), PlaybackSource.ByAlbum(album.id)).left(), result)
        assertEquals(SongId(1), player.currentSong.first()?.id)
    }

    @Test
    fun play_anUnknownSourceFailsWithTheLibraryError() = scope.runTest {
        val result = player.play(PlaybackSource.ByAlbum(AlbumId(99)), SongId(1))

        assertEquals(PlaybackError.Library(LibraryError.AlbumNotFound(AlbumId(99))).left(), result)
        assertNull(player.currentSong.first())
    }

    @Test
    fun play_aReadErrorFailsWithTheLibraryError() = scope.runTest {
        val cause = StorageError.Unavailable
        library.setReadError(cause)

        val result = player.play(PlaybackSource.AllSongs, SongId(1))

        assertEquals(PlaybackError.Library(LibraryError.Storage(cause)).left(), result)
        assertNull(player.currentSong.first())
    }

    @Test
    fun next_inTheMiddleMovesOnAndResetsThePosition() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        player.seekTo(40.seconds)

        player.next()

        assertEquals(SongId(4), player.currentSong.first()?.id)
        assertEquals(3, player.queueIndex.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun previous_inTheMiddleMovesBackAndResetsThePosition() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        player.seekTo(40.seconds)

        player.previous()

        assertEquals(SongId(2), player.currentSong.first()?.id)
        assertEquals(1, player.queueIndex.first())
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun next_onTheLastSongDoesNothing() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(5))
        player.seekTo(40.seconds)

        player.next()

        assertEquals(SongId(5), player.currentSong.first()?.id)
        assertEquals(40.seconds, player.position.first())
    }

    @Test
    fun previous_onTheFirstSongDoesNothing() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.seekTo(40.seconds)

        player.previous()

        assertEquals(SongId(1), player.currentSong.first()?.id)
        assertEquals(40.seconds, player.position.first())
    }

    @Test
    fun seekTo_movesThePosition() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo(30.seconds)

        assertEquals(30.seconds, player.position.first())
    }

    @Test
    fun seekTo_clampsToTheStart() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo((-5).seconds)

        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun seekTo_clampsToTheDurationOfTheSong() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.seekTo(10_000.seconds)

        assertEquals(101.seconds, player.position.first())
    }

    @Test
    fun play_startsPlaying_andThePositionAdvancesWithTheClock() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        assertTrue(player.isPlaying.first())
        after(10.seconds)

        assertEquals(10.seconds, player.position.first())
    }

    @Test
    fun pause_stopsThePosition_andResumeGoesOnFromThere() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        after(10.seconds)

        player.pause()
        after(30.seconds)

        assertFalse(player.isPlaying.first())
        assertEquals(10.seconds, player.position.first())

        player.resume()
        after(5.seconds)

        assertTrue(player.isPlaying.first())
        assertEquals(15.seconds, player.position.first())
    }

    @Test
    fun pause_betweenTwoTicksKeepsTheTimeSinceTheLastOne() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        after(10.seconds + Tick / 2)

        player.pause()

        assertEquals(10.seconds + Tick / 2, player.position.first())
    }

    @Test
    fun pauseAndResume_twiceAreTheSameAsOnce() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.pause()
        player.pause()
        assertFalse(player.isPlaying.first())

        player.resume()
        player.resume()
        assertTrue(player.isPlaying.first())
    }

    @Test
    fun aSongThatEnds_isFollowedByTheNextFromItsStart() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        after(101.seconds)

        assertEquals(SongId(2), player.currentSong.first()?.id)
        assertEquals(1, player.queueIndex.first())
        assertEquals(Duration.ZERO, player.position.first())
        assertTrue(player.isPlaying.first())

        after(3.seconds)
        assertEquals(3.seconds, player.position.first())
    }

    @Test
    fun theLastSongEnds_andNothingIsLoaded() = scope.runTest {
        player.setVolume(20)
        player.play(PlaybackSource.ByAlbum(album.id), SongId(5))

        after(105.seconds)

        assertNull(player.currentSong.first())
        assertNull(player.queueIndex.first())
        assertEquals(emptyList<Any>(), player.queue.first())
        assertEquals(Duration.ZERO, player.position.first())
        assertFalse(player.isPlaying.first())
        assertEquals(20, player.volume.first())
    }

    @Test
    fun theWholeQueuePlaysToItsEnd() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(4))

        after(104.seconds + 105.seconds - Tick)
        assertEquals(SongId(5), player.currentSong.first()?.id)

        after(Tick)
        assertNull(player.currentSong.first())
    }

    @Test
    fun aPausedSongNeverEnds() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        after(100.seconds)

        player.pause()
        after(500.seconds)

        assertEquals(SongId(1), player.currentSong.first()?.id)
        assertEquals(100.seconds, player.position.first())
    }

    @Test
    fun nextPreviousAndSeekTo_leaveAPausedSongPaused() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        player.pause()

        player.next()
        player.previous()
        player.seekTo(40.seconds)
        after(10.seconds)

        assertFalse(player.isPlaying.first())
        assertEquals(40.seconds, player.position.first())
    }

    @Test
    fun nextAndPrevious_leaveAPlayingSongPlaying() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(3))
        after(10.seconds)

        player.next()
        after(2.seconds)

        assertTrue(player.isPlaying.first())
        assertEquals(2.seconds, player.position.first())
    }

    @Test
    fun play_whileAnotherSongIsPausedStartsPlaying() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.pause()

        player.play(PlaybackSource.ByAlbum(album.id), SongId(2))

        assertTrue(player.isPlaying.first())
    }

    @Test
    fun seeking_movesFasterThanPlaying_andTheSongGoesOnFromThereWhenItStops() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.startSeeking(SeekDirection.Forward)
        after(2.seconds)
        assertEquals(2.seconds * SEEK_SPEED, player.position.first())

        player.stopSeeking()
        after(1.seconds)
        assertEquals(2.seconds * SEEK_SPEED + 1.seconds, player.position.first())
    }

    @Test
    fun seekingBackwards_stopsAtTheStart() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.seekTo(15.seconds)

        player.startSeeking(SeekDirection.Backward)
        after(1.seconds)
        assertEquals(15.seconds - 1.seconds * SEEK_SPEED, player.position.first())

        after(10.seconds)
        assertEquals(Duration.ZERO, player.position.first())
        assertEquals(SongId(1), player.currentSong.first()?.id)
    }

    @Test
    fun seekingForwards_stopsAtTheEnd_andTheSongEndsOnlyOnceItIsReleased() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))

        player.startSeeking(SeekDirection.Forward)
        after(60.seconds)

        assertEquals(SongId(1), player.currentSong.first()?.id)
        assertEquals(101.seconds, player.position.first())

        player.stopSeeking()
        after(Tick)

        assertEquals(SongId(2), player.currentSong.first()?.id)
        assertEquals(Duration.ZERO, player.position.first())
    }

    @Test
    fun seekingForwards_toTheEndOfTheLastSong_endsTheQueueOnceItIsReleased() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(5))

        player.startSeeking(SeekDirection.Forward)
        after(60.seconds)
        assertEquals(SongId(5), player.currentSong.first()?.id)

        player.stopSeeking()
        after(Tick)

        assertNull(player.currentSong.first())
    }

    @Test
    fun seeking_aPausedSongMovesIt_andItStaysPaused() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.pause()

        player.startSeeking(SeekDirection.Forward)
        after(2.seconds)
        player.stopSeeking()
        after(30.seconds)

        assertFalse(player.isPlaying.first())
        assertEquals(2.seconds * SEEK_SPEED, player.position.first())
    }

    @Test
    fun seeking_aPausedSongToItsEndLeavesItThere() = scope.runTest {
        player.play(PlaybackSource.ByAlbum(album.id), SongId(1))
        player.pause()

        player.startSeeking(SeekDirection.Forward)
        after(60.seconds)
        player.stopSeeking()
        after(30.seconds)

        assertEquals(SongId(1), player.currentSong.first()?.id)
        assertEquals(101.seconds, player.position.first())
    }

    @Test
    fun setVolume_setsTheLevel() = scope.runTest {
        player.setVolume(70)

        assertEquals(70, player.volume.first())
    }

    @Test
    fun setVolume_clampsBetween0And100() = scope.runTest {
        player.setVolume(140)
        assertEquals(100, player.volume.first())

        player.setVolume(-3)
        assertEquals(0, player.volume.first())
    }

    @Test
    fun theVolumeSurvivesPlayingAnotherSong() = scope.runTest {
        player.setVolume(20)

        player.play(PlaybackSource.AllSongs, SongId(1))

        assertEquals(20, player.volume.first())
    }

    /** Lets [duration] pass on the clock, with everything that is due by then. */
    private fun TestScope.after(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
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
