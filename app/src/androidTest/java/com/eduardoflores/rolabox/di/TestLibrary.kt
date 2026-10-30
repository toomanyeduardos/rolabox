package com.eduardoflores.rolabox.di

import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration.Companion.minutes

/**
 * The library of the app's tests: a few artists, the first with two albums of more songs than the tests turn
 * through. The tests read their rows from the repository, so nothing here is repeated in them.
 */
internal object TestLibrary {
    private val aurora = Artist(ArtistId(1), "Aurora Lane")
    private val tides = Artist(ArtistId(2), "The Low Tides")
    private val mireille = Artist(ArtistId(3), "Mireille Okafor")

    private val paperLanterns = Album(AlbumId(1), aurora.id, "Paper Lanterns", 2019)
    private val northbound = Album(AlbumId(2), aurora.id, "Northbound", 2022)
    private val saltwater = Album(AlbumId(3), tides.id, "Saltwater Radio", 2015)
    private val twoRooms = Album(AlbumId(4), mireille.id, "Two Rooms", 2020)

    val artists = listOf(aurora, tides, mireille)
    val albums = listOf(paperLanterns, northbound, saltwater, twoRooms)
    val songs =
        songsOf(paperLanterns, "Morning Signal", "Glass Harbor", "Small Hours", "Paper Lanterns", "Lantern Light") +
            songsOf(northbound, "Northbound", "Cold Light", "Long Way Home") +
            songsOf(saltwater, "Undertow", "Breakwater", "Static on the Line", "Last Ferry") +
            songsOf(twoRooms, "Front Room", "Hallway", "Lamp Light")

    private fun songsOf(album: Album, vararg titles: String): List<Song> = titles.mapIndexed { index, title ->
        val id = album.id.value * 100 + index + 1
        Song(
            id = SongId(id),
            albumId = album.id,
            artistId = album.artistId,
            title = title,
            trackNumber = index + 1,
            duration = 3.minutes,
            uri = "content://test/songs/$id",
        )
    }
}
