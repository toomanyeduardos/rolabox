package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration.Companion.seconds

/**
 * The hard-coded library behind [InMemoryLibraryRepository].
 *
 * TEMPORARY: this stands in for the real library so the device can be built. It has no local database,
 * which ADR-002 expects, and it is replaced when the real library is designed.
 *
 * The data is made up, and shaped to exercise the device: a few featured artists, one with several albums,
 * more songs than fit on a screen, and a long roster of artists to spin through fast. Ids follow the
 * order of the seeds below.
 */
internal class SampleLibrary private constructor(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
) {
    companion object {
        val Default: SampleLibrary = build(FEATURED + ROSTER)

        private fun build(seeds: List<ArtistSeed>): SampleLibrary {
            val artists = mutableListOf<Artist>()
            val albums = mutableListOf<Album>()
            val songs = mutableListOf<Song>()
            seeds.forEach { artistSeed ->
                val artist = Artist(ArtistId(artists.size + 1L), artistSeed.name)
                artists += artist
                artistSeed.albums.forEach { albumSeed ->
                    val album = Album(AlbumId(albums.size + 1L), artist.id, albumSeed.title, albumSeed.year)
                    albums += album
                    albumSeed.songs.forEachIndexed { index, title ->
                        songs += Song(
                            id = SongId(songs.size + 1L),
                            albumId = album.id,
                            artistId = artist.id,
                            title = title,
                            trackNumber = index + 1,
                            // Between 2:30 and 5:29, spread so that no two neighbours match.
                            duration = (MIN_SECONDS + (songs.size * STEP_SECONDS) % RANGE_SECONDS).seconds,
                        )
                    }
                }
            }
            return SampleLibrary(artists, albums, songs)
        }
    }
}

private const val MIN_SECONDS = 150
private const val RANGE_SECONDS = 180
private const val STEP_SECONDS = 47

private class ArtistSeed(val name: String, val albums: List<AlbumSeed>)

private class AlbumSeed(val title: String, val year: Int, val songs: List<String>)

// A long tail of artists, so the Artists list is long enough to spin through fast. Every letter is there,
// and names that start with a digit, a symbol or "The". Each has one short album.
private val ROSTER_NAMES = listOf(
    "!Static", "10,000 Lakes", "2 Hour Drive", "4th Avenue", "99 Lanterns",
    "Adeline Park", "Amber Coast", "Atlas Rowe", "Blue Harbor", "Bramble Kings", "Brightwater",
    "Cass Monroe", "Cinder Hill", "Copper Lane", "Dune Collective", "Delta Nine", "Dover Grey",
    "Ellis Grey", "Echo Parlor", "Emberly", "Fern & Iron", "Foxglove", "Fable Street",
    "Glass Arcade", "Golden Hour Club", "Hollow Pines", "Harbor Lights", "Halcyon Drive",
    "Isla Moreno", "Ivory Coastline", "Juno Vale", "Jetty Wren", "Kite Theory", "Kestrel",
    "Lantern Bay", "Late Arrivals", "Lowland Choir", "Lucid Mile", "Lyra Banks",
    "Mabel Quinn", "Maple Static", "Maren Holt", "Mason Reed", "Meadowlark", "Midnight Arcade", "Mira Sol",
    "Moth & Flame", "Nadia Frost", "Northbound", "Neon Willow", "Otis Ray", "Orchid Lane",
    "Pale Lanterns", "Pine & Pewter", "Quiet Fleet", "Quill & Quarry", "Rosa Lind", "Rust Belt Radio",
    "Silver Tide", "Slow Meridian", "Sundial", "The Atlas Club", "The Blue Hours", "The Low Fields",
    "The Weather Station", "Tidal Mile", "Umber", "Union Pier", "Violet Hour", "Vesper Lane",
    "Willa Marsh", "Wolf & Willow", "Xavier Lune", "Yellow Kite", "Yara Bell", "Zephyr Lane", "Zinnia",
)

private val ROSTER_SONGS = listOf(
    "Open Road",
    "Low Light",
    "Paper Moon",
    "Northern Line",
    "Second Wind",
    "Static Bloom",
    "Long Way Round",
)

private val ROSTER = ROSTER_NAMES.mapIndexed { index, name ->
    ArtistSeed(
        name,
        listOf(
            AlbumSeed(
                "$name EP",
                ROSTER_FIRST_YEAR + index % ROSTER_YEARS,
                listOf(ROSTER_SONGS[index % ROSTER_SONGS.size], ROSTER_SONGS[(index + 3) % ROSTER_SONGS.size]),
            ),
        ),
    )
}

private const val ROSTER_FIRST_YEAR = 2001
private const val ROSTER_YEARS = 24

