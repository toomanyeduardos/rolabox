package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.userdata.api.Repeat
import com.eduardoflores.rolabox.common.userdata.api.Shuffle
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Shuffle and repeat (ADR-028, rules 2 to 7) on the real player: a real [ExoPlayer] with Media3's fake clock, playing
 * MP3s of library/02's fixtures behind [MediaPlayback], with a random source fixed for each test. The settings are set
 * as the device's Settings sets them, and playback reads them itself.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class ShuffleAndRepeatTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artist = Artist(ArtistId(1), "Artist")

    // Three albums of one artist: songs 1 to 3, 4 and 5, and 6 and 7, in that order on each album.
    private val albums = listOf(
        Album(AlbumId(1), artist.id, "First", 2000, ArtworkRef(SongId(1))),
        Album(AlbumId(2), artist.id, "Second", 2001, ArtworkRef(SongId(4))),
        Album(AlbumId(3), artist.id, "Third", 2002, ArtworkRef(SongId(6))),
    )
    private val albumOf = mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 1, 5 to 1, 6 to 2, 7 to 2)
    private val library = FakeLibraryRepository()
    private val userData = FakeUserDataRepository()
    private val scope = TestScope()
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        library.artists = listOf(artist)
        library.albums = albums
        library.songs = (1..7).map(::song)
        player = buildPlayer(context) {
            setClock(FakeClock(true))
            setStuckBufferingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingNotEndingTimeoutMs(Int.MAX_VALUE)
            setStuckSuppressedDetectionTimeoutMs(Int.MAX_VALUE)
        }
        val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val connection = object : PlayerConnection {
            override val dispatcher = dispatcher

            override suspend fun player() = player.right()
        }
        playback = MediaPlayback(
            library,
            connection,
            PhoneVolume(context),
            FakePlaybackLocalDataSource(),
            scope.backgroundScope,
            scope.testScheduler.timeSource,
            FakeArtworkRepository(),
            userData,
            PlayOrders(Random(SEED)),
        )
    }

    @After
    fun tearDown() {
        player.release()
    }

    // Rule 2, Off: the list's order, from the selected row, with the rows before it before it.
    @Test
    fun shuffleOff_playsTheListsOrder_andTheStateSaysSo() = scope.runTest {
        playback.play(allSongs, item(3))

        assertEquals((1..7).map(::item), queue())
        assertEquals(2, playback.queueIndex.first())
        assertFalse(playback.isShuffled.first())
        assertEquals(RepeatMode.Off, playback.repeatMode.first())
        assertFalse(player.shuffleModeEnabled)
    }

    // Rules 2, 6 and 7, Songs: the selected song first, every other once, and the playlist in the list's order.
    @Test
    fun shuffleSongs_playsTheSelectedSongFirst_thenEveryOtherSongOnce() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)

        playback.play(allSongs, item(3))

        val queue = queue()
        assertEquals(item(3), queue.first())
        assertEquals((1..7).map(::item).toSet(), queue.toSet())
        assertEquals(7, queue.size)
        assertEquals(0, playback.queueIndex.first())
        assertEquals(item(3), playback.currentItem.first()?.id)
        assertTrue(playback.isShuffled.first())
        assertTrue(player.shuffleModeEnabled)
        assertEquals((1..7).map { item(it).mediaId }, playlist())
    }

    // Rule 3: ⏭ and the end of a song follow the play order, which the player keeps.
    @Test
    fun shuffled_nextAndTheEndOfASong_followThePlayOrder() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        playback.play(allSongs, item(3))
        val queue = queue()

        playback.next()
        assertEquals(queue[1], playback.currentItem.first()?.id)
        assertEquals(1, playback.queueIndex.first())

        playUntilTheNextItem()
        assertEquals(queue[2], playback.currentItem.first()?.id)
        assertEquals(2, playback.queueIndex.first())
    }

    // Rule 2, Albums: the row's album from the row, then the other albums whole and in their order, then the rest.
    @Test
    fun shuffleAlbums_playsTheRowsAlbumFromTheRow_thenTheOtherAlbumsWhole_thenTheSongsBeforeTheRow() = scope.runTest {
        userData.setShuffle(Shuffle.ALBUMS)

        playback.play(PlaybackSource.ByArtist(artist.id), item(2))

        val queue = queue()
        assertEquals(listOf(item(2), item(3)), queue.take(2))
        val middle = queue.subList(2, 6)
        val second = listOf(item(4), item(5))
        val third = listOf(item(6), item(7))
        assertTrue("$middle", middle == second + third || middle == third + second)
        assertEquals(item(1), queue.last())
    }

    // Rule 3, Off: the ends of the play order are the ends.
    @Test
    fun shuffled_nextOnTheLastOfThePlayOrder_doesNothing_withoutRepeat() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        playback.play(allSongs, item(3))
        val queue = queue()
        repeat(queue.size - 1) { playback.next() }

        playback.next()

        assertEquals(queue.last(), playback.currentItem.first()?.id)
    }

    // Rule 3, One: the song that ends starts again, and that is a start of it.
    @Test
    fun repeatOne_startsTheSongAgainWhenItEnds() = scope.runTest {
        userData.setRepeat(Repeat.ONE)
        playback.play(byAlbum(1), item(1))
        val starts = playback.startCount.first()

        TestPlayerRunHelper.run(
            player,
        ).untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        player.pause()
        tick()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(starts + 1, playback.startCount.first())
        assertEquals(RepeatMode.One, playback.repeatMode.first())
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
    }

    // Rule 3, One: ⏮ and ⏭ move as without repeat, and do nothing at the ends.
    @Test
    fun repeatOne_nextMovesOn_andDoesNothingOnTheLast() = scope.runTest {
        userData.setRepeat(Repeat.ONE)
        playback.play(byAlbum(1), item(2))

        playback.next()
        assertEquals(item(3), playback.currentItem.first()?.id)

        playback.next()
        assertEquals(item(3), playback.currentItem.first()?.id)
    }

    // Rule 3, All: the last song is followed by the first, and ⏭ and ⏮ wrap.
    @Test
    fun repeatAll_wrapsAtBothEnds() = scope.runTest {
        userData.setRepeat(Repeat.ALL)
        playback.play(byAlbum(1), item(3))

        playback.next()
        assertEquals(item(1), playback.currentItem.first()?.id)

        playback.previous()
        assertEquals(item(3), playback.currentItem.first()?.id)

        playUntilTheNextItem()
        assertEquals(item(1), playback.currentItem.first()?.id)
    }

    // Rule 3, All with Shuffle on: each round is a new order of the whole list, which never starts with the song that
    // just played.
    @Test
    fun repeatAllShuffled_aNewRound_isANewOrder_thatDoesntStartWithTheSongThatJustPlayed() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        userData.setRepeat(Repeat.ALL)
        playback.play(allSongs, item(3))
        val firstRound = queue()
        repeat(firstRound.size - 1) { playback.next() }
        assertEquals(firstRound.last(), playback.currentItem.first()?.id)

        playback.next()

        val secondRound = queue()
        assertEquals(secondRound.first(), playback.currentItem.first()?.id)
        assertEquals(0, playback.queueIndex.first())
        assertNotEquals(firstRound.last(), secondRound.first())
        assertEquals(firstRound.toSet(), secondRound.toSet())
        assertNotEquals(firstRound, secondRound)
    }

    // Rule 3: the end of the last song of a round starts the next round, as ⏭ does.
    @Test
    fun repeatAllShuffled_theLastSongEnding_startsTheNextRound() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        userData.setRepeat(Repeat.ALL)
        playback.play(allSongs, item(3))
        val firstRound = queue()
        repeat(firstRound.size - 1) { playback.next() }

        playUntilTheNextItem()

        val secondRound = queue()
        assertEquals(secondRound.first(), playback.currentItem.first()?.id)
        assertNotEquals(firstRound.last(), secondRound.first())
    }

    // Rule 3: ⏮ on the last song of a round still goes to the song before it, not to the next round.
    @Test
    fun repeatAllShuffled_previousOnTheLastSong_goesToTheSongBeforeIt() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        userData.setRepeat(Repeat.ALL)
        playback.play(allSongs, item(3))
        val round = queue()
        repeat(round.size - 1) { playback.next() }

        playback.previous()

        assertEquals(round[round.size - 2], playback.currentItem.first()?.id)
        assertEquals(round, queue())
    }

    // Rule 3: ⏮ on the first song of a round goes to the last song of that round.
    @Test
    fun repeatAllShuffled_previousOnTheFirstSong_goesToTheLastSongOfTheRound() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        userData.setRepeat(Repeat.ALL)
        playback.play(allSongs, item(3))
        val round = queue()

        playback.previous()

        assertEquals(round.last(), playback.currentItem.first()?.id)
        assertEquals(round.size - 1, playback.queueIndex.first())
    }

    // Rule 4: turned on, the loaded song becomes the first of a new order, and keeps playing where it is.
    @Test
    fun turningShuffleOn_makesTheLoadedSongTheFirstOfANewOrder_withoutInterruptingIt() = scope.runTest {
        playback.play(allSongs, item(3))
        playTo(2.seconds)
        val starts = playback.startCount.first()
        val before = playback.position.first()

        userData.setShuffle(Shuffle.SONGS)
        tick()

        assertEquals(item(3), playback.currentItem.first()?.id)
        assertEquals(item(3), queue().first())
        assertEquals(0, playback.queueIndex.first())
        assertTrue("${playback.position.first()} after $before", playback.position.first() >= before)
        assertTrue(playback.isPlaying.first())
        assertEquals(starts, playback.startCount.first())
        assertTrue(playback.isShuffled.first())
    }

    // Rule 4: turned off, the list's order is back, at the loaded song.
    @Test
    fun turningShuffleOff_putsTheListsOrderBack_atTheLoadedSong() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        playback.play(allSongs, item(5))

        userData.setShuffle(Shuffle.OFF)
        tick()

        assertEquals((1..7).map(::item), queue())
        assertEquals(4, playback.queueIndex.first())
        assertFalse(player.shuffleModeEnabled)
        playback.next()
        assertEquals(item(6), playback.currentItem.first()?.id)
    }

    // Rule 4: from one value to the other, a new order by the new value, from the loaded song.
    @Test
    fun changingShuffleFromSongsToAlbums_reordersByAlbums_fromTheLoadedSong() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        playback.play(PlaybackSource.ByArtist(artist.id), item(4))

        userData.setShuffle(Shuffle.ALBUMS)
        tick()

        val queue = queue()
        assertEquals(listOf(item(4), item(5)), queue.take(2))
        assertEquals(item(4), playback.currentItem.first()?.id)
    }

    // Rule 4: a change of Repeat applies at once, and doesn't change the order.
    @Test
    fun changingRepeat_appliesAtOnce_andKeepsTheOrder() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        playback.play(allSongs, item(3))
        val queue = queue()

        userData.setRepeat(Repeat.ALL)
        tick()

        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertEquals(RepeatMode.All, playback.repeatMode.first())
        assertEquals(queue, queue())
    }

    // Rule 5: Shuffle Songs plays every song in a random order, whatever the setting says.
    @Test
    fun shuffleSongs_playsEverySongShuffled_withShuffleOff() = scope.runTest {
        val result = playback.shuffle(allSongs)

        assertEquals(Unit.right(), result)
        val queue = queue()
        assertEquals((1..7).map(::item).toSet(), queue.toSet())
        assertEquals(7, queue.size)
        assertEquals(0, playback.queueIndex.first())
        assertEquals(queue.first(), playback.currentItem.first()?.id)
        assertTrue(playback.isShuffled.first())
        assertTrue(playback.isPlaying.first())
    }

    // Rule 5: it doesn't read or change the setting, so changing the setting leaves it shuffled by songs.
    @Test
    fun shuffleSongs_staysShuffled_whateverTheSettingBecomes() = scope.runTest {
        playback.shuffle(allSongs)
        val queue = queue()

        userData.setShuffle(Shuffle.ALBUMS)
        tick()
        assertEquals(queue, queue())

        userData.setShuffle(Shuffle.OFF)
        tick()
        assertEquals(queue, queue())
        assertTrue(playback.isShuffled.first())
        assertEquals(Shuffle.OFF, userData.observeUserData().first().getOrNull()?.shuffle)
    }

    // Rule 5: Repeat applies to it, with a new order each round.
    @Test
    fun shuffleSongs_underRepeatAll_hasANewRound() = scope.runTest {
        userData.setRepeat(Repeat.ALL)
        playback.shuffle(allSongs)
        val firstRound = queue()
        repeat(firstRound.size - 1) { playback.next() }

        playback.next()

        assertNotEquals(firstRound.last(), playback.currentItem.first()?.id)
        assertEquals(0, playback.queueIndex.first())
    }

    @Test
    fun shuffleSongs_withNothingToPlay_failsAndChangesNothing() = scope.runTest {
        library.songs = emptyList()

        val result = playback.shuffle(allSongs)

        assertEquals(PlaybackError.NothingToPlay(allSongs).left(), result)
        assertEquals(0, player.mediaItemCount)
    }

    // Rule 7: the repeat mode is in the state whether or not anything is loaded.
    @Test
    fun theRepeatMode_isInTheState_withNothingLoaded() = scope.runTest {
        userData.setRepeat(Repeat.ALL)
        tick()

        assertEquals(RepeatMode.All, playback.repeatMode.first())
        assertFalse(playback.isShuffled.first())
    }

    private suspend fun queue() = playback.queue.first().map { it.id }

    private fun playlist() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    private fun TestScope.playTo(position: Duration) {
        TestPlayerRunHelper.playUntilPosition(player, player.currentMediaItemIndex, position.inWholeMilliseconds)
        tick()
    }

    /**
     * Lets the player play until the current item ends and the next one starts, held there, and lets a tick read it.
     */
    private fun TestScope.playUntilTheNextItem() {
        TestPlayerRunHelper.run(
            player,
        ).untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        player.pause()
        tick()
    }

    private fun TestScope.tick() {
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(Tick)
        runCurrent()
    }

    private fun byAlbum(number: Int) = PlaybackSource.ByAlbum(albums[number - 1].id)

    private fun item(number: Int) = PlaybackItemId.Song(SongId(number.toLong()))

    private fun song(number: Int): Song {
        val file = File(folder.root, "$number.mp3")
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = LENGTH_MS)).bytes())
        val album = albums[albumOf.getValue(number)]
        return Song(
            id = SongId(number.toLong()),
            albumId = album.id,
            artistId = artist.id,
            title = "Song $number",
            trackNumber = number,
            duration = LENGTH_MS.milliseconds,
            uri = Uri.fromFile(file).toString(),
        )
    }

    private companion object {
        const val SEED = 7
        const val LENGTH_MS = 4_000
        val allSongs = PlaybackSource.AllSongs
    }
}
