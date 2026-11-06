package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.GenreGroup
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import kotlinx.coroutines.flow.Flow

private const val SONG_COLUMNS =
    "s.id, s.albumId, s.artistId, s.title, s.trackNumber, s.durationMillis, s.uri, s.discNumber, s.isProtected"

// A title without a leading "The", as the artists' sortName and the fast-scroll letter (ADR-024): "The Wall" is with
// the W's. A title of only "The" sorts by that. LIKE ignores the case of ASCII letters.
private const val SONG_SORT_TITLE = "CASE WHEN s.title LIKE 'the %' THEN substr(s.title, 5) ELSE s.title END"
private const val ALBUM_SORT_TITLE = "CASE WHEN a.title LIKE 'the %' THEN substr(a.title, 5) ELSE a.title END"

// A missing value sorts last, as a value of its own (ADR-024). The id settles what is otherwise equal.
private const val BY_TITLE = "ORDER BY s.title IS NULL, $SONG_SORT_TITLE COLLATE NOCASE, s.id"
private const val BY_ALBUM_TITLE = "ORDER BY a.title IS NULL, $ALBUM_SORT_TITLE COLLATE NOCASE, a.id"
private const val BY_YEAR = "ORDER BY year IS NULL, year, title IS NULL, title COLLATE NOCASE, id"

// The id of the song whose cover stands for album `a`: the first, in track order, whose tags have a picture, or else
// the first song (ADR-026). A duplicate comes last, and an album always has a song, since one without is deleted.
internal const val ARTWORK_SONG_OF_ALBUM = "(SELECT s.id FROM songs s WHERE s.albumId = a.id " +
    "ORDER BY s.hasEmbeddedPicture IS NOT 1, s.isDuplicate, s.discNumber IS NULL, s.discNumber, " +
    "s.trackNumber IS NULL, s.trackNumber, s.title IS NULL, s.title COLLATE NOCASE, s.id LIMIT 1)"

private const val ALBUM_COLUMNS = "a.*, $ARTWORK_SONG_OF_ALBUM AS artworkSongId"

// The songs of a genre that a list shows, and the order of artists, as the list of every artist sorts them.
private const val GENRE_SONGS =
    "SELECT s.id FROM songs s JOIN song_genres g ON g.songId = s.id WHERE g.genreId = :genreId AND s.isDuplicate = 0"
private const val BY_ARTIST_NAME = "ORDER BY sortName IS NULL, sortName, id"
private const val BY_DISC_AND_TRACK = "ORDER BY s.discNumber IS NULL, s.discNumber, s.trackNumber IS NULL, " +
    "s.trackNumber, s.title IS NULL, s.title COLLATE NOCASE, s.id"

// One interface for one database: the observed lists, and what one batch of a scan writes.
@Suppress("TooManyFunctions")
@Dao
internal interface LibraryIndexDao {
    // The album of songs with no album belongs to the unknown artist, who isn't a row unless a song is theirs.
    @Query(
        "SELECT * FROM artists WHERE name IS NOT NULL OR isVarious OR id IN (SELECT artistId FROM songs) " +
            BY_ARTIST_NAME,
    )
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT $ALBUM_COLUMNS FROM albums a $BY_ALBUM_TITLE")
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT $SONG_COLUMNS FROM songs s WHERE s.isDuplicate = 0 $BY_TITLE")
    fun observeSongs(): Flow<List<SongRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtist(id: Long): Flow<ArtistEntity?>

    @Query("SELECT $ALBUM_COLUMNS FROM albums a WHERE a.id = :id")
    fun observeAlbum(id: Long): Flow<AlbumRow?>

