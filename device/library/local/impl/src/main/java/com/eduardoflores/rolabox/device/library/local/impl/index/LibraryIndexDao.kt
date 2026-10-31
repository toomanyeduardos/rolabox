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
    @Query("SELECT * FROM artists ORDER BY name IS NULL, name COLLATE NOCASE, id")
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM albums $BY_YEAR")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT $SONG_COLUMNS FROM songs s $BY_TITLE")
    fun observeSongs(): Flow<List<SongRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtist(id: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbum(id: Long): Flow<AlbumEntity?>

    @Query(
        "SELECT * FROM albums WHERE artistId = :artistId " +
            "OR id IN (SELECT albumId FROM songs WHERE artistId = :artistId) $BY_YEAR",
    )
    fun observeAlbumsByArtist(artistId: Long): Flow<List<AlbumEntity>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.artistId = :artistId " +
            "OR s.albumId IN (SELECT id FROM albums WHERE artistId = :artistId) $BY_TITLE",
    )
    fun observeSongsByArtist(artistId: Long): Flow<List<SongRow>>

    @Query(
        "SELECT $SONG_COLUMNS FROM songs s WHERE s.albumId = :albumId " +
            "ORDER BY s.discNumber IS NULL, s.discNumber, s.trackNumber IS NULL, s.trackNumber, " +
            "s.title IS NULL, s.title COLLATE NOCASE, s.id",
    )
    fun observeSongsByAlbum(albumId: Long): Flow<List<SongRow>>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeSongCount(): Flow<Int>

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

    private suspend fun artistIdOf(artist: ArtistGroup): Long =
        artistIdByKey(artist.key) ?: insertArtist(ArtistEntity(groupKey = artist.key, name = artist.name))

    private suspend fun albumIdOf(album: AlbumGroup): Long {
        val artistId = artistIdOf(album.artist)
        val existing = albumByKey(album.key)
            ?: return insertAlbum(
                AlbumEntity(groupKey = album.key, artistId = artistId, title = album.title, year = album.year),
            )
        // A song that has no year doesn't take it away from the album.
        val updated = existing.copy(artistId = artistId, year = album.year ?: existing.year)
        if (updated != existing) updateAlbum(updated)
        return existing.id
    }

    @Query("SELECT id FROM artists WHERE groupKey = :key")
    suspend fun artistIdByKey(key: String): Long?

    @Query("SELECT * FROM albums WHERE groupKey = :key")
    suspend fun albumByKey(key: String): AlbumEntity?

    @Insert
    suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

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
