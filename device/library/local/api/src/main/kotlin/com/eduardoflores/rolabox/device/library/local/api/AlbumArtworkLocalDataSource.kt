package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlinx.coroutines.flow.Flow

/**
 * What the library keeps of the covers it downloads (ADR-027): a record per album, which is the user's data and
 * survives rescans, the images in the app's own files, and whether downloading is allowed. A `Left` ends a flow.
 */
@Suppress("TooManyFunctions") // The record, the files and the setting are one store, in one database and one folder.
interface AlbumArtworkLocalDataSource {
    /** Every album in the index, with its record, for the pass after a scan and the download. */
    suspend fun getAlbums(): Either<LibraryError, List<ArtworkAlbum>>

    /** The album of [key] in the index, with its record, or `null` when it isn't in the index any more. */
    suspend fun getAlbum(key: String): Either<LibraryError, ArtworkAlbum?>

    /** The album [song] is on, with its record, or `null` when the song isn't in the index. */
    suspend fun getAlbumOfSong(song: SongId): Either<LibraryError, ArtworkAlbum?>

    /** The record of the album of [key], and again whenever it changes. `null` when there is none. */
    fun observeRecord(key: String): Flow<Either<LibraryError, ArtworkRecord?>>

    /** Replaces the record of the album of [key]. A [ArtworkRecord.Found] is written by [saveCover] only. */
    suspend fun setRecord(key: String, record: ArtworkRecord): Either<LibraryError, Unit>

    /**
     * Keeps [bytes], a cover the source served, as the album's image, and then records it found. The file is complete
     * before the record says so.
     */
    suspend fun saveCover(key: String, sourceId: String, bytes: ByteArray, foundAt: Long): Either<LibraryError, Unit>

    /**
     * The image of [fileName], at most [ArtworkLocalDataSource.ARTWORK_SIZE] pixels on its longest side, or `null` when
     * the file is gone or isn't an image.
     */
    suspend fun loadCover(fileName: String): Either<LibraryError, ArtworkImage?>

    /** Deletes the image of [fileName]. A file that is already gone is deleted. */
    suspend fun deleteCover(fileName: String): Either<LibraryError, Unit>

    /** Whether missing covers are downloaded. On until the user turns it off. Kept on this phone, and not synced. */
    fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>>

    suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit>
}

/**
 * An album as the download of covers sees it. [key] is its grouping key, which the record is kept by, since its id is
 * a row of a cache table. [artistName] is `null` for Various Artists, when [isVarious] is set, and for the unknown
 * artist. [artworkSongUri] is the file of the song whose cover stands for the album (ADR-026). [songArtists],
 * [highestTrackNumber] and [year] are what the match reads of its songs (ADR-027, How a match is judged): their
 * distinct artists, their highest track number, and the one year they share, `null` when they have none or disagree.
 */
data class ArtworkAlbum(
    val key: String,
    val title: String?,
    val artistName: String?,
    val isVarious: Boolean,
    val artworkSongUri: String,
    val record: ArtworkRecord?,
    val songArtists: List<String> = emptyList(),
    val highestTrackNumber: Int? = null,
    val year: Int? = null,
)

/** What is known of an album's cover beyond the phone (ADR-027, What is recorded). */
sealed interface ArtworkRecord {
    /** Queued, and never answered. */
    data object Pending : ArtworkRecord

    /** Downloaded, and kept in [fileName]. [sourceId] is the source's id of the album, [foundAt] in epoch millis. */
    data class Found(val fileName: String, val sourceId: String, val foundAt: Long) : ArtworkRecord

    /** The source answered [attempts] times with no cover, the last at [lastAttemptAt], in epoch millis. */
    data class NotFound(val attempts: Int, val lastAttemptAt: Long) : ArtworkRecord

    /** The phone has a cover for the album, so nothing is downloaded. */
    data object OnPhone : ArtworkRecord
}
