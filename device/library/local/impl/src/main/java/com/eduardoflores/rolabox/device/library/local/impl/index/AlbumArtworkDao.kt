package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// An album `a`, its artist, the file of the song whose cover stands for it, and its record, if it has one.
private const val ARTWORK_ALBUMS = "SELECT a.groupKey AS albumKey, a.title AS title, ar.name AS artistName, " +
    "ar.isVarious AS isVarious, " +
    "(SELECT so.uri FROM songs so WHERE so.id = $ARTWORK_SONG_OF_ALBUM) AS artworkSongUri, " +
    "w.state AS state, w.fileName AS fileName, w.sourceId AS sourceId, w.foundAt AS foundAt, " +
    "w.attempts AS attempts, w.lastAttemptAt AS lastAttemptAt " +
    "FROM albums a JOIN artists ar ON ar.id = a.artistId LEFT JOIN album_artwork w ON w.albumKey = a.groupKey"

/** The records of the covers the library downloads, with the albums of the index they are for (ADR-027). */
@Dao
internal interface AlbumArtworkDao {
    @Query("$ARTWORK_ALBUMS ORDER BY a.id")
    suspend fun albums(): List<ArtworkAlbumRow>

    @Query("$ARTWORK_ALBUMS WHERE a.groupKey = :key")
    suspend fun album(key: String): ArtworkAlbumRow?

    @Query("$ARTWORK_ALBUMS WHERE a.id = (SELECT albumId FROM songs WHERE id = :songId)")
    suspend fun albumOfSong(songId: Long): ArtworkAlbumRow?

    @Query("SELECT * FROM album_artwork WHERE albumKey = :key")
    fun observe(key: String): Flow<AlbumArtworkEntity?>

    @Upsert
    suspend fun upsert(record: AlbumArtworkEntity)
}
