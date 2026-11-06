package com.eduardoflores.rolabox.device.playback.local.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.local.api.PlayOrder
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first

/**
 * The point as one value of [PreferencesStore], so a write is never half of one. The store can't remove a key, so an
 * empty value is no point. A value that can't be read, from an older format, is no point either: the worst that loses
 * is where the user was. The order is a value of its own, under its own key, and the same holds for it: the worst an
 * order that can't be read loses is the order, which is made again (ADR-028).
 */
internal class DefaultPlaybackLocalDataSource @Inject constructor(private val preferences: PreferencesStore) :
    PlaybackLocalDataSource {
    override suspend fun resumePoint(): Either<StorageError, ResumePoint?> =
        preferences.observeString(RESUME_POINT_KEY).first().map { it?.toResumePoint() }

    override suspend fun store(point: ResumePoint): Either<StorageError, Unit> =
        preferences.setString(RESUME_POINT_KEY, point.encoded())

    override suspend fun playOrder(): Either<StorageError, PlayOrder?> =
        preferences.observeString(PLAY_ORDER_KEY).first().map { it?.toPlayOrder() }

    override suspend fun storeOrder(order: PlayOrder?): Either<StorageError, Unit> =
        preferences.setString(PLAY_ORDER_KEY, order?.encoded().orEmpty())

    override suspend fun clear(): Either<StorageError, Unit> =
        preferences.updateStrings(setOf(RESUME_POINT_KEY, PLAY_ORDER_KEY)) {
            mapOf(RESUME_POINT_KEY to "", PLAY_ORDER_KEY to "")
        }
}

// What shuffled it, and then the items, each as the point names one.
private fun PlayOrder.encoded(): String =
    (listOf(shuffledBy.encoded()) + items.map { it.encoded() }).joinToString(SEPARATOR)

private fun String.toPlayOrder(): PlayOrder? {
    val parts = split(SEPARATOR)
    val shuffledBy = parts.first().toShuffledBy()
    val items = parts.drop(1).mapNotNull { it.toItem() }
    val readable = items.isNotEmpty() && items.size == parts.size - 1 && items.distinct().size == items.size
    return if (shuffledBy != null && readable) PlayOrder(items, shuffledBy) else null
}

// Written out, not the enum's names, so renaming one in code never loses what was stored.
private fun ShuffledBy.encoded(): String = when (this) {
    ShuffledBy.Songs -> SHUFFLED_BY_SONGS
    ShuffledBy.Albums -> SHUFFLED_BY_ALBUMS
    ShuffledBy.ShuffleSongs -> SHUFFLED_BY_SHUFFLE_SONGS
}

private fun String.toShuffledBy(): ShuffledBy? = when (this) {
    SHUFFLED_BY_SONGS -> ShuffledBy.Songs
    SHUFFLED_BY_ALBUMS -> ShuffledBy.Albums
    SHUFFLED_BY_SHUFFLE_SONGS -> ShuffledBy.ShuffleSongs
    else -> null
}

private fun ResumePoint.encoded(): String =
    listOf(source.encoded(), item.encoded(), index, position.inWholeMilliseconds).joinToString(SEPARATOR)

private fun String.toResumePoint(): ResumePoint? = split(SEPARATOR).takeIf { it.size == PART_COUNT }?.let { parts ->
    val source = parts[0].toSource() ?: return null
    val item = parts[1].toItem() ?: return null
    val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val position = parts[PART_COUNT - 1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    ResumePoint(source, item, index, position.milliseconds)
}

private fun PlaybackSource.encoded(): String = when (this) {
    PlaybackSource.AllSongs -> ALL_SONGS
    is PlaybackSource.ByArtist -> "$ARTIST:${artistId.value}"
    is PlaybackSource.ByAlbum -> "$ALBUM:${albumId.value}"
    is PlaybackSource.ByGenreAndArtist -> "$GENRE_ARTIST:${genreId.value}:${artistId.value}"
    is PlaybackSource.ByGenreAndAlbum -> "$GENRE_ALBUM:${genreId.value}:${albumId.value}"
}

// The kind, then its ids: one, or a genre's and the artist's or album's in it.
private fun String.toSource(): PlaybackSource? {
    val kind = substringBefore(':')
    val parts = split(':').drop(1)
    val ids = parts.mapNotNull { it.toLongOrNull() }.takeIf { it.size == parts.size }.orEmpty()
    return when {
        this == ALL_SONGS -> PlaybackSource.AllSongs
        kind == ARTIST && ids.size == 1 -> PlaybackSource.ByArtist(ArtistId(ids[0]))
        kind == ALBUM && ids.size == 1 -> PlaybackSource.ByAlbum(AlbumId(ids[0]))
        kind == GENRE_ARTIST && ids.size == 2 -> PlaybackSource.ByGenreAndArtist(GenreId(ids[0]), ArtistId(ids[1]))
        kind == GENRE_ALBUM && ids.size == 2 -> PlaybackSource.ByGenreAndAlbum(GenreId(ids[0]), AlbumId(ids[1]))
        else -> null
    }
}

// Named by its kind, so an episode gets its own (ADR-023, rule 4).
private fun PlaybackItemId.encoded(): String = when (this) {
    is PlaybackItemId.Song -> "$SONG:${id.value}"
}

private fun String.toItem(): PlaybackItemId? {
    val id = substringAfter(':', "").toLongOrNull()
    return if (id != null && startsWith("$SONG:")) PlaybackItemId.Song(SongId(id)) else null
}

private const val RESUME_POINT_KEY = "playback_resume_point"
private const val PLAY_ORDER_KEY = "playback_play_order"
private const val SHUFFLED_BY_SONGS = "songs"
private const val SHUFFLED_BY_ALBUMS = "albums"
private const val SHUFFLED_BY_SHUFFLE_SONGS = "shuffle_songs"
private const val SEPARATOR = "|"
private const val PART_COUNT = 4
private const val ALL_SONGS = "all"
private const val ARTIST = "artist"
private const val ALBUM = "album"
private const val GENRE_ARTIST = "genre-artist"
private const val GENRE_ALBUM = "genre-album"
private const val SONG = "song"
