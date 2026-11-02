package com.eduardoflores.rolabox.device.playback.local.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first

/**
 * The point as one value of [PreferencesStore], so a write is never half of one. The store can't remove a key, so an
 * empty value is no point. A value that can't be read, from an older format, is no point either: the worst that loses
 * is where the user was.
 */
internal class DefaultPlaybackLocalDataSource @Inject constructor(private val preferences: PreferencesStore) :
    PlaybackLocalDataSource {
    override suspend fun resumePoint(): Either<StorageError, ResumePoint?> =
        preferences.observeString(RESUME_POINT_KEY).first().map { it?.toResumePoint() }

    override suspend fun store(point: ResumePoint): Either<StorageError, Unit> =
        preferences.setString(RESUME_POINT_KEY, point.encoded())

    override suspend fun clear(): Either<StorageError, Unit> = preferences.setString(RESUME_POINT_KEY, "")
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
}

private fun String.toSource(): PlaybackSource? = if (this == ALL_SONGS) {
    PlaybackSource.AllSongs
} else {
    val id = substringAfter(':', "").toLongOrNull()
    when {
        id == null -> null
        startsWith("$ARTIST:") -> PlaybackSource.ByArtist(ArtistId(id))
        startsWith("$ALBUM:") -> PlaybackSource.ByAlbum(AlbumId(id))
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
private const val SEPARATOR = "|"
private const val PART_COUNT = 4
private const val ALL_SONGS = "all"
private const val ARTIST = "artist"
private const val ALBUM = "album"
private const val SONG = "song"
