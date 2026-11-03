package com.eduardoflores.rolabox.device.library.impl.artwork

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How many covers stay in memory: the song playing and the ones around it. */
internal const val CACHED_ARTWORK = 4

/**
 * The covers, through the index, which knows where each song's file is, the system's thumbnail of it (ADR-026), and
 * the record of its album's downloaded cover (ADR-027). What is on the phone always wins: each load asks for the
 * thumbnail first, and one that finds it records the album as on the phone, deleting a downloaded file.
 *
 * The last few are kept in memory, each for one version of its file and of its album's record, so a file that
 * changed, or a cover that arrived, loads again. A song with no cover is kept too. An error isn't kept.
 */
@Singleton
internal class CachedArtworkRepository @Inject constructor(
    private val index: LibraryIndexLocalDataSource,
    private val artwork: ArtworkLocalDataSource,
    private val albums: AlbumArtworkLocalDataSource,
    private val wanted: WantedAlbum,
    private val permit: DownloadPermit,
    private val scheduler: ArtworkDownloadScheduler,
) : ArtworkRepository {
    private val mutex = Mutex()

    // In order of use, the least recent first.
    private val cache = object : LinkedHashMap<Version, Loaded>(CACHED_ARTWORK, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Version, Loaded>) = size > CACHED_ARTWORK
    }

    override suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?> =
        artworkOf(ref, wantIt = false).first()

    override fun observeArtwork(ref: ArtworkRef): Flow<Either<LibraryError, ArtworkImage?>> =
        artworkOf(ref, wantIt = true)

    // What is shown is observed, so an album observed with no cover is the one downloaded next (ADR-027, The work).
    private fun artworkOf(ref: ArtworkRef, wantIt: Boolean): Flow<Either<LibraryError, ArtworkImage?>> = flow {
        val (file, album) = either {
            val file = index.getSongFile(ref.song).bind() ?: return@either null
            file to albums.getAlbumOfSong(ref.song).bind()
        }.getOrElse { error ->
            emit(error.left())
            return@flow
        } ?: run {
            // A song no longer in the index has no cover.
            emit(null.right())
            return@flow
        }
        if (album == null) {
            emit(either { load(file, null, null) })
            return@flow
        }
        // A Left ends the flow, as everywhere in the library.
        val loads = albums.observeRecord(album.key).distinctUntilChanged().map { observed ->
            either {
                val record = observed.bind()
                load(file, album, record).also { image -> if (wantIt && image == null) want(file, album, record) }
            }
        }
        emitAll(
            loads.transformWhile {
                emit(it)
                it.isRight()
            }.distinctUntilChanged(),
        )
    }

    override fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>> = albums.observeDownloadEnabled()

    override suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit> =
        albums.setDownloadEnabled(enabled)

    private suspend fun Raise<LibraryError>.load(
        file: SongFile,
        album: ArtworkAlbum?,
        record: ArtworkRecord?,
    ): ArtworkImage? {
        val version = Version(file, record)
        mutex.withLock { cache[version] }?.let { return it.image }
        val onPhone = artwork.loadArtwork(file.uri).bind()
        val image = when {
            onPhone != null -> onPhone.also { if (album != null) recordOnPhone(album.key, record) }
            album == null -> null
            record is ArtworkRecord.Found -> downloaded(album.key, record)
            record == ArtworkRecord.OnPhone -> null.also { checkStillOnPhone(file, album) }
            else -> null
        }
        mutex.withLock { cache[version] = Loaded(image) }
        return image
    }

    // A cover on the phone replaces a downloaded one, also when it appears later (ADR-027, rule 1).
    private suspend fun Raise<LibraryError>.recordOnPhone(key: String, record: ArtworkRecord?) {
        if (record == ArtworkRecord.OnPhone) return
        if (record is ArtworkRecord.Found) albums.deleteCover(record.fileName).bind()
        albums.setRecord(key, ArtworkRecord.OnPhone).bind()
    }

    // A record that says found, whose file is gone, is queued again.
    private suspend fun Raise<LibraryError>.downloaded(key: String, record: ArtworkRecord.Found): ArtworkImage? =
        albums.loadCover(record.fileName).bind()
            ?: null.also { albums.setRecord(key, ArtworkRecord.Pending).bind() }

    // This song has no cover of its own, which doesn't mean its album has none: another of its songs can. Only when the
    // song that stands for the album has none either is the album queued again (ADR-027, When).
    private suspend fun Raise<LibraryError>.checkStillOnPhone(file: SongFile, album: ArtworkAlbum) {
        val albumHasOne = album.artworkSongUri != file.uri && artwork.loadArtwork(album.artworkSongUri).bind() != null
        if (!albumHasOne) albums.setRecord(album.key, ArtworkRecord.Pending).bind()
    }

    /**
     * Makes [album] the one downloaded next, when it is queued, or has no record yet and no cover on the phone: it is
     * queued then. An album found, on the phone, or not found and waiting its turn is left as it is (ADR-027, rule 5).
     * The work is enqueued only while downloading is allowed, and a request in flight isn't cancelled.
     */
    private suspend fun Raise<LibraryError>.want(file: SongFile, album: ArtworkAlbum, record: ArtworkRecord?) {
        if (album.toQuery() == null || (record != null && record != ArtworkRecord.Pending)) return
        if (record == null) {
            // The pass hasn't looked at this album yet, and another of its songs can have the cover.
            val elsewhere = album.artworkSongUri != file.uri && artwork.loadArtwork(album.artworkSongUri).bind() != null
            albums.setRecord(album.key, if (elsewhere) ArtworkRecord.OnPhone else ArtworkRecord.Pending).bind()
            if (elsewhere) return
        }
        wanted.want(album.key)
        if (permit.observe().first()) scheduler.enqueue()
    }

    /** A song's file, as the index had it, and its album's record, which together say what a load returns. */
    private data class Version(val file: SongFile, val record: ArtworkRecord?)

    private class Loaded(val image: ArtworkImage?)
}

private const val LOAD_FACTOR = 0.75f