private val FEATURED = listOf(
    ArtistSeed(
        "The Paper Lanterns",
        listOf(
            AlbumSeed(
                "First Light",
                2009,
                listOf(
                    "Morning Static",
                    "Window Seat",
                    "Slow Train Home",
                    "Paper Boats",
                    "Half Awake",
                    "Salt and Lime",
                ),
            ),
            AlbumSeed(
                "Low Tide",
                2011,
                listOf(
                    "Harbor Lights",
                    "Under the Pier",
                    "Driftwood",
                    "Anchor Song",
                    "Late Ferry",
                    "Sea Glass",
                    "Gull Parade",
                ),
            ),
            AlbumSeed(
                "Cartographers",
                2014,
                listOf(
                    "North of Here",
                    "Folded Maps",
                    "The Long Way",
                    "Compass Rose",
                    "Mile Marker",
                    "Detour",
                    "Bearings",
                    "Last Stop",
                ),
            ),
            AlbumSeed(
                "Afterglow",
                2017,
                listOf("Dusk Patrol", "Neon Rain", "Quiet Hours", "Golden Static", "Porchlight", "Dim the Sun"),
            ),
            AlbumSeed(
                "Lantern Season",
                2021,
                listOf(
                    "Lit from Within",
                    "Wick",
                    "Orchard Road",
                    "Warm Front",
                    "Ember Waltz",
                    "Candlelight Tax",
                    "Wind Chime",
                    "Hush",
                ),
            ),
        ),
    ),
    ArtistSeed(
        "Marlo Vance",
        listOf(
            AlbumSeed(
                "Gravel Road Blues",
                2012,
                listOf("Dust Devil", "Hand Me Down", "Kerosene", "Rust Belt Lullaby", "Two Dollar Guitar", "Tin Roof"),
            ),
            AlbumSeed(
                "Spare Change",
                2016,
                listOf("Pocket Full", "Bus Fare", "Laundromat Love", "Cheap Wine", "Last Nickel", "Payday", "Layaway"),
            ),
        ),
    ),
    ArtistSeed(
        "Velvet Circuit",
        listOf(
            AlbumSeed(
                "Mainframe Romance",
                2018,
                listOf(
                    "Boot Sequence",
                    "Dial Tone",
                    "Pixel Heart",
                    "Modem Love",
                    "Cache Me Outside",
                    "Overclocked",
                    "Safe Mode",
                    "Shutdown",
                ),
            ),
            AlbumSeed(
                "Analog Ghost",
                2020,
                listOf(
                    "Tape Hiss",
                    "Ghost in the Cable",
                    "Warm Signal",
                    "Vinyl Crackle",
                    "Reel to Reel",
                    "Static Bloom",
                ),
            ),
        ),
    ),
    ArtistSeed(
        "Ines Calderon",
        listOf(
            AlbumSeed(
                "Sombra y Sol",
                2015,
                listOf(
                    "Amanecer",
                    "Calle Larga",
                    "Naranjos",
                    "Bailar Despacio",
                    "La Siesta",
                    "Cartas al Mar",
                    "Fuego Lento",
                ),
            ),
        ),
    ),
    ArtistSeed(
        "Oak & Ember",
        listOf(
            AlbumSeed(
                "Timber",
                2013,
                listOf("Fell the Tall Ones", "Woodsmoke", "Axe Handle", "Winter Store", "Cabin Fever", "Sawdust Waltz"),
            ),
            AlbumSeed(
                "Hearth",
                2019,
                listOf("Kindling", "Slow Burn", "Chimney Song", "Banked Fire", "Ash Wednesday", "Red Coals", "Stay In"),
            ),
        ),
    ),
    ArtistSeed(
        "DJ Parallax",
        listOf(
            AlbumSeed(
                "Phase Shift",
                2022,
                listOf(
                    "Warm Up",
                    "Frequency",
                    "Sub Bass Theory",
                    "Drop Zone",
                    "Four on the Floor",
                    "Cooldown",
                    "After Hours",
                ),
            ),
        ),
    ),
    ArtistSeed(
        "The Midnight Orchard",
        listOf(
            AlbumSeed(
                "Windfall",
                2010,
                listOf(
                    "Bruised Apples",
                    "Cider Press",
                    "Grafted",
                    "Ladder Against the Moon",
                    "Harvest Hands",
                    "Frost Warning",
                ),
            ),
            AlbumSeed(
                "Rootstock",
                2023,
                listOf(
                    "Deep Roots",
                    "Pruning Season",
                    "Blossom Count",
                    "Bee Loud",
                    "Crooked Row",
                    "Last Fruit",
                    "Gate Latch",
                    "Sleep Under",
                ),
            ),
        ),
    ),
    ArtistSeed(
        "Juno Okafor",
        listOf(
            AlbumSeed(
                "Small Hours",
                2020,
                listOf("Kettle On", "Rooftop Quiet", "Night Bus", "Borrowed Sweater", "Soft Landing", "Lights Out"),
            ),
        ),
    ),
)
