package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The albums of the index in [albums], each song's album in [albumOfSong], the records by album key in [records], the
 * saved covers by file name in [files], and the setting in [downloadEnabled]. A cover's file decodes to the image in
 * [images], or to none. [writeError] fails every write.
 */
@Suppress("TooManyFunctions") // One function for each of the store's.
class FakeAlbumArtworkLocalDataSource : AlbumArtworkLocalDataSource {
    /** The albums of the index, without their records, which are in [records]. */
    var albums: List<ArtworkAlbum> = emptyList()
    val albumOfSong = mutableMapOf<SongId, String>()
    val records = MutableStateFlow<Map<String, ArtworkRecord>>(emptyMap())
    val files = mutableMapOf<String, ByteArray>()
    val images = mutableMapOf<String, ArtworkImage>()
    val downloadEnabled = MutableStateFlow(true)
    var writeError: LibraryError? = null
    private var saved = 0

    override suspend fun getAlbums(): Either<LibraryError, List<ArtworkAlbum>> = albums.map(::withRecord).right()

    override suspend fun getAlbum(key: String): Either<LibraryError, ArtworkAlbum?> =
        albums.firstOrNull { it.key == key }?.let(::withRecord).right()

    override suspend fun getAlbumOfSong(song: SongId): Either<LibraryError, ArtworkAlbum?> =
        albumOfSong[song]?.let { key -> albums.firstOrNull { it.key == key } }?.let(::withRecord).right()

    override fun observeRecord(key: String): Flow<Either<LibraryError, ArtworkRecord?>> =
        records.map { it[key].right() }

    override suspend fun setRecord(key: String, record: ArtworkRecord): Either<LibraryError, Unit> = write {
        records.value = records.value + (key to record)
    }

    override suspend fun saveCover(
        key: String,
        sourceId: String,
        bytes: ByteArray,
        foundAt: Long,
    ): Either<LibraryError, Unit> = write {
        val fileName = "cover-${++saved}.jpg"
        files[fileName] = bytes
        records.value = records.value + (key to ArtworkRecord.Found(fileName, sourceId, foundAt))
    }

    override suspend fun loadCover(fileName: String): Either<LibraryError, ArtworkImage?> =
        (if (fileName in files) images[fileName] else null).right()

    override suspend fun deleteCover(fileName: String): Either<LibraryError, Unit> = write { files -= fileName }

    override fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>> = downloadEnabled.map { it.right() }

    override suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit> =
        write { downloadEnabled.value = enabled }

    private fun withRecord(album: ArtworkAlbum) = album.copy(record = records.value[album.key])

    private fun write(change: () -> Unit): Either<LibraryError, Unit> = writeError?.left() ?: change().right()
}
