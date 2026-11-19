package com.eduardoflores.rolabox.device.podcasts.local.impl

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Every table is the user's data (ADR-031, rule 5): a change to any of them has a migration tested against the
// exported schema (ADR-024, rule 10). Times are milliseconds since the epoch.

/**
 * A show of the library. [directoryId] is its identity across phones, and [ownerKey] the grouping key of
 * [ownerName], empty for a show with no owner. [titleSortKey] and [ownerSortKey] are what the lists sort by, ignoring
 * case and accents, as the part's `:impl` gives them. The feed's [etag], [lastModified] and [feedReadAt] are written
 * by the follow check (podcasts/09), and [imageUrl] by the feed.
 */
@Entity(tableName = "shows", indices = [Index(value = ["directoryId"], unique = true), Index("ownerKey")])
internal data class ShowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val directoryId: String,
    val feedUrl: String,
    val title: String,
    val titleSortKey: String,
    val ownerName: String?,
    val ownerKey: String,
    val ownerSortKey: String?,
    val imageUrl: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val feedReadAt: Long? = null,
    val joinedAt: Long,
)

/**
 * An episode, as its feed says. [itemKey] identifies it within its show: its GUID, or its enclosure URL when it has
 * none. [lengthMillis] is the feed's length. [listedAt] is when it was first stored, and [leftFeedAt] when a check last
 * found the feed without it, `null` while it is in it (ADR-031, The database).
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(entity = ShowEntity::class, parentColumns = ["id"], childColumns = ["showId"])],
    indices = [Index(value = ["showId", "itemKey"], unique = true)],
)
internal data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val showId: Long,
    val itemKey: String,
    val guid: String?,
    val title: String?,
    val publishedAt: Long?,
    val lengthMillis: Long?,
    val description: String?,
    val audioUrl: String,
    val audioSize: Long?,
    val audioType: String?,
    val imageUrl: String? = null,
    val listedAt: Long,
    val leftFeedAt: Long? = null,
)

/**
 * Where an episode is, apart from the episode, so reading its feed again never touches it. [playerLengthMillis] is
 * the length the player knew, which replaces the feed's.
 */
@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(entity = EpisodeEntity::class, parentColumns = ["id"], childColumns = ["episodeId"])],
)
internal data class ProgressEntity(
    @PrimaryKey val episodeId: Long,
    val positionMillis: Long,
    val played: Boolean,
    val playerLengthMillis: Long?,
    val lastPlayedAt: Long,
)

/**
 * The Follow switch of a show, by its directory id, apart from the shows: a follow can arrive by sync before its show,
 * and an unfollow is kept as the record that wins over an older follow (ADR-031, Syncing follows).
 */
@Entity(tableName = "follows")
internal data class FollowEntity(
    @PrimaryKey val directoryId: String,
    val feedUrl: String,
    val followed: Boolean,
    val updatedAt: Long,
)
