package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An index in memory, which finds albums and artists by their keys as the real one does and keeps every batch it
 * was given in [batches]. The orders are the real index's only in the tests of `:local:impl`: here, insertion's.
 */
@Suppress("TooManyFunctions") // One for each function of the index.
class FakeLibraryIndexLocalDataSource : LibraryIndexLocalDataSource {
    private data class Index(
        val songs: Map<SongId, IndexedSong> = emptyMap(),
        val artists: Map<String, Artist> = emptyMap(),
        val albums: Map<String, Album> = emptyMap(),
    )

    private val state = MutableStateFlow(Index())
    private var nextId = 1L

    val batches = mutableListOf<IndexBatch>()
    var savedScanState: List<VolumeScanState>? = null
        private set

    /** Makes every write fail with this. */
    var writeError: LibraryError? = null

    val indexed: List<IndexedSong> get() = state.value.songs.values.toList()

    /** The songs as the `:api` shows them. */
    val songs: List<Song> get() = state.value.songList()

    val artists: List<Artist> get() = state.value.artists.values.toList()

    val albums: List<Album> get() = state.value.albums.values.toList()

    private fun Index.songList(): List<Song> = songs.values.map { song ->
        Song(
            id = song.id,
            albumId = albums.getValue(song.albumKey).id,
            artistId = artists.getValue(song.artistKey).id,
            title = song.metadata.title.value,
            trackNumber = song.metadata.trackNumber.value,
            duration = song.metadata.durationMillis.value?.milliseconds ?: Duration.ZERO,
            uri = song.file.uri,
        )
    }

    private fun <T> read(select: Index.() -> T): Flow<Either<LibraryError, T>> = state.map { it.select().right() }

    override fun observeArtists() = read { artists.values.toList() }

    override fun observeAlbums() = read { albums.values.toList() }

    override fun observeSongs() = read { songList() }

    override fun observeArtist(id: ArtistId) = read { artists.values.firstOrNull { it.id == id } }

    override fun observeAlbum(id: AlbumId) = read { albums.values.firstOrNull { it.id == id } }

    override fun observeAlbumsByArtist(artistId: ArtistId) = read {
        val onSongs = songList().filter { it.artistId == artistId }.map { it.albumId }
        albums.values.filter { it.artistId == artistId || it.id in onSongs }
    }

    override fun observeSongsByArtist(artistId: ArtistId) = read {
        val ownAlbums = albums.values.filter { it.artistId == artistId }.map { it.id }
        songList().filter { it.artistId == artistId || it.albumId in ownAlbums }
    }

    override fun observeSongsByAlbum(albumId: AlbumId) = read { songList().filter { it.albumId == albumId } }

    override fun observeSongCount() = read { songs.size }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist?> =
        state.value.artists.values.firstOrNull { it.id == id }.right()

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album?> =
        state.value.albums.values.firstOrNull { it.id == id }.right()

    override suspend fun indexedSongs(): Either<LibraryError, List<IndexedSong>> = indexed.right()

    override suspend fun write(batch: IndexBatch): Either<LibraryError, Unit> {
        writeError?.let { return it.left() }
        batches += batch
        state.value = state.value.applying(batch)
        return Unit.right()
    }

    private fun Index.applying(batch: IndexBatch): Index {
        val songs = songs.toMutableMap()
        val artists = artists.toMutableMap()
        val albums = albums.toMutableMap()
        batch.writes.forEach { write ->
            val artistIdOf = { key: String, name: String? ->
                artists.getOrPut(key) { Artist(ArtistId(nextId++), name) }.id
            }
            artistIdOf(write.artist.key, write.artist.name)
            val albumArtist = artistIdOf(write.album.artist.key, write.album.artist.name)
            val album = albums[write.album.key]
            albums[write.album.key] = Album(
                id = album?.id ?: AlbumId(nextId++),
                artistId = albumArtist,
                title = write.album.title,
                year = write.album.year ?: album?.year,
            )
            val id = write.id ?: SongId(nextId++)
            songs[id] = IndexedSong(id, write.file, write.metadata, write.artist.key, write.album.key)
        }
        batch.removals.forEach(songs::remove)
        val usedAlbums = songs.values.mapTo(HashSet()) { it.albumKey }
        albums.keys.retainAll(usedAlbums)
        val albumArtists = albums.values.mapTo(HashSet()) { it.artistId }
        val songArtists = songs.values.mapTo(HashSet()) { it.artistKey }
        artists.entries.retainAll { (key, artist) -> key in songArtists || artist.id in albumArtists }
        return Index(songs, artists, albums)
    }

    override suspend fun saveScanState(volumes: List<VolumeScanState>): Either<LibraryError, Unit> {
        savedScanState = volumes
        return Unit.right()
    }
}
