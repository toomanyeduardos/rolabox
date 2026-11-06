package com.eduardoflores.rolabox.device.library.impl.artwork

import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How long after each answer of no cover an album is tried again: a day, then a week. After the last, it isn't
 * (ADR-027, Retries).
 */
internal val RetryAfter: List<Duration> = listOf(1.days, 7.days)

/** How many answers of no cover an album gets before it is given up on. */
internal val MaxAttempts: Int = RetryAfter.size + 1

/**
 * What the source is asked for this album, or `null` when it is never asked: an album with no title, or by the
 * unknown artist (ADR-027, How a match is judged).
 */
internal fun ArtworkAlbum.toQuery(): CoverQuery? {
    val title = title
    val name = artistName
    val artist = when {
        isVarious -> CoverArtist.Various
        !name.isNullOrBlank() -> CoverArtist.Named(name)
        else -> null
    }
    return if (title.isNullOrBlank() || artist == null) {
        null
    } else {
        CoverQuery(title, artist, songArtists, highestTrackNumber, year)
    }
}

/** Whether the album is due a request at [nowMillis]: queued, or not found long enough ago and not given up on. */
internal fun ArtworkRecord?.isDueAt(nowMillis: Long): Boolean = when (this) {
    ArtworkRecord.Pending -> true

    is ArtworkRecord.NotFound ->
        attempts < MaxAttempts &&
            nowMillis - lastAttemptAt >= RetryAfter[(attempts - 1).coerceIn(RetryAfter.indices)].inWholeMilliseconds

    null, is ArtworkRecord.Found, ArtworkRecord.OnPhone -> false
}

/**
 * Whether a request may be made now: the setting is on and offline mode isn't chosen (ADR-027, ADR-008 rule 6). A
 * setting that can't be read allows nothing, since sending is what it is there to stop.
 */
internal class DownloadPermit @Inject constructor(
    private val artwork: AlbumArtworkLocalDataSource,
    private val userData: UserDataRepository,
) {
    fun observe(): Flow<Boolean> = combine(
        artwork.observeDownloadEnabled(),
        userData.observeOfflineModeChosen(),
    ) { enabled, offline ->
        enabled.fold(ifLeft = { false }, ifRight = { it }) && offline.fold(ifLeft = { false }, ifRight = { !it })
    }.distinctUntilChanged()
}
