package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import kotlinx.coroutines.flow.Flow

private const val SONG_COLUMNS = "s.id, s.albumId, s.artistId, s.title, s.trackNumber, s.durationMillis, s.uri"

// A missing value sorts last, as a value of its own (ADR-024). The id settles what is otherwise equal.
private const val BY_TITLE = "ORDER BY s.title IS NULL, s.title COLLATE NOCASE, s.id"
private const val BY_YEAR = "ORDER BY year IS NULL, year, title IS NULL, title COLLATE NOCASE, id"

// One interface for one database: the observed lists, and what one batch of a scan writes.
@Suppress("TooManyFunctions")
@Dao
internal interface LibraryIndexDao {
    // The album of songs with no album belongs to the unknown artist, who isn't a row unless a song is theirs.
    @Query(
        "SELECT * FROM artists WHERE name IS NOT NULL OR isVarious OR id IN (SELECT artistId FROM songs) " +
            "ORDER BY sortName IS NULL, sortName, id",
    )
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM albums $BY_YEAR")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT $SONG_COLUMNS FROM songs s WHERE s.isDuplicate = 0 $BY_TITLE")
    fun observeSongs(): Flow<List<SongRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtist(id: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbum(id: Long): Flow<AlbumEntity?>

    @Query(
        "SELECT * FROM albums WHERE title IS NOT NULL AND (artistId = :artistId " +
            "OR id IN (SELECT albumId FROM songs WHERE artistId = :artistId)) $BY_YEAR",
    )
    fun observeAlbumsByArtist(artistId: Long): Flow<List<AlbumEntity>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.isDuplicate = 0 AND (s.artistId = :artistId " +
            "OR s.albumId IN (SELECT id FROM albums WHERE artistId = :artistId AND title IS NOT NULL)) $BY_TITLE",
    )
    fun observeSongsByArtist(artistId: Long): Flow<List<SongRow>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.albumId = :albumId AND s.isDuplicate = 0 " +
            "ORDER BY s.discNumber IS NULL, s.discNumber, s.trackNumber IS NULL, s.trackNumber, " +
            "s.title IS NULL, s.title COLLATE NOCASE, s.id",
    )
    fun observeSongsByAlbum(albumId: Long): Flow<List<SongRow>>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeSongCount(): Flow<Int>

    @Query("SELECT id, uri FROM songs")
    fun observeSongLocations(): Flow<List<SongLocationRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtist(id: Long): ArtistEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbum(id: Long): AlbumEntity?

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
            upsertSong(write.toEntity(artistId, albumId))
        }
        if (batch.removals.isNotEmpty()) deleteSongs(batch.removals.map { it.value })
        deleteAlbumsWithoutSongs()
        deleteArtistsWithoutSongsOrAlbums()
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
    suspend fun upsertSong(song: SongEntity)

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
