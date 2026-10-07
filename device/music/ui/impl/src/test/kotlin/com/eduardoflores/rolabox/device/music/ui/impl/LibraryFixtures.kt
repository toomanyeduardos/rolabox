package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import kotlin.time.Duration.Companion.minutes

val MarenHolt = Artist(ArtistId(1), "Maren Holt")
val BlueHarbor = Artist(ArtistId(2), "Blue Harbor")
val LowTideRadio = Album(AlbumId(10), MarenHolt.id, "Low Tide Radio", year = 2019)
val PaperSatellites = Album(AlbumId(11), MarenHolt.id, "Paper Satellites", year = 2022)
val Harborlight = Album(AlbumId(20), BlueHarbor.id, "Harborlight", year = 2021)

val Glasshouse = song(100, PaperSatellites, "Glasshouse", trackNumber = 2)
val Coastline = song(101, PaperSatellites, "Coastline", trackNumber = 1)
val DialTone = song(102, LowTideRadio, "Dial Tone", trackNumber = 1)
val Lighthouse = song(200, Harborlight, "Lighthouse", trackNumber = 1)

private fun song(id: Long, album: Album, title: String, trackNumber: Int) =
    Song(SongId(id), album.id, album.artistId, title, trackNumber, duration = 3.minutes)

/** A library with two artists, three albums, and songs on each. */
fun filledLibrary() = FakeLibraryRepository().apply {
    artists = listOf(MarenHolt, BlueHarbor)
    albums = listOf(LowTideRadio, PaperSatellites, Harborlight)
    songs = listOf(Glasshouse, Coastline, DialTone, Lighthouse)
}
