package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import org.junit.Test

/** The music lists for the Play listing and the README (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    @Test
    fun artists() = list(
        StoreSet.Play,
        "02-artists",
        title = "Artists",
        rows = StoreArtists.map { DeviceListRow(it, opensSubmenu = true) },
        highlighted = StoreArtists.indexOf("Maren Holt"),
    )

    @Test
    fun albumSongs() = list(
        StoreSet.Play,
        "03-album-songs",
        title = "Paper Satellites",
        rows = StorePaperSatellites.map { DeviceListRow(it) },
        highlighted = StorePaperSatellites.indexOf("Weather Report"),
    )

    // U2's The Best of 1990-2000 (2002), in album order, with track 1 highlighted, as on Now Playing.
    @Test
    fun readmeAlbumSongs() = list(
        StoreSet.Readme,
        "songs-u2-best-of",
        title = "The Best of 1990-2000",
        rows = StoreBestOf1990To2000.map { DeviceListRow(it) },
        highlighted = 0,
    )

    private fun list(set: StoreSet, name: String, title: String, rows: List<DeviceListRow>, highlighted: Int) =
        shoot(set, name) {
            RolaboxTheme {
                DeviceScreen(title = title, batteryLevel = 0.8f, onWheelEvent = {}, record = DeviceRecord.Spinning) {
                    Box(Modifier.fillMaxSize()) {
                        MusicList(ListUiState.Loaded(rows), highlightedIndex = highlighted, emptyText = "")
                    }
                }
            }
        }
}

private val StoreArtists = listOf(
    "Adeline Park", "Blue Harbor", "Cass Monroe", "Dune Collective", "Ellis Grey", "Fern & Iron", "Hollow Pines",
    "Juno Vale", "Kite Theory", "Maren Holt", "Northbound", "Otis Ray", "Pale Lanterns", "Quiet Fleet",
)

private val StorePaperSatellites = listOf(
    "Dial Tone", "Every Other Window", "Glasshouse", "Tin Can Summer", "Weather Report", "Satellite Hymn",
    "Coastline", "Halfway Houses", "Night Bus", "Paper Satellites",
)

private val StoreBestOf1990To2000 = listOf(
    "Even Better Than the Real Thing",
    "Mysterious Ways",
    "Beautiful Day",
    "Electrical Storm (William Orbit Mix)",
    "One",
    "Miss Sarajevo",
    "Stay (Faraway, So Close!)",
    "Stuck in a Moment You Can't Get Out Of",
    "Gone (New Mix)",
    "Until the End of the World",
    "The Hands That Built America",
    "Discothèque (New Mix)",
    "Hold Me, Thrill Me, Kiss Me, Kill Me",
    "Staring at the Sun (New Mix)",
    "Numb (New Mix)",
    "The First Time",
)
