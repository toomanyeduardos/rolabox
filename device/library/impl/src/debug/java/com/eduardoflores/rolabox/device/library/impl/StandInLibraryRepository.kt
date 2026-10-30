// ADR-024 rule 2 keeps MediaStore in :device:library:local:impl, which library/03 creates. This debug-only
// stand-in reads it from here until then, and library/03 deletes it.
@file:Suppress("MediaStoreOutsideLibrary")

package com.eduardoflores.rolabox.device.library.impl

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore.Audio.Media
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
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Where `pushAudioFixtures` copies the fixture sets, and a folder of your own music with `-Pset=<folder>`. */
private const val FIXTURES_PATH = "Music/RolaboxFixtures/"

/**
 * The debug build's library: the files `pushAudioFixtures` copied onto the phone, as MediaStore reads them,
 * with their `content://` URIs. TEMPORARY: it stands in for the index until library/03, so that playback/02 has
 * real files to play.
 *
 * Debug builds only. It is in `src/debug`, with its binding and the audio permission, so a release build doesn't
 * contain it: the release build's `LibraryModule`, in `src/release`, binds [InMemoryLibraryRepository] instead.
 *
 * Every read queries MediaStore again, so a push shows on the next screen. Without the audio permission
 * MediaStore answers with nothing, and the library is empty. The orders are [InMemoryLibraryRepository]'s.
 */
internal class StandInLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {
    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> = read { it.artists.right() }

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> = read { it.albums.right() }

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> = read { it.songs.right() }

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> = read { files ->
        if (files.artists.any { it.id == artistId }) {
            files.albums.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        }
    }

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> = read { files ->
        if (files.artists.any { it.id == artistId }) {
            files.songs.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        }
    }

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> = read { files ->
        if (files.albums.any { it.id == albumId }) {
            files.songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }.right()
        } else {
            LibraryError.AlbumNotFound(albumId).left()
        }
    }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> =
        load().artists.firstOrNull { it.id == id }?.right() ?: LibraryError.ArtistNotFound(id).left()

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> =
        load().albums.firstOrNull { it.id == id }?.right() ?: LibraryError.AlbumNotFound(id).left()

    private fun <T> read(select: (Files) -> Either<LibraryError, T>): Flow<Either<LibraryError, T>> =
        flow { emit(select(query(context.contentResolver))) }.flowOn(ioDispatcher)

    private suspend fun load(): Files = withContext(ioDispatcher) { query(context.contentResolver) }
}

/** The files under [FIXTURES_PATH], sorted as the library's `:api` says. */
private class Files(songs: List<Song>, albums: List<Album>, artists: List<Artist>) {
    val songs = songs.sortedBy { it.title }
    val albums = albums.sortedWith(compareBy({ it.year }, { it.title }))
    val artists = artists.sortedBy { it.name }
}

private val Columns = arrayOf(
    Media._ID,
    Media.TITLE,
    Media.ARTIST,
    Media.ARTIST_ID,
    Media.ALBUM,
    Media.ALBUM_ID,
    Media.TRACK,
    Media.YEAR,
    Media.DURATION,
)

private fun query(resolver: ContentResolver): Files {
    val songs = mutableListOf<Song>()
    val albums = linkedMapOf<AlbumId, Album>()
    val artists = linkedMapOf<ArtistId, Artist>()
    resolver.query(
        Media.EXTERNAL_CONTENT_URI,
        Columns,
        "${Media.RELATIVE_PATH} LIKE ?",
        arrayOf("$FIXTURES_PATH%"),
        null,
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.long(Media._ID)
            val artist = Artist(ArtistId(cursor.long(Media.ARTIST_ID)), cursor.text(Media.ARTIST))
            val album =
                Album(AlbumId(cursor.long(Media.ALBUM_ID)), artist.id, cursor.text(Media.ALBUM), cursor.int(Media.YEAR))
            artists.putIfAbsent(artist.id, artist)
            albums.putIfAbsent(album.id, album)
            songs += Song(
                id = SongId(id),
                albumId = album.id,
                artistId = artist.id,
                title = cursor.text(Media.TITLE),
                // MediaStore's track is the disc times 1000 plus the track, so an album on two discs keeps its order.
                trackNumber = cursor.int(Media.TRACK),
                duration = cursor.long(Media.DURATION).milliseconds,
                uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id).toString(),
            )
        }
    }
    return Files(songs, albums.values.toList(), artists.values.toList())
}

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()
