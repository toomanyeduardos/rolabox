package com.eduardoflores.rolabox.device.playback.local.impl

import arrow.core.right
import com.eduardoflores.rolabox.common.storage.testing.FakePreferencesStore
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.local.api.PlayOrder
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPlaybackLocalDataSourceTest {
    private val preferences = FakePreferencesStore()
    private val dataSource = DefaultPlaybackLocalDataSource(preferences)

    @Test
    fun nothingStored_isNoPoint() = runTest {
        assertEquals(null.right(), dataSource.resumePoint())
    }

    @Test
    fun aPointComesBackAsItWasStored_fromEveryKindOfSource() = runTest {
        listOf(
            PlaybackSource.AllSongs,
            PlaybackSource.ByArtist(ArtistId(7)),
            PlaybackSource.ByAlbum(AlbumId(8)),
            PlaybackSource.ByGenreAndArtist(GenreId(5), ArtistId(7)),
            PlaybackSource.ByGenreAndAlbum(GenreId(5), AlbumId(8)),
        ).forEach { source ->
            val point = ResumePoint(source, PlaybackItemId.Song(SongId(9)), 3, 65.seconds)

            dataSource.store(point)

            assertEquals(point.right(), dataSource.resumePoint())
        }
    }

    @Test
    fun aClearedPoint_isNoPoint() = runTest {
        dataSource.store(ResumePoint(PlaybackSource.AllSongs, PlaybackItemId.Song(SongId(1)), 0, 1.seconds))

        dataSource.clear()

        assertEquals(null.right(), dataSource.resumePoint())
    }

    @Test
    fun aValueThatCantBeRead_isNoPoint() = runTest {
        listOf("nonsense", "all|song:1|0", "album:x|song:1|0|0", "all|episode:1|0|0", "all|song:1|-1|0").forEach {
            preferences.setString("playback_resume_point", it)

            assertEquals(null.right(), dataSource.resumePoint())
        }
    }

    // ADR-028: a shuffled queue's order, and what shuffled it, apart from the point.
    @Test
    fun anOrderComesBackAsItWasStored_whateverShuffledIt() = runTest {
        ShuffledBy.entries.forEach { shuffledBy ->
            val order = PlayOrder(listOf(3L, 1L, 2L).map { PlaybackItemId.Song(SongId(it)) }, shuffledBy)

            dataSource.storeOrder(order)

            assertEquals(order.right(), dataSource.playOrder())
        }
    }

    @Test
    fun noOrder_isStoredAsNone() = runTest {
        dataSource.storeOrder(PlayOrder(listOf(PlaybackItemId.Song(SongId(1))), ShuffledBy.Songs))

        dataSource.storeOrder(null)

        assertEquals(null.right(), dataSource.playOrder())
    }

    @Test
    fun theOrder_isWrittenApartFromThePoint() = runTest {
        val order = PlayOrder(listOf(PlaybackItemId.Song(SongId(2))), ShuffledBy.ShuffleSongs)
        dataSource.storeOrder(order)

        dataSource.store(ResumePoint(PlaybackSource.AllSongs, PlaybackItemId.Song(SongId(2)), 0, 1.seconds))

        assertEquals(order.right(), dataSource.playOrder())
    }

    @Test
    fun clear_forgetsThePointAndTheOrder() = runTest {
        dataSource.store(ResumePoint(PlaybackSource.AllSongs, PlaybackItemId.Song(SongId(1)), 0, 1.seconds))
        dataSource.storeOrder(PlayOrder(listOf(PlaybackItemId.Song(SongId(1))), ShuffledBy.Albums))

        dataSource.clear()

        assertEquals(null.right(), dataSource.resumePoint())
        assertEquals(null.right(), dataSource.playOrder())
    }

    @Test
    fun anOrderThatCantBeRead_isNoOrder() = runTest {
        listOf("nonsense", "songs", "songs|song:1|song:1", "artists|song:1", "songs|song:1|episode:2").forEach {
            preferences.setString("playback_play_order", it)

            assertEquals(null.right(), dataSource.playOrder())
        }
    }
}
