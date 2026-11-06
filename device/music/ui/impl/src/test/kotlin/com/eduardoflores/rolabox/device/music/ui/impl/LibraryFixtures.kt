package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.Genre
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import kotlin.time.Duration.Companion.minutes

val MarenHolt = Artist(ArtistId(1), "Maren Holt")
val BlueHarbor = Artist(ArtistId(2), "Blue Harbor")
val LowTideRadio = Album(AlbumId(10), MarenHolt.id, "Low Tide Radio", year = 2019, ArtworkRef(SongId(10)))
val PaperSatellites = Album(AlbumId(11), MarenHolt.id, "Paper Satellites", year = 2022, ArtworkRef(SongId(11)))
val Harborlight = Album(AlbumId(20), BlueHarbor.id, "Harborlight", year = 2021, ArtworkRef(SongId(20)))

val Glasshouse = song(100, PaperSatellites, "Glasshouse", trackNumber = 2)
val Coastline = song(101, PaperSatellites, "Coastline", trackNumber = 1)
val DialTone = song(102, LowTideRadio, "Dial Tone", trackNumber = 1)
val Lighthouse = song(200, Harborlight, "Lighthouse", trackNumber = 1)

private fun song(id: Long, album: Album, title: String, trackNumber: Int) =
    Song(SongId(id), album.id, album.artistId, title, trackNumber, duration = 3.minutes, uri = "content://songs/$id")

val Jazz = Genre(GenreId(30), "Jazz")
val Rock = Genre(GenreId(31), "Rock")
val UnknownGenre = Genre(GenreId(32), name = null)

/**
 * A library with two artists, three albums, and songs on each. Glasshouse is Rock and Jazz, Coastline and Lighthouse
 * Rock, and Dial Tone has no genre.
 */
fun filledLibrary() = FakeLibraryRepository().apply {
    artists = listOf(MarenHolt, BlueHarbor)
    albums = listOf(LowTideRadio, PaperSatellites, Harborlight)
    songs = listOf(Glasshouse, Coastline, DialTone, Lighthouse)
    genres = listOf(Jazz, Rock, UnknownGenre)
    songGenres = mapOf(
        Glasshouse.id to setOf(Rock.id, Jazz.id),
        Coastline.id to setOf(Rock.id),
        DialTone.id to setOf(UnknownGenre.id),
        Lighthouse.id to setOf(Rock.id),
    )
}