    @Query(
        "SELECT $ALBUM_COLUMNS FROM albums a WHERE a.title IS NOT NULL AND (a.artistId = :artistId " +
            "OR a.id IN (SELECT albumId FROM songs WHERE artistId = :artistId)) $BY_YEAR",
    )
    fun observeAlbumsByArtist(artistId: Long): Flow<List<AlbumRow>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.isDuplicate = 0 AND (s.artistId = :artistId " +
            "OR s.albumId IN (SELECT id FROM albums WHERE artistId = :artistId AND title IS NOT NULL)) $BY_TITLE",
    )
    fun observeSongsByArtist(artistId: Long): Flow<List<SongRow>>

    @Query("SELECT $SONG_COLUMNS FROM songs s WHERE s.albumId = :albumId AND s.isDuplicate = 0 $BY_DISC_AND_TRACK")
    fun observeSongsByAlbum(albumId: Long): Flow<List<SongRow>>

    // Unknown Genre, the one with no name, sorts last (ADR-024, Genre).
    @Query("SELECT * FROM genres ORDER BY name IS NULL, name COLLATE NOCASE, id")
    fun observeGenres(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE id = :id")
    fun observeGenre(id: Long): Flow<GenreEntity?>

    // The songs' own artists, and the artists of their albums, as the list of every artist has both. The album of
    // songs with no album has no artist of its own, as on an artist's screen.
    @Query(
        "SELECT * FROM artists WHERE id IN (SELECT artistId FROM songs WHERE id IN ($GENRE_SONGS)) " +
            "OR id IN (SELECT artistId FROM albums WHERE title IS NOT NULL " +
            "AND id IN (SELECT albumId FROM songs WHERE id IN ($GENRE_SONGS))) $BY_ARTIST_NAME",
    )
    fun observeArtistsByGenre(genreId: Long): Flow<List<ArtistEntity>>

    // The albums of observeAlbumsByArtist with a song in the genre: the artist's, or one with their song in it.
    @Query(
        "SELECT $ALBUM_COLUMNS FROM albums a WHERE a.title IS NOT NULL AND a.id IN (SELECT albumId FROM songs " +
            "WHERE id IN ($GENRE_SONGS) AND (artistId = :artistId OR a.artistId = :artistId)) $BY_YEAR",
    )
    fun observeAlbumsByGenreAndArtist(genreId: Long, artistId: Long): Flow<List<AlbumRow>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.id IN ($GENRE_SONGS) AND (s.artistId = :artistId " +
            "OR s.albumId IN (SELECT id FROM albums WHERE artistId = :artistId AND title IS NOT NULL)) $BY_TITLE",
    )
    fun observeSongsByGenreAndArtist(genreId: Long, artistId: Long): Flow<List<SongRow>>

    @Query("SELECT $SONG_COLUMNS FROM songs s WHERE s.albumId = :albumId AND s.id IN ($GENRE_SONGS) $BY_DISC_AND_TRACK")
    fun observeSongsByGenreAndAlbum(genreId: Long, albumId: Long): Flow<List<SongRow>>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeSongCount(): Flow<Int>

    @Query("SELECT id, uri FROM songs")
    fun observeSongLocations(): Flow<List<SongLocationRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtist(id: Long): ArtistEntity?

    @Query("SELECT $ALBUM_COLUMNS FROM albums a WHERE a.id = :id")
    suspend fun getAlbum(id: Long): AlbumRow?

    @Query("SELECT * FROM genres WHERE id = :id")
    suspend fun getGenre(id: Long): GenreEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: Long): SongEntity?

    @Query(
        "SELECT s.*, ar.groupKey AS artistKey, al.groupKey AS albumKey FROM songs s " +
            "JOIN artists ar ON ar.id = s.artistId JOIN albums al ON al.id = s.albumId ORDER BY s.id",
    )
    suspend fun getIndexedSongs(): List<IndexedSongRow>

    @Query("SELECT * FROM scan_state ORDER BY volume")
    suspend fun scanState(): List<ScanStateEntity>

