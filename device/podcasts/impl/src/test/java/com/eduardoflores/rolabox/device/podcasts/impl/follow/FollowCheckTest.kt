package com.eduardoflores.rolabox.device.podcasts.impl.follow

import arrow.core.Either
import arrow.core.left
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultPodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.FakeEpisodeDownloads
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import com.eduardoflores.rolabox.device.podcasts.network.api.Feed
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import com.eduardoflores.rolabox.device.podcasts.testing.FakeFeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowCheckTest {
    private val local = FakePodcastsLocalDataSource()
    private val feeds = FakeFeedNetworkDataSource()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val log = RecordingFollowCheckLog()
    private val check = FollowCheck(local, feeds, clock, log)
    private val repository = DefaultPodcastsRepository(local, FakeEpisodeDownloads(), clock, FakeFollowCheckScheduler())

    private val daily = DirectoryShow(DirectoryId.itunes(1), "The Daily", "NY Times", DAILY_FEED)

    private fun item(guid: String, day: Long = 1, title: String = "Episode $guid") = FeedEpisode(
        guid = guid,
        title = title,
        publishedAt = Instant.ofEpochSecond(day * DAY),
        length = 30.minutes,
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        audioSizeBytes = null,
        audioType = "audio/mpeg",
        imageUrl = null,
    )

    private fun read(
        vararg items: FeedEpisode,
        title: String? = "The Daily",
        etag: String? = "etag-2",
        movedTo: String? = null,
    ) = FeedAnswer.Read(
        feed = Feed(title = title, ownerName = "The New York Times", imageUrl = null, episodes = items.toList()),
        validators = FeedValidators(etag, lastModified = null),
        movedTo = movedTo,
    )

    /** The Daily in the library, followed, with the episode of [guid] played to 12 minutes. */
    private suspend fun followedDaily(guid: String = "g1"): ShowId {
        val episode =
            DirectoryEpisode(
                guid,
                "Episode $guid",
                Instant.ofEpochSecond(DAY),
                30.minutes,
                null,
                "https://example.com/$guid.mp3",
            )
        val id = repository.addEpisode(daily, episode).getOrNull()!!
        local.setProgress(id, PROGRESS)
        val showId = repository.getEpisode(id).getOrNull()!!.showId
        repository.setFollowed(showId, followed = true)
        return showId
    }

    private suspend fun episodesOf(showId: ShowId): List<EpisodeRecord> =
        local.observeEpisodesByShow(showId).first().getOrNull()!!

    @Test
    fun newEpisodes_areAdded_asNotDownloadedAndUnplayed_andTheOthersKeepTheirProgress() = runTest {
        val showId = followedDaily()
        feeds.serve(DAILY_FEED, read(item("g1", title = "Renamed"), item("g2", day = 2)))

        assertEquals(FollowCheckOutcome.Done, check.checkAll())

        val episodes = episodesOf(showId)
        assertEquals(listOf("g2", "g1"), episodes.map { it.itemKey })
        assertNull(episodes[0].progress)
        assertEquals("Renamed" to PROGRESS, episodes[1].title to episodes[1].progress)
        assertEquals("The New York Times", local.getShow(showId).getOrNull()?.ownerName)
    }

    @Test
    fun noNewEpisode_changesNothing_butTheValidators() = runTest {
        val showId = followedDaily()
        val before = episodesOf(showId)
        feeds.serve(DAILY_FEED, read(item("g1")))

        check.checkAll()
        check.checkAll()

        assertEquals(before.map { it.id to it.progress }, episodesOf(showId).map { it.id to it.progress })
        // The second check sends back what the first one was answered with.
        assertEquals(listOf(FeedValidators.None, FeedValidators("etag-2", null)), feeds.fetched.map { it.second })
    }

    @Test
    fun aNotModifiedFeed_changesNoEpisode_evenOneThatWouldHaveLeft() = runTest {
        val showId = followedDaily()
        val before = episodesOf(showId)
        feeds.serve(DAILY_FEED, FeedAnswer.NotModified(movedTo = null))

        check.checkAll()

        assertEquals(before, episodesOf(showId))
        assertEquals(emptyList<Any>(), log.lines)
    }

    @Test
    fun anEpisodeThatLeavesTheFeed_isMarked_andOneThatComesBack_isCleared() = runTest {
        val showId = followedDaily()
        feeds.serve(DAILY_FEED, read(item("g2", day = 2)))
        check.checkAll()
        val left = episodesOf(showId).single { it.itemKey == "g1" }

        feeds.serve(DAILY_FEED, read(item("g1"), item("g2", day = 2)))
        check.checkAll()

        assertEquals(NOW, left.leftFeedAt)
        assertEquals(PROGRESS, left.progress)
        assertNull(episodesOf(showId).single { it.itemKey == "g1" }.leftFeedAt)
    }

    @Test
    fun aFeedThatFails_isSkippedAndLogged_andTheOthersAreStillRead() = runTest {
        val dailyId = followedDaily()
        val other = DirectoryShow(DirectoryId.itunes(2), "Hard Fork", "NY Times", OTHER_FEED)
        val episode =
            DirectoryEpisode("h1", "Episode h1", Instant.ofEpochSecond(DAY), 30.minutes, null, "https://e.com/h1")
        val otherId = repository.getEpisode(repository.addEpisode(other, episode).getOrNull()!!).getOrNull()!!.showId
        repository.setFollowed(otherId, followed = true)
        feeds.answers = mapOf(DAILY_FEED to FeedNetworkError.Unreachable.left())
        feeds.serve(OTHER_FEED, read(item("h1"), item("h2", day = 2)))
        val before = episodesOf(dailyId)

        assertEquals(FollowCheckOutcome.Done, check.checkAll())

        assertEquals(before, episodesOf(dailyId))
        assertEquals(2, episodesOf(otherId).size)
        assertEquals(listOf("itunes-1 Unreachable"), log.lines)
    }

    @Test
    fun aFeedReadWithNoEpisode_isSkipped_andHidesNothing() = runTest {
        val showId = followedDaily()
        feeds.serve(DAILY_FEED, read())

        check.checkAll()

        assertNull(episodesOf(showId).single().leftFeedAt)
        assertEquals(listOf("itunes-1 NoEpisodes"), log.lines)
    }

    @Test
    fun aShowUnfollowedWhileTheWorkRan_isntChanged() = runTest {
        val showId = followedDaily()
        val unfollowing = object : FeedNetworkDataSource {
            override suspend fun fetch(url: String, since: FeedValidators): Either<FeedNetworkError, FeedAnswer> {
                repository.setFollowed(showId, followed = false)
                return Either.Right(read(item("g2", day = 2)))
            }
        }

        FollowCheck(local, unfollowing, clock, log).checkAll()

        val episodes = episodesOf(showId)
        assertEquals(listOf("g1"), episodes.map { it.itemKey })
        assertNull(episodes.single().leftFeedAt)
    }

    @Test
    fun aFollowedShowMissingFromTheLibrary_isAddedFromItsFeed() = runTest {
        local.storeFollow(DirectoryId.itunes(3), SYNCED_FEED, followed = true, NOW)
        feeds.serve(SYNCED_FEED, read(item("s1"), title = "Synced Show"))

        check.checkAll()

        val show = local.observeShowByDirectoryId(DirectoryId.itunes(3)).first().getOrNull()!!
        assertEquals("Synced Show" to true, show.title to show.isFollowed)
        assertEquals(listOf("s1"), episodesOf(show.id).map { it.itemKey })
    }

    @Test
    fun aFollowedShowMissingFromTheLibrary_withNoTitle_isntAdded() = runTest {
        local.storeFollow(DirectoryId.itunes(3), SYNCED_FEED, followed = true, NOW)
        feeds.serve(SYNCED_FEED, read(item("s1"), title = "  "))

        check.checkAll()

        assertNull(local.observeShowByDirectoryId(DirectoryId.itunes(3)).first().getOrNull())
        assertEquals(listOf("itunes-3 NoTitle"), log.lines)
    }

    @Test
    fun aFeedThatMoved_movesTheShowAndItsFollow_withoutChangingWhenItWasFollowed() = runTest {
        followedDaily()
        feeds.serve(DAILY_FEED, read(item("g1"), movedTo = MOVED_FEED))

        check.checkAll()

        assertEquals(MOVED_FEED, local.showFeedUrls[DirectoryId.itunes(1)])
        assertEquals(MOVED_FEED, local.followFeedUrls[DirectoryId.itunes(1)])
        assertEquals(true to NOW, local.follows[DirectoryId.itunes(1)])
    }

    @Test
    fun checkingOneShow_readsOnlyItsFeed() = runTest {
        followedDaily()
        local.storeFollow(DirectoryId.itunes(3), SYNCED_FEED, followed = true, NOW)
        feeds.serve(SYNCED_FEED, read(item("s1"), title = "Synced Show"))

        check.checkShow(DirectoryId.itunes(3))

        assertEquals(listOf(SYNCED_FEED), feeds.fetched.map { it.first })
    }

    @Test
    fun followsThatCantBeRead_areRetried() = runTest {
        local.readError = StorageError.Unavailable

        assertEquals(FollowCheckOutcome.Retry, check.checkAll())
        assertEquals(emptyList<Any>(), feeds.fetched)
    }

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(5_000)
        const val DAY = 86_400L
        const val DAILY_FEED = "https://example.com/daily"
        const val OTHER_FEED = "https://example.com/other"
        const val SYNCED_FEED = "https://example.com/synced"
        const val MOVED_FEED = "https://example.com/moved"
        val PROGRESS = ProgressRecord(12.minutes, isPlayed = false, playerLength = null, lastPlayedAt = NOW)
    }
}

/** The lines the check logs, as "<directory id> <reason>". */
private class RecordingFollowCheckLog : FollowCheckLog {
    val lines = mutableListOf<String>()

    override fun failed(directoryId: DirectoryId?, reason: String) {
        lines += "${directoryId?.value} $reason"
    }
}
