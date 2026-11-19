package com.eduardoflores.rolabox.device.podcasts.local.impl

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// A show and its Follow switch, Off when it has none.
private const val SHOWS = "SELECT s.id AS id, s.directoryId AS directoryId, s.title AS title, " +
    "s.ownerKey AS ownerKey, s.ownerName AS ownerName, COALESCE(f.followed, 0) AS followed " +
    "FROM shows s LEFT JOIN follows f ON f.directoryId = s.directoryId"

// An episode and its progress, null when it was never started.
private const val EPISODES = "SELECT e.id AS id, e.showId AS showId, e.itemKey AS itemKey, e.title AS title, " +
    "e.publishedAt AS publishedAt, e.lengthMillis AS lengthMillis, e.description AS description, " +
    "e.audioUrl AS audioUrl, e.audioSize AS audioSize, p.positionMillis AS positionMillis, p.played AS played, " +
    "p.playerLengthMillis AS playerLengthMillis, p.lastPlayedAt AS lastPlayedAt, e.leftFeedAt AS leftFeedAt " +
    "FROM episodes e LEFT JOIN progress p ON p.episodeId = e.id"

/** The shows, their episodes and the follows. */
@Suppress("TooManyFunctions") // One interface for the part's lists, and the steps of its transactions.
@Dao
internal interface PodcastsDao {
    // Each owner's name is the spelling most of its shows have. The sort keys are the :impl's, ignoring case and
    // accents, so the shows of one owner have the same one. The owner with no name sorts last.
    @Query(
        "SELECT s.ownerKey AS ownerKey, (SELECT o.ownerName FROM shows o WHERE o.ownerKey = s.ownerKey " +
            "GROUP BY o.ownerName ORDER BY count(*) DESC, o.ownerName LIMIT 1) AS name " +
            "FROM shows s GROUP BY s.ownerKey " +
            "ORDER BY min(s.ownerSortKey) IS NULL, min(s.ownerSortKey), s.ownerKey",
    )
    fun observeOwners(): Flow<List<OwnerRow>>

    @Query("$SHOWS WHERE s.ownerKey = :ownerKey ORDER BY s.titleSortKey, s.id")
    fun observeShowsByOwner(ownerKey: String): Flow<List<ShowRow>>

    @Query("$SHOWS WHERE s.id = :id")
    fun observeShow(id: Long): Flow<ShowRow?>

    @Query("$SHOWS WHERE s.id = :id")
    suspend fun getShow(id: Long): ShowRow?

    @Query("$SHOWS WHERE s.directoryId = :directoryId")
    fun observeShowByDirectoryId(directoryId: String): Flow<ShowRow?>

    @Query(
        "SELECT e.id FROM episodes e JOIN shows s ON s.id = e.showId " +
            "WHERE s.directoryId = :directoryId AND e.itemKey = :itemKey",
    )
    suspend fun episodeIdOf(directoryId: String, itemKey: String): Long?

    @Query("$EPISODES WHERE e.showId = :showId ORDER BY e.publishedAt IS NULL, e.publishedAt DESC, e.id DESC")
    fun observeEpisodesByShow(showId: Long): Flow<List<EpisodeRow>>

    @Query("$EPISODES WHERE e.id = :id")
    fun observeEpisode(id: Long): Flow<EpisodeRow?>

    @Query("$EPISODES WHERE e.id = :id")
    suspend fun getEpisode(id: Long): EpisodeRow?

    @Upsert
    suspend fun upsertFollow(follow: FollowEntity)

    @Query("SELECT * FROM shows WHERE id = :id")
    suspend fun getShowEntity(id: Long): ShowEntity?

    @Query("SELECT * FROM follows WHERE directoryId = :directoryId")
    suspend fun getFollow(directoryId: String): FollowEntity?

    /** Stores the show's Follow switch, and returns whether the show is there. */
    @Transaction
    suspend fun setFollow(showId: Long, followed: Boolean, updatedAt: Long): Boolean {
        val show = getShowEntity(showId) ?: return false
        upsertFollow(FollowEntity(show.directoryId, show.feedUrl, followed, updatedAt))
        return true
    }

    @Query("SELECT id FROM shows WHERE directoryId = :directoryId")
    suspend fun showIdOf(directoryId: String): Long?

