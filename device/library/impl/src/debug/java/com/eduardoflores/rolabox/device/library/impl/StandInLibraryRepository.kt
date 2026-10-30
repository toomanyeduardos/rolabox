package com.eduardoflores.rolabox.device.library.impl

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Where `pushAudioFixtures` copies the sets for this library, in the app's own files. */
private const val FIXTURES_FOLDER = "RolaboxFixtures"

private val AudioExtensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav")

/** MediaStore's numbering of a track on a disc, so an album on two discs keeps its order. */
private const val DISC_FACTOR = 1000

/**
 * The debug build's library: the files `pushAudioFixtures` copied into the app's own files, with their
 * `file://` URIs and the tags the platform reads from them. TEMPORARY: it stands in for the index until
 * library/03, so that playback/02 has real files to play. An app needs no permission to read its own files, and
 * MediaStore and the storage permission stay the library's local side's (ADR-024, rules 2 and 3).
 *
 * Debug builds only. It is in `src/debug`, with its binding, so a release build doesn't contain it: the release
 * build's `LibraryModule`, in `src/release`, binds [InMemoryLibraryRepository] instead.
 *
 * Every read lists the folder again, so a push shows on the next screen, and the tags are read again only when a
 * file changed. The orders are [InMemoryLibraryRepository]'s.
 */
@Singleton
internal class StandInLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {
    @Volatile
    private var loaded: Pair<List<Pair<String, Long>>, Library>? = null

    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> = read { it.artists.right() }

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> = read { it.albums.right() }

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> = read { it.songs.right() }

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> = read { library ->
        if (library.artists.any { it.id == artistId }) {
            library.albums.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        }
    }

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> = read { library ->
        if (library.artists.any { it.id == artistId }) {
            library.songs.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        }
    }

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> = read { library ->
        if (library.albums.any { it.id == albumId }) {
            library.songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }.right()
        } else {
            LibraryError.AlbumNotFound(albumId).left()
        }
    }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> =
        load().artists.firstOrNull { it.id == id }?.right() ?: LibraryError.ArtistNotFound(id).left()

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> =
        load().albums.firstOrNull { it.id == id }?.right() ?: LibraryError.AlbumNotFound(id).left()

    private fun <T> read(select: (Library) -> Either<LibraryError, T>): Flow<Either<LibraryError, T>> =
        flow { emit(select(listFiles())) }.flowOn(ioDispatcher)

    private suspend fun load(): Library = withContext(ioDispatcher) { listFiles() }

    private fun listFiles(): Library {
        val files = File(context.filesDir, FIXTURES_FOLDER).walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AudioExtensions }
            .sortedBy { it.path }
            .toList()
        val signature = files.map { it.path to it.lastModified() }
        loaded?.takeIf { it.first == signature }?.let { return it.second }
        return Library.of(files.map(::readTags)).also { loaded = signature to it }
    }
}

/** What a file says about itself, with its name and folder in place of the tags it doesn't have. */
private class Tagged(val file: File, tags: (Int) -> String?) {
    private val folder = file.parentFile?.name.orEmpty()
    private val track = tags(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).leadingNumber()
    private val disc = tags(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).leadingNumber()

    val title = tags(MediaMetadataRetriever.METADATA_KEY_TITLE).orIfBlank(file.nameWithoutExtension)
    val artist = tags(MediaMetadataRetriever.METADATA_KEY_ARTIST).orIfBlank(folder)
    val album = tags(MediaMetadataRetriever.METADATA_KEY_ALBUM).orIfBlank(folder)
    val trackNumber = disc * DISC_FACTOR + track
    val year = tags(MediaMetadataRetriever.METADATA_KEY_YEAR).leadingNumber()
    val durationMs = tags(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
}

/** The songs, albums and artists of the files, with ids in the order of the files, sorted as the `:api` says. */
private class Library(songs: List<Song>, albums: List<Album>, artists: List<Artist>) {
    val songs = songs.sortedBy { it.title }
    val albums = albums.sortedWith(compareBy({ it.year }, { it.title }))
    val artists = artists.sortedBy { it.name }

    companion object {
        fun of(files: List<Tagged>): Library {
            val artists = linkedMapOf<String, Artist>()
            val albums = linkedMapOf<Pair<String, String>, Album>()
            val songs = files.mapIndexed { index, tagged ->
                val artist = artists.getOrPut(tagged.artist) { Artist(ArtistId(artists.size + 1L), tagged.artist) }
                val album = albums.getOrPut(tagged.artist to tagged.album) {
                    Album(AlbumId(albums.size + 1L), artist.id, tagged.album, tagged.year)
                }
                Song(
                    id = SongId(index + 1L),
                    albumId = album.id,
                    artistId = artist.id,
                    title = tagged.title,
                    trackNumber = tagged.trackNumber,
                    duration = tagged.durationMs.milliseconds,
                    uri = Uri.fromFile(tagged.file).toString(),
                )
            }
            return Library(songs, albums.values.toList(), artists.values.toList())
        }
    }
}

// MediaMetadataRetriever answers a file it can't read with a bare RuntimeException, so that is the one named here
// (ADR-007, rule 4). A broken fixture stays in the list, as a file the player has to fail on (playback/04).
@Suppress("TooGenericExceptionCaught")
private fun readTags(file: File): Tagged {
    val tags: (Int) -> String? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            val keys = listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE,
                MediaMetadataRetriever.METADATA_KEY_ARTIST,
                MediaMetadataRetriever.METADATA_KEY_ALBUM,
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
                MediaMetadataRetriever.METADATA_KEY_YEAR,
                MediaMetadataRetriever.METADATA_KEY_DURATION,
            ).associateWith { retriever.extractMetadata(it) }
            keys::get
        }
    } catch (_: RuntimeException) {
        { null }
    }
    return Tagged(file, tags)
}

private fun String?.orIfBlank(other: String): String = this?.takeIf { it.isNotBlank() } ?: other

/** The `3` of a track's `3/12`, or 0 when there is none. */
private fun String?.leadingNumber(): Int = this?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
