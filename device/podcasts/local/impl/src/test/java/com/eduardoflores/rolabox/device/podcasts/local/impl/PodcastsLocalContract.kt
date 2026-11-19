package com.eduardoflores.rolabox.device.podcasts.local.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Follow
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FeedWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the podcasts' local sources promise, run against the Room database ([RoomPodcastsLocalContractTest]) and
 * against the fake the repositories are tested with ([FakePodcastsLocalContractTest]). A check that passes for one
 * and fails for the other is a fake that no longer behaves as the database does.
 */
abstract class PodcastsLocalContract {
    protected abstract val local: PodcastsLocalDataSource
    protected abstract val progress: EpisodeProgressLocalDataSource

    /** Stores a follow as sync would, of a show that may not be in the library. */
    protected abstract suspend fun storeFollow(directoryId: DirectoryId, feedUrl: String, followed: Boolean)

    // The sort keys are the :impl's to make. These stand for them: lower case, without a leading "The".
    private fun show(
        id: Long,
        title: String,
        owner: String?,
        key: String = owner?.lowercase().orEmpty(),
        titleSortKey: String = title.lowercase().removePrefix("the "),
        ownerSortKey: String? = owner?.lowercase()?.removePrefix("the "),
    ) = ShowWrite(
        directoryId = DirectoryId.itunes(id),
        feedUrl = "https://example.com/$id/feed",
        title = title,
        ownerName = owner,
        ownerKey = OwnerId(key),
        titleSortKey = titleSortKey,
        ownerSortKey = ownerSortKey,
    )

    private fun episode(key: String, publishedAt: Long? = null, title: String = key) = EpisodeWrite(
        itemKey = key,
        guid = key,
        title = title,
        publishedAt = publishedAt?.let(Instant::ofEpochSecond),
        length = 30.minutes,
        description = null,
        audioUrl = "https://example.com/$key.mp3",
    )

    protected suspend fun add(show: ShowWrite, episode: EpisodeWrite = episode("${show.directoryId.value}-1")) =
        local.addEpisode(show, episode, NOW).getOrNull()!!

    protected suspend fun addShow(id: Long, title: String = "The Daily", owner: String? = "NY Times"): ShowId =
        local.getEpisode(add(show(id, title, owner))).getOrNull()!!.showId

    @Test
    fun owners_areSortedWithoutALeadingThe_andTheOneWithNoNameLast() = runTest {
        add(show(1, "Unsigned", owner = null))
        add(show(2, "The Daily", "NY Times"))
        add(show(3, "Small Talk", "The Small Rooms"))
        add(show(4, "Bench Notes", "harbor audio"))

        val owners = local.observeOwners().first().getOrNull()!!

        assertEquals(listOf("harbor audio", "NY Times", "The Small Rooms", null), owners.map { it.name })
    }

    @Test
    fun anOwner_isOne_whatever_theCaseOfItsShows_withTheSpellingMostHave() = runTest {
        add(show(1, "Hard Fork", "ny times", key = "ny times"))
        add(show(2, "The Daily", "NY Times", key = "ny times"))
        add(show(3, "The Ezra Klein Show", "NY Times", key = "ny times"))

        assertEquals(listOf(Owner(OwnerId("ny times"), "NY Times")), local.observeOwners().first().getOrNull())
    }

    @Test
    fun anOwnersSpelling_tiedBetweenShows_isTheFirstAToZ() = runTest {
        add(show(1, "Hard Fork", "ny times", key = "ny times"))
        add(show(2, "The Daily", "NY Times", key = "ny times"))

        assertEquals(listOf(Owner(OwnerId("ny times"), "NY Times")), local.observeOwners().first().getOrNull())
    }

    @Test
    fun anOwnersShows_areSortedWithoutALeadingThe() = runTest {
        add(show(1, "The Ezra Klein Show", "NY Times"))
        add(show(2, "The Daily", "NY Times"))
        add(show(3, "Hard Fork", "NY Times"))
        add(show(4, "Daily Dose of Design", "Field Notes Media"))

        val shows = local.observeShowsByOwner(OwnerId("ny times")).first().getOrNull()!!

        assertEquals(listOf("The Daily", "The Ezra Klein Show", "Hard Fork"), shows.map { it.title })
    }