    @Query("SELECT id FROM episodes WHERE showId = :showId AND itemKey = :itemKey")
    suspend fun episodeIdOf(showId: Long, itemKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShow(show: ShowEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query(
        "SELECT e.id AS episodeId, e.showId AS showId, e.itemKey AS itemKey, s.feedUrl AS feedUrl, " +
            "e.imageUrl AS episodeImageUrl, s.imageUrl AS showImageUrl " +
            "FROM episodes e JOIN shows s ON s.id = e.showId WHERE e.id = :episodeId",
    )
    suspend fun getArtworkSource(episodeId: Long): ArtworkSourceRow?

    @Query("UPDATE shows SET imageUrl = :imageUrl WHERE id = :showId")
    suspend fun setShowImageUrl(showId: Long, imageUrl: String?)

    @Query("UPDATE episodes SET imageUrl = :imageUrl WHERE showId = :showId AND itemKey = :itemKey")
    suspend fun setEpisodeImageUrl(showId: Long, itemKey: String, imageUrl: String)

    /** Stores the image URLs a show's feed names, the show's and its episodes', in one transaction. */
    @Transaction
    suspend fun setImageUrls(showId: Long, showImageUrl: String?, episodeImageUrls: Map<String, String>) {
        setShowImageUrl(showId, showImageUrl)
        episodeImageUrls.forEach { (itemKey, url) -> setEpisodeImageUrl(showId, itemKey, url) }
    }

    /** Adds the show and the episode that aren't there yet, and returns the episode's id. */
    @Transaction
    suspend fun addEpisode(show: ShowEntity, episode: EpisodeEntity): Long {
        val showId = showIdOf(show.directoryId) ?: insertShow(show)
        return episodeIdOf(showId, episode.itemKey) ?: insertEpisode(episode.copy(showId = showId))
    }

    /** Adds the show and the episodes that aren't there yet, turns its follow On, and returns the show's id. */
    @Transaction
    suspend fun follow(show: ShowEntity, episodes: List<EpisodeEntity>, follow: FollowEntity): Long {
        val showId = showIdOf(show.directoryId) ?: insertShow(show)
        episodes.forEach { episode ->
            if (episodeIdOf(showId, episode.itemKey) == null) insertEpisode(episode.copy(showId = showId))
        }
        upsertFollow(follow)
        return showId
    }

    @Query(
        "SELECT f.directoryId AS directoryId, COALESCE(s.feedUrl, f.feedUrl) AS feedUrl, s.id AS showId, " +
            "s.etag AS etag, s.lastModified AS lastModified " +
            "FROM follows f LEFT JOIN shows s ON s.directoryId = f.directoryId " +
            "WHERE f.followed = 1 ORDER BY f.directoryId",
    )
    suspend fun getFollowedFeeds(): List<FollowedFeedRow>

    @Query(
        "UPDATE shows SET feedUrl = :feedUrl, title = :title, titleSortKey = :titleSortKey, ownerName = :ownerName, " +
            "ownerKey = :ownerKey, ownerSortKey = :ownerSortKey, imageUrl = COALESCE(:imageUrl, imageUrl), " +
            "etag = :etag, lastModified = :lastModified, feedReadAt = :feedReadAt WHERE id = :id",
    )
    @Suppress("LongParameterList") // One for each column the feed writes.
    suspend fun updateShowFromFeed(
        id: Long,
        feedUrl: String,
        title: String,
        titleSortKey: String,
        ownerName: String?,
        ownerKey: String,
        ownerSortKey: String?,
        imageUrl: String?,
        etag: String?,
        lastModified: String?,
        feedReadAt: Long,
    )

    @Query(
        "UPDATE episodes SET guid = :guid, title = :title, publishedAt = :publishedAt, lengthMillis = :lengthMillis, " +
            "description = :description, audioUrl = :audioUrl, audioSize = :audioSize, audioType = :audioType, " +
            "imageUrl = COALESCE(:imageUrl, imageUrl), leftFeedAt = NULL WHERE id = :id",
    )
    @Suppress("LongParameterList") // One for each column the feed writes.
    suspend fun updateEpisodeFromFeed(
        id: Long,
        guid: String?,
        title: String?,
        publishedAt: Long?,
        lengthMillis: Long?,
        description: String?,
        audioUrl: String,
        audioSize: Long?,
        audioType: String?,
        imageUrl: String?,
    )

    @Query("SELECT id, itemKey FROM episodes WHERE showId = :showId AND leftFeedAt IS NULL")
    suspend fun getEpisodesInFeed(showId: Long): List<EpisodeKeyRow>

    @Query("UPDATE episodes SET leftFeedAt = :leftFeedAt WHERE id = :id")
    suspend fun setLeftFeedAt(id: Long, leftFeedAt: Long)

    @Query("UPDATE shows SET feedUrl = :feedUrl, feedReadAt = :feedReadAt WHERE directoryId = :directoryId")
    suspend fun setFeedRead(directoryId: String, feedUrl: String, feedReadAt: Long): Int

    /**
     * Applies a feed read whole to its show, while its follow is On, and returns the show's id, or `null` when the
     * follow isn't On. [show] is the show as the feed has it, and [episodes] every episode of the feed.
     */
    @Transaction
    suspend fun applyFeed(show: ShowEntity, episodes: List<EpisodeEntity>, readAt: Long): Long? {
        val follow = getFollow(show.directoryId)?.takeIf { it.followed } ?: return null
        val showId = showIdOf(show.directoryId)?.also { id ->
            updateShowFromFeed(
                id = id,
                feedUrl = show.feedUrl,
                title = show.title,
                titleSortKey = show.titleSortKey,
                ownerName = show.ownerName,
                ownerKey = show.ownerKey,
                ownerSortKey = show.ownerSortKey,
                imageUrl = show.imageUrl,
                etag = show.etag,
                lastModified = show.lastModified,
                feedReadAt = readAt,
            )
        } ?: insertShow(show.copy(feedReadAt = readAt, joinedAt = readAt))
        // The move isn't the user's choice: updatedAt stays, so it can't win over a newer Follow from another phone.
        if (follow.feedUrl != show.feedUrl) upsertFollow(follow.copy(feedUrl = show.feedUrl))
        val inFeed = HashSet<String>()
        episodes.forEach { episode ->
            if (!inFeed.add(episode.itemKey)) return@forEach
            val id = episodeIdOf(showId, episode.itemKey)
            if (id == null) {
                insertEpisode(episode.copy(showId = showId, listedAt = readAt, leftFeedAt = null))
            } else {
                updateEpisodeFromFeed(
                    id = id,
                    guid = episode.guid,
                    title = episode.title,
                    publishedAt = episode.publishedAt,
                    lengthMillis = episode.lengthMillis,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    audioSize = episode.audioSize,
                    audioType = episode.audioType,
                    imageUrl = episode.imageUrl,
                )
            }
        }
        getEpisodesInFeed(showId).forEach { if (it.itemKey !in inFeed) setLeftFeedAt(it.id, readAt) }
        return showId
    }

    /** Records a 304 of the show's feed, from [feedUrl], while its follow is On. No episode changes. */
    @Transaction
    suspend fun applyFeedNotModified(directoryId: String, feedUrl: String, readAt: Long) {
        val follow = getFollow(directoryId)?.takeIf { it.followed } ?: return
        if (setFeedRead(directoryId, feedUrl, readAt) > 0 && follow.feedUrl != feedUrl) {
            upsertFollow(follow.copy(feedUrl = feedUrl))
        }
    }
}

/** The progress of the episodes. */
@Dao
internal interface ProgressDao {
    @Query("SELECT EXISTS(SELECT 1 FROM episodes WHERE id = :episodeId)")
    suspend fun hasEpisode(episodeId: Long): Boolean

    @Upsert
    suspend fun upsert(progress: ProgressEntity)
}

internal data class OwnerRow(val ownerKey: String, val name: String?)

internal data class ShowRow(
    val id: Long,
    val directoryId: String,
    val title: String,
    val ownerKey: String,
    val ownerName: String?,
    val followed: Boolean,
)

internal data class EpisodeRow(
    val id: Long,
    val showId: Long,
    val itemKey: String,
    val title: String?,
    val publishedAt: Long?,
    val lengthMillis: Long?,
    val description: String?,
    val audioUrl: String,
    val audioSize: Long?,
    val positionMillis: Long?,
    val played: Boolean?,
    val playerLengthMillis: Long?,
    val lastPlayedAt: Long?,
    val leftFeedAt: Long?,
)

internal data class FollowedFeedRow(
    val directoryId: String,
    val feedUrl: String,
    val showId: Long?,
    val etag: String?,
    val lastModified: String?,
)

internal data class EpisodeKeyRow(val id: Long, val itemKey: String)

internal data class ArtworkSourceRow(
    val episodeId: Long,
    val showId: Long,
    val itemKey: String,
    val feedUrl: String,
    val episodeImageUrl: String?,
    val showImageUrl: String?,
)
