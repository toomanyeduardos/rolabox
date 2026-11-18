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
private const val EPISODES = "SELECT e.id AS id, e.showId AS showId, e.title AS title, " +
    "e.publishedAt AS publishedAt, e.lengthMillis AS lengthMillis, e.description AS description, " +
    "e.audioUrl AS audioUrl, e.audioSize AS audioSize, p.positionMillis AS positionMillis, p.played AS played, " +
    "p.playerLengthMillis AS playerLengthMillis, p.lastPlayedAt AS lastPlayedAt " +
    "FROM episodes e LEFT JOIN progress p ON p.episodeId = e.id"

/** The shows, their episodes and the follows. */
@Suppress("TooManyFunctions") // One interface for the part's lists, and the steps of its two transactions.
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
)

internal data class ArtworkSourceRow(
    val episodeId: Long,
    val showId: Long,
    val itemKey: String,
    val feedUrl: String,
    val episodeImageUrl: String?,
    val showImageUrl: String?,
)