    @Test
    fun ownersAndShows_followTheirSortKeys_notTheirSpelling() = runTest {
        add(show(1, "Daily Dose of Design", "Field Notes Media"))
        add(show(2, "Écoute", "Écoute Média", titleSortKey = "ecoute", ownerSortKey = "ecoute media"))
        add(show(3, "Daily Brief", "Écoute Média", key = "écoute média", titleSortKey = "daily brief"))
        add(show(4, "Bench Notes", "Dans la Rue"))

        val owners = local.observeOwners().first().getOrNull()!!
        val shows = local.observeShowsByOwner(OwnerId("écoute média")).first().getOrNull()!!

        assertEquals(listOf("Dans la Rue", "Écoute Média", "Field Notes Media"), owners.map { it.name })
        assertEquals(listOf("Daily Brief", "Écoute"), shows.map { it.title })
    }

    @Test
    fun anOwnerNoShowHas_hasNoShows() = runTest {
        add(show(1, "The Daily", "NY Times"))

        assertEquals(emptyList<Any>(), local.observeShowsByOwner(OwnerId("nobody")).first().getOrNull())
    }

    @Test
    fun aShowsEpisodes_areNewestFirst_andThoseWithNoDateLast() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val showId = local.getEpisode(add(daily, episode("old", publishedAt = 100))).getOrNull()!!.showId
        add(daily, episode("undated"))
        add(daily, episode("new", publishedAt = 300))
        add(daily, episode("middle", publishedAt = 200))
        add(daily, episode("middle, added later", publishedAt = 200))
        add(show(2, "Hard Fork", "NY Times"), episode("another show's", publishedAt = 250))

        val episodes = local.observeEpisodesByShow(showId).first().getOrNull()!!