    @Transaction
    suspend fun write(batch: IndexBatch) {
        batch.writes.forEach { write ->
            val artistId = artistIdOf(write.artist)
            val albumId = albumIdOf(write.album)
            val entity = write.toEntity(artistId, albumId)
            // An update keeps the row's id, and Room answers -1 for it.
            val songId = upsertSong(entity).takeIf { it != -1L } ?: entity.id
            deleteGenresOfSongs(listOf(songId))
            insertSongGenres(write.genres.map { SongGenreEntity(songId, genreIdOf(it)) })
        }
        if (batch.removals.isNotEmpty()) {
            val removed = batch.removals.map { it.value }
            deleteSongs(removed)
            deleteGenresOfSongs(removed)
        }
        deleteAlbumsWithoutSongs()
        deleteArtistsWithoutSongsOrAlbums()
        deleteGenresWithoutSongs()
    }

    @Transaction
    suspend fun replaceScanState(state: List<ScanStateEntity>) {
        deleteScanState()
        upsertScanState(state)
    }

    private suspend fun artistIdOf(artist: ArtistGroup): Long {
        val wanted = ArtistEntity(
            groupKey = artist.key,
            name = artist.name,
            sortName = artist.sortName,
            isVarious = artist.isVarious,
        )
        val existing = artistByKey(artist.key) ?: return insertArtist(wanted)
        // The key stays, and the spelling the row shows follows the songs.
        val updated = wanted.copy(id = existing.id)
        if (updated != existing) updateArtist(updated)
        return existing.id
    }

    private suspend fun albumIdOf(album: AlbumGroup): Long {
        val artistId = artistIdOf(album.artist)
        val existing = albumByKey(album.key)
            ?: return insertAlbum(
                AlbumEntity(groupKey = album.key, artistId = artistId, title = album.title, year = album.year),
            )
        // A song that has no year doesn't take it away from the album.
        val updated = existing.copy(artistId = artistId, title = album.title, year = album.year ?: existing.year)
        if (updated != existing) updateAlbum(updated)
        return existing.id
    }

    private suspend fun genreIdOf(genre: GenreGroup): Long {
        val wanted = GenreEntity(groupKey = genre.key, name = genre.name)
        val existing = genreByKey(genre.key) ?: return insertGenre(wanted)
        // The key stays, and the spelling the row shows follows the songs.
        if (existing.name != genre.name) updateGenre(wanted.copy(id = existing.id))
        return existing.id
    }

    @Query("SELECT * FROM genres WHERE groupKey = :key")
    suspend fun genreByKey(key: String): GenreEntity?

    @Insert
    suspend fun insertGenre(genre: GenreEntity): Long

    @Update
    suspend fun updateGenre(genre: GenreEntity)

    @Insert
    suspend fun insertSongGenres(songGenres: List<SongGenreEntity>)

    @Query("DELETE FROM song_genres WHERE songId IN (:songIds)")
    suspend fun deleteGenresOfSongs(songIds: List<Long>)

    @Query("DELETE FROM genres WHERE id NOT IN (SELECT genreId FROM song_genres)")
    suspend fun deleteGenresWithoutSongs()

    @Query("SELECT * FROM artists WHERE groupKey = :key")
    suspend fun artistByKey(key: String): ArtistEntity?

    @Query("SELECT * FROM albums WHERE groupKey = :key")
    suspend fun albumByKey(key: String): AlbumEntity?

    @Insert
    suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Update
    suspend fun updateArtist(artist: ArtistEntity)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Upsert
    suspend fun upsertSong(song: SongEntity): Long

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT albumId FROM songs)")
    suspend fun deleteAlbumsWithoutSongs()

    @Query(
        "DELETE FROM artists WHERE id NOT IN (SELECT artistId FROM songs) " +
            "AND id NOT IN (SELECT artistId FROM albums)",
    )
    suspend fun deleteArtistsWithoutSongsOrAlbums()

    @Query("DELETE FROM scan_state")
    suspend fun deleteScanState()

    @Upsert
    suspend fun upsertScanState(state: List<ScanStateEntity>)
}
