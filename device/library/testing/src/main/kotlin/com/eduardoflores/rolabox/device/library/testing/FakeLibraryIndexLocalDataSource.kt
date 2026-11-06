package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.Genre
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.SongFile
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
        val genres: Map<String, Genre> = emptyMap(),
        val songGenres: Map<SongId, List<String>> = emptyMap(),
    )

    private val state = MutableStateFlow(Index())
    private var nextId = 1L

    val batches = mutableListOf<IndexBatch>()
    var savedScanState: List<VolumeScanState>? = null
        private set

    /** Makes every write fail with this. */
    var writeError: LibraryError? = null

    /** Makes every write fail once this many songs have been written, as a process killed by then would stop. */
    var failWritesAfter: Int? = null

    val indexed: List<IndexedSong> get() = state.value.songs.values.toList()

    /** The songs as the `:api` shows them. */
    val songs: List<Song> get() = state.value.songList()

    val artists: List<Artist> get() = state.value.artists.values.toList()

    val albums: List<Album> get() = state.value.albums.values.toList()

    val genres: List<Genre> get() = state.value.genres.values.toList()

    /** The names of the genres each song is in, `null` for Unknown Genre. */
    val genresOfSongs: Map<SongId, List<String?>>
        get() = state.value.run { songGenres.mapValues { (_, keys) -> keys.map { genres.getValue(it).name } } }

    private fun Index.songList(): List<Song> = songs.values.filterNot { it.isDuplicate }.map { song ->
        Song(
            id = song.id,
            albumId = albums.getValue(song.albumKey).id,
            artistId = artists.getValue(song.artistKey).id,
            title = song.metadata.title.value,
            trackNumber = song.metadata.trackNumber.value,
            discNumber = song.metadata.discNumber.value,
            duration = song.metadata.durationMillis.value?.milliseconds ?: Duration.ZERO,
            uri = song.file.uri,
            isProtected = song.isProtected,
        )
    }

    private fun <T> read(select: Index.() -> T): Flow<Either<LibraryError, T>> = state.map { it.select().right() }

    override fun observeArtists() = read {
        val onSongs = songs.values.mapTo(HashSet()) { it.artistKey }
        artists.filter { (key, artist) -> artist.name != null || artist.isVarious || key in onSongs }.values.toList()
    }

    override fun observeAlbums() = read { albums.values.toList() }

    override fun observeSongs() = read { songList() }

    override fun observeArtist(id: ArtistId) = read { artists.values.firstOrNull { it.id == id } }

    override fun observeAlbum(id: AlbumId) = read { albums.values.firstOrNull { it.id == id } }

    override fun observeAlbumsByArtist(artistId: ArtistId) = read {
        val onSongs = songList().filter { it.artistId == artistId }.map { it.albumId }
        albums.values.filter { it.title != null && (it.artistId == artistId || it.id in onSongs) }
    }

    override fun observeSongsByArtist(artistId: ArtistId) = read {
        val ownAlbums = albums.values.filter { it.artistId == artistId && it.title != null }.map { it.id }
        songList().filter { it.artistId == artistId || it.albumId in ownAlbums }
    }

    override fun observeSongsByAlbum(albumId: AlbumId) = read { songList().filter { it.albumId == albumId } }

    override fun observeGenres() = read { genres.values.toList() }

    override fun observeGenre(id: GenreId) = read { genres.values.firstOrNull { it.id == id } }

    override fun observeArtistsByGenre(genreId: GenreId) = read {
        val inGenre = songsIn(genreId)
        val ids = inGenre.map { it.artistId } +
            albums.values.filter { album -> album.title != null && inGenre.any { it.albumId == album.id } }
                .map { it.artistId }
        artists.values.filter { it.id in ids }
    }

    override fun observeAlbumsByGenreAndArtist(genreId: GenreId, artistId: ArtistId) = read {
        val inGenre = songsIn(genreId)
        albums.values.filter { album ->
            album.title != null &&
                inGenre.any { it.albumId == album.id && (it.artistId == artistId || album.artistId == artistId) }
        }
    }

    override fun observeSongsByGenreAndArtist(genreId: GenreId, artistId: ArtistId) = read {
        val ownAlbums = albums.values.filter { it.artistId == artistId && it.title != null }.map { it.id }
        songsIn(genreId).filter { it.artistId == artistId || it.albumId in ownAlbums }
    }

    override fun observeSongsByGenreAndAlbum(genreId: GenreId, albumId: AlbumId) =
        read { songsIn(genreId).filter { it.albumId == albumId } }

    private fun Index.songsIn(genreId: GenreId): List<Song> {
        val key = genres.entries.firstOrNull { it.value.id == genreId }?.key
        return songList().filter { key in songGenres[it.id].orEmpty() }
    }

    override fun observeSongCount() = read { songs.size }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist?> =
        state.value.artists.values.firstOrNull { it.id == id }.right()

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album?> =
        state.value.albums.values.firstOrNull { it.id == id }.right()

    override suspend fun getGenre(id: GenreId): Either<LibraryError, Genre?> =
        state.value.genres.values.firstOrNull { it.id == id }.right()

    override suspend fun getSongFile(id: SongId): Either<LibraryError, SongFile?> = state.value.songs[id]?.file.right()

    override suspend fun getIndexedSongs(): Either<LibraryError, List<IndexedSong>> = indexed.right()

    override suspend fun write(batch: IndexBatch): Either<LibraryError, Unit> {
        val killed = failWritesAfter?.let { after -> batches.sumOf { it.writes.size } >= after } == true
        val error = writeError ?: LibraryError.Storage(StorageError.Unavailable).takeIf { killed }
        if (error != null) return error.left()
        batches += batch
        state.value = state.value.applying(batch)
        return Unit.right()
    }

    private fun Index.applying(batch: IndexBatch): Index {
        val songs = songs.toMutableMap()
        val artists = artists.toMutableMap()
        val albums = albums.toMutableMap()
        val genres = genres.toMutableMap()
        val songGenres = songGenres.toMutableMap()
        batch.writes.forEach { write ->
            val artistIdOf = { group: ArtistGroup ->
                val id = artists[group.key]?.id ?: ArtistId(nextId++)
                artists[group.key] = Artist(id, group.name, group.isVarious)
                id
            }
            artistIdOf(write.artist)
            val albumArtist = artistIdOf(write.album.artist)
            val album = albums[write.album.key]
            albums[write.album.key] = Album(
                id = album?.id ?: AlbumId(nextId++),
                artistId = albumArtist,
                title = write.album.title,
                year = write.album.year ?: album?.year,
                // Set below, once the album's songs are known.
                artwork = album?.artwork ?: ArtworkRef(SongId(0)),
            )
            val id = write.id ?: SongId(nextId++)
            songs[id] = IndexedSong(
                id,
                write.file,
                write.metadata,
                write.artist.key,
                write.album.key,
                write.hasEmbeddedPicture,
                write.tagsReadAt,
                write.isDuplicate,
                write.isProtected,
            )
            write.genres.forEach { group ->
                genres[group.key] = Genre(genres[group.key]?.id ?: GenreId(nextId++), group.name)
            }
            songGenres[id] = write.genres.map { it.key }
        }
        batch.removals.forEach(songs::remove)
        songGenres.keys.retainAll(songs.keys)
        val usedGenres = songGenres.values.flatten().toSet()
        genres.keys.retainAll(usedGenres)
        val usedAlbums = songs.values.mapTo(HashSet()) { it.albumKey }
        albums.keys.retainAll(usedAlbums)
        albums.replaceAll { key, album -> album.copy(artwork = songs.values.artworkOf(key)) }
        val albumArtists = albums.values.mapTo(HashSet()) { it.artistId }
        val songArtists = songs.values.mapTo(HashSet()) { it.artistKey }
        artists.entries.retainAll { (key, artist) -> key in songArtists || artist.id in albumArtists }
        return Index(songs, artists, albums, genres, songGenres)
    }

    override suspend fun getScanState(): Either<LibraryError, List<VolumeScanState>> = savedScanState.orEmpty().right()

    override fun observeSongLocations() = read { songs.mapValues { (_, song) -> song.file.uri } }

    override suspend fun saveScanState(volumes: List<VolumeScanState>): Either<LibraryError, Unit> {
        savedScanState = volumes
        return Unit.right()
    }
}

/** The song whose cover stands for the album of [albumKey], as the real index picks it, in insertion order. */
private fun Collection<IndexedSong>.artworkOf(albumKey: String): ArtworkRef {
    val song = filter { it.albumKey == albumKey }
        .sortedWith(compareBy({ it.hasEmbeddedPicture != true }, { it.isDuplicate }))
        .first()
    return ArtworkRef(song.id)
}