        assertEquals(
            listOf("new", "middle, added later", "middle", "old", "undated"),
            episodes.map { it.title },
        )
    }

    @Test
    fun anEpisode_isReadAsItWasWritten() = runTest {
        val written = episode("g1", publishedAt = 100).copy(audioSizeBytes = 42, description = "About the moon")
        val id = add(show(1, "The Daily", "NY Times"), written)

        val record = local.getEpisode(id).getOrNull()!!

        assertEquals(written.title, record.title)
        assertEquals(written.publishedAt, record.publishedAt)
        assertEquals(written.length, record.feedLength)
        assertEquals(written.description, record.description)
        assertEquals(written.audioUrl, record.audioUrl)
        assertEquals(written.audioSizeBytes, record.audioSizeBytes)
        assertEquals(record, local.observeEpisode(id).first().getOrNull())
    }

    @Test
    fun addingTheSameEpisodeTwice_keepsOneShowAndOneEpisode_asTheyWere() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val first = add(daily, episode("g1"))
        val second = add(daily.copy(title = "Renamed"), episode("g1", title = "Retitled"))

        assertEquals(first, second)
        val record = local.getEpisode(first).getOrNull()!!
        assertEquals("g1", record.title)
        assertEquals("The Daily", local.getShow(record.showId).getOrNull()?.title)
        assertEquals(1, local.observeEpisodesByShow(record.showId).first().getOrNull()?.size)
    }

    @Test
    fun anotherEpisodeOfAShowInTheLibrary_joinsThatShow() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val first = local.getEpisode(add(daily, episode("g1"))).getOrNull()!!
        val second = local.getEpisode(add(daily, episode("g2"))).getOrNull()!!

        assertEquals(first.showId, second.showId)
        assertEquals(1, local.observeOwners().first().getOrNull()?.size)
    }

    @Test
    fun theSameItemKey_inTwoShows_isTwoEpisodes() = runTest {
        val first = add(show(1, "The Daily", "NY Times"), episode("g1"))
        val second = add(show(2, "Hard Fork", "NY Times"), episode("g1"))

        assertEquals(false, first == second)
    }

    @Test
    fun aShow_isReadAsItWasWritten() = runTest {
        val showId = addShow(1, "The Daily", "NY Times")

        val show = local.getShow(showId).getOrNull()!!

        assertEquals(DirectoryId.itunes(1), show.directoryId)
        assertEquals("The Daily", show.title)
        assertEquals(OwnerId("ny times"), show.ownerId)
        assertEquals("NY Times", show.ownerName)
        assertEquals(show, local.observeShow(showId).first().getOrNull())
    }

    @Test
    fun whatIsntThere_isNull() = runTest {
        assertNull(local.getShow(ShowId(9)).getOrNull())
        assertNull(local.observeShow(ShowId(9)).first().getOrNull())
        assertNull(local.getEpisode(EpisodeId(9)).getOrNull())
        assertNull(local.observeEpisode(EpisodeId(9)).first().getOrNull())
        assertEquals(emptyList<Any>(), local.observeEpisodesByShow(ShowId(9)).first().getOrNull())
        assertEquals(emptyList<Any>(), local.observeOwners().first().getOrNull())
    }

    @Test
    fun follow_isOffUntilSet_andCanBeTurnedOffAgain() = runTest {
        val showId = addShow(1)
        assertEquals(false, local.getShow(showId).getOrNull()?.isFollowed)

        assertEquals(Unit.right(), local.setFollow(showId, followed = true, NOW))
        assertEquals(true, local.observeShow(showId).first().getOrNull()?.isFollowed)
        assertEquals(true, local.observeShowsByOwner(OwnerId("ny times")).first().getOrNull()?.single()?.isFollowed)

        local.setFollow(showId, followed = false, LATER)
        assertEquals(false, local.getShow(showId).getOrNull()?.isFollowed)
    }

    @Test
    fun follow_ofAShowThatIsntThere_isShowNotFound() = runTest {
        assertEquals(PodcastError.ShowNotFound(ShowId(9)).left(), local.setFollow(ShowId(9), followed = true, NOW))
    }

    @Test
    fun progress_isReadWithItsEpisode_andReplaced() = runTest {
        val id = add(show(1, "The Daily", "NY Times"))
        assertNull(local.getEpisode(id).getOrNull()?.progress)

        val stored = ProgressRecord(12.minutes, isPlayed = false, playerLength = 31.minutes, lastPlayedAt = NOW)
        assertEquals(Unit.right(), progress.setProgress(id, stored))
        assertEquals(stored, local.getEpisode(id).getOrNull()?.progress)

        val played = stored.copy(position = 30.minutes, isPlayed = true, playerLength = null, lastPlayedAt = LATER)
        progress.setProgress(id, played)
        assertEquals(played, local.observeEpisode(id).first().getOrNull()?.progress)
        assertEquals(
            played,
            local.observeEpisodesByShow(local.getEpisode(id).getOrNull()!!.showId).first()
                .getOrNull()?.single()?.progress,
        )
    }

    @Test
    fun anArtworkSource_hasNoImagesUntilTheFeedNamesThem_andThenHasThem() = runTest {
        val written = show(1, "The Daily", "NY Times")
        val episodeId = add(written, episode("g1"))
        val other = add(written, episode("g2"))
        val showId = local.getEpisode(episodeId).getOrNull()!!.showId

        val before = local.getArtworkSource(episodeId).getOrNull()!!
        assertEquals(ArtworkSource(episodeId, showId, "g1", written.feedUrl, null, null), before)

        local.setImageUrls(showId, "https://example.com/show.jpg", mapOf("g1" to "https://example.com/g1.jpg"))

        val after = local.getArtworkSource(episodeId).getOrNull()!!
        assertEquals("https://example.com/g1.jpg", after.episodeImageUrl)
        assertEquals("https://example.com/show.jpg", after.showImageUrl)
        assertNull(local.getArtworkSource(other).getOrNull()!!.episodeImageUrl)
    }

    @Test
    fun theArtworkSource_ofAnEpisodeThatIsntThere_isNull() = runTest {
        assertEquals(null.right(), local.getArtworkSource(EpisodeId(99)))
    }

    @Test
    fun progress_ofAnEpisodeThatIsntThere_isEpisodeNotFound() = runTest {
        val stored = ProgressRecord(1.minutes, isPlayed = false, playerLength = null, lastPlayedAt = NOW)

        assertEquals(PodcastError.EpisodeNotFound(EpisodeId(9)).left(), progress.setProgress(EpisodeId(9), stored))
    }

    @Test
    fun aShow_isFoundByItsDirectoryId_andAnEpisodeByItsShowsDirectoryIdAndItemKey() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val episodeId = add(daily, episode("a"))
        add(show(2, "Hard Fork", "NY Times"), episode("b"))

        val found = local.observeShowByDirectoryId(daily.directoryId).first().getOrNull()
        assertEquals("The Daily", found?.title)
        assertEquals(episodeId, local.getEpisodeId(daily.directoryId, "a").getOrNull())
        assertEquals("a", local.getEpisode(episodeId).getOrNull()?.itemKey)
        assertEquals(null.right(), local.getEpisodeId(daily.directoryId, "b"))
        assertEquals(null.right(), local.getEpisodeId(DirectoryId.itunes(9), "a"))
        assertEquals(null.right(), local.observeShowByDirectoryId(DirectoryId.itunes(9)).first())
    }

    @Test
    fun following_addsTheShowAndEachEpisode_andTurnsItsFollowOn() = runTest {
        val daily = show(1, "The Daily", "NY Times")

        val showId = local.follow(daily, listOf(episode("a", 10), episode("b", 20)), NOW).getOrNull()!!

        assertEquals(true, local.observeShow(showId).first().getOrNull()?.isFollowed)
        assertEquals(
            listOf("b", "a"),
            local.observeEpisodesByShow(showId).first().getOrNull()!!.map { it.itemKey },
        )
    }

    @Test
    fun following_aShowInTheLibrary_keepsWhatIsThere_andAddsOnlyTheNewEpisodes() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val played = add(daily, episode("a", 10, title = "As played"))
        val showId = local.getEpisode(played).getOrNull()!!.showId

        val followed = local.follow(
            daily.copy(title = "Renamed"),
            listOf(episode("a", 10, title = "As the feed says now"), episode("b", 20)),
            LATER,
        ).getOrNull()

        assertEquals(showId, followed)
        val show = local.observeShow(showId).first().getOrNull()!!
        assertEquals("The Daily", show.title)
        assertEquals(true, show.isFollowed)
        val episodes = local.observeEpisodesByShow(showId).first().getOrNull()!!
        assertEquals(listOf("b", "a"), episodes.map { it.itemKey })
        assertEquals("As played", episodes.single { it.id == played }.title)
    }

    private fun feed(show: ShowWrite, vararg episodes: EpisodeWrite, etag: String? = "e1") = FeedWrite(
        show = show,
        imageUrl = null,
        etag = etag,
        lastModified = null,
        episodes = episodes.toList(),
    )

    private suspend fun episodesOf(showId: ShowId) = local.observeEpisodesByShow(showId).first().getOrNull()!!

    @Test
    fun followedFeeds_areTheFollowsThatAreOn_withTheirShowWhenItIsThere() = runTest {
        val daily = addShow(1)
        local.setFollow(daily, followed = true, NOW)
        addShow(2, title = "Unfollowed")
        storeFollow(DirectoryId.itunes(3), "https://example.com/3/feed", followed = true)
        storeFollow(DirectoryId.itunes(4), "https://example.com/4/feed", followed = false)

        val feeds = local.getFollowedFeeds().getOrNull()!!

        assertEquals(listOf(DirectoryId.itunes(1), DirectoryId.itunes(3)), feeds.map { it.directoryId })
        assertEquals(daily, feeds[0].show?.id)
        assertEquals(listOf("https://example.com/1/feed", "https://example.com/3/feed"), feeds.map { it.feedUrl })
        assertNull(feeds[1].show)
    }

    @Test
    fun applyingAFeed_addsTheNewEpisodes_updatesTheOthers_andKeepsTheirProgress() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val playedId = add(daily, episode("a", 10, title = "Old title"))
        val showId = local.getEpisode(playedId).getOrNull()!!.showId
        local.setFollow(showId, followed = true, NOW)
        val stored = ProgressRecord(12.minutes, isPlayed = false, playerLength = null, lastPlayedAt = NOW)
        progress.setProgress(playedId, stored)

        val applied = local.applyFeed(feed(daily, episode("a", 10, title = "New title"), episode("b", 20)), LATER)

        assertEquals(showId.right(), applied)
        val episodes = episodesOf(showId)
        assertEquals(listOf("b", "a"), episodes.map { it.itemKey })
        assertEquals("New title", episodes.single { it.id == playedId }.title)
        assertEquals(stored, episodes.single { it.id == playedId }.progress)
        assertEquals("e1", local.getFollowedFeeds().getOrNull()!!.single().etag)
    }

    @Test
    fun anEpisodeThatLeavesTheFeed_isKept_withWhenItLeft_andClearedWhenItComesBack() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)
        local.applyFeed(feed(daily, episode("a", 10), episode("b", 20)), NOW)

        local.applyFeed(feed(daily, episode("b", 20)), LATER)
        val left = episodesOf(showId).associate { it.itemKey to it.leftFeedAt }
        local.applyFeed(feed(daily, episode("a", 10), episode("b", 20)), LATER.plusSeconds(1))
        val back = episodesOf(showId).associate { it.itemKey to it.leftFeedAt }

        assertEquals(LATER, left["a"])
        assertNull(left["b"])
        assertNull(back["a"])
    }

    @Test
    fun anEpisodeThatLeftTheFeed_keepsWhenItFirstLeft() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)

        local.applyFeed(feed(daily, episode("b", 20)), NOW)
        local.applyFeed(feed(daily, episode("b", 20)), LATER)

        assertEquals(NOW, episodesOf(showId).single { it.itemKey != "b" }.leftFeedAt)
    }

    @Test
    fun applyingAFeed_ofAShowNotFollowed_writesNothing() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val showId = addShow(1)

        val applied = local.applyFeed(feed(daily.copy(title = "Renamed"), episode("new", 10)), LATER)

        assertEquals(null.right(), applied)
        assertEquals("The Daily", local.getShow(showId).getOrNull()?.title)
        assertEquals(1, episodesOf(showId).size)
        assertEquals(null.right(), local.applyFeed(feed(show(2, "Not here", null), episode("x")), LATER))
        assertNull(local.observeShowByDirectoryId(DirectoryId.itunes(2)).first().getOrNull())
    }

    @Test
    fun applyingAFeed_ofAFollowedShowNotInTheLibrary_addsIt() = runTest {
        storeFollow(DirectoryId.itunes(3), "https://example.com/3/feed", followed = true)

        val showId = local.applyFeed(feed(show(3, "Synced", "Someone"), episode("a", 10)), NOW).getOrNull()!!

        val show = local.getShow(showId).getOrNull()!!
        assertEquals("Synced" to true, show.title to show.isFollowed)
        assertEquals(listOf("a"), episodesOf(showId).map { it.itemKey })
        assertEquals(showId, local.getFollowedFeeds().getOrNull()!!.single().show?.id)
    }

    @Test
    fun aFeedThatMoved_movesTheShowAndItsFollow() = runTest {
        val daily = show(1, "The Daily", "NY Times")
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)

        local.applyFeed(feed(daily.copy(feedUrl = "https://example.com/moved"), episode("a")), LATER)

        assertEquals("https://example.com/moved", local.getFollowedFeeds().getOrNull()!!.single().feedUrl)
        assertFollowFeedUrl(DirectoryId.itunes(1), "https://example.com/moved")
    }

    @Test
    fun aFeedNotModified_changesNoEpisode_andMovesTheShowWhenItMoved() = runTest {
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)
        val before = episodesOf(showId)

        local.applyFeedNotModified(DirectoryId.itunes(1), "https://example.com/moved", LATER)

        assertEquals(before, episodesOf(showId))
        assertEquals("https://example.com/moved", local.getFollowedFeeds().getOrNull()!!.single().feedUrl)
        assertFollowFeedUrl(DirectoryId.itunes(1), "https://example.com/moved")
    }

    private fun synced(feedUrl: String, followed: Boolean, at: Instant) =
        SyncedValue(Follow(feedUrl, followed), SyncTimestamp(at.toEpochMilli()))

    @Test
    fun syncedFollows_areEveryFollow_OnAndOff_withWhenEachWasSet() = runTest {
        val daily = addShow(1)
        local.setFollow(daily, followed = true, NOW)
        local.setFollow(daily, followed = false, LATER)
        storeFollow(DirectoryId.itunes(3), "https://example.com/3/feed", followed = true)

        val expected = mapOf(
            DirectoryId.itunes(1) to synced("https://example.com/1/feed", followed = false, LATER),
            DirectoryId.itunes(3) to synced("https://example.com/3/feed", followed = true, NOW),
        )
        assertEquals(expected, local.getSyncedFollows().getOrNull())
        assertEquals(expected, local.observeSyncedFollows().first().getOrNull())
    }

    @Test
    fun applyingFollows_addsANewOne_andReturnsIt_asTurnedOn() = runTest {
        val follows = mapOf(DirectoryId.itunes(3) to synced("https://example.com/3/feed", followed = true, NOW))

        assertEquals(setOf(DirectoryId.itunes(3)).right(), local.applySyncedFollows(follows))
        assertEquals(follows, local.getSyncedFollows().getOrNull())
        assertEquals(DirectoryId.itunes(3), local.getFollowedFeeds().getOrNull()!!.single().directoryId)
    }

    @Test
    fun applyingFollows_keepsTheNewerOfWhatIsStoredAndWhatIsApplied() = runTest {
        val daily = addShow(1)
        val serial = addShow(2, title = "Serial")
        local.setFollow(daily, followed = true, LATER)
        local.setFollow(serial, followed = true, NOW)

        val turnedOn = local.applySyncedFollows(
            mapOf(
                // Older than this phone's Follow, made while the sync ran.
                DirectoryId.itunes(1) to synced("https://example.com/1/feed", followed = false, NOW),
                // Newer: an unfollow on another phone.
                DirectoryId.itunes(2) to synced("https://example.com/2/feed", followed = false, LATER),
            ),
        )

        assertEquals(emptySet<DirectoryId>().right(), turnedOn)
        assertEquals(true, local.getShow(daily).getOrNull()?.isFollowed)
        assertEquals(false, local.getShow(serial).getOrNull()?.isFollowed)
    }

    // A tie goes to the merged follow, the remote one, as LastWriteWins resolves it on every phone.
    @Test
    fun applyingFollows_onATie_takesTheAppliedOne() = runTest {
        val daily = addShow(1)
        local.setFollow(daily, followed = false, NOW)

        val turnedOn = local.applySyncedFollows(
            mapOf(DirectoryId.itunes(1) to synced("https://example.com/1/feed", followed = true, NOW)),
        )

        assertEquals(setOf(DirectoryId.itunes(1)).right(), turnedOn)
        assertEquals(true, local.getShow(daily).getOrNull()?.isFollowed)
    }

    // A feed's move isn't synced as the user's choice, and this phone read where the feed is now.
    @Test
    fun applyingFollows_ofAShowInTheLibrary_keepsTheShowsFeedUrl() = runTest {
        val daily = addShow(1)
        local.setFollow(daily, followed = false, NOW)

        local.applySyncedFollows(
            mapOf(DirectoryId.itunes(1) to synced("https://example.com/old", followed = true, LATER)),
        )

        assertEquals(
            synced("https://example.com/1/feed", followed = true, LATER),
            local.getSyncedFollows().getOrNull()!![DirectoryId.itunes(1)],
        )
    }

    @Test
    fun applyingAFollowThatWasAlreadyOn_doesNotReturnIt() = runTest {
        val daily = addShow(1)
        local.setFollow(daily, followed = true, NOW)

        val turnedOn = local.applySyncedFollows(
            mapOf(DirectoryId.itunes(1) to synced("https://example.com/1/feed", followed = true, LATER)),
        )

        assertEquals(emptySet<DirectoryId>().right(), turnedOn)
    }

    /** That the follow keeps [feedUrl] for sync, with its `updatedAt` as it was set. */
    protected abstract suspend fun assertFollowFeedUrl(directoryId: DirectoryId, feedUrl: String)

    protected companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_000)
        val LATER: Instant = Instant.ofEpochMilli(2_000)
    }
}
