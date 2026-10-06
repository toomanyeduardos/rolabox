package com.eduardoflores.rolabox.device.music.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.library.api.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistAlbumsRowsTest {
    private val allSongs = DeviceListRow("All Songs", opensSubmenu = true)

    @Test
    fun allSongs_comesFirst_thenTheAlbumsInOrder() {
        val rows = ListUiState.Loaded(listOf(LowTideRadio, PaperSatellites)).withAllSongs("All Songs")

        assertEquals(
            ListUiState.Loaded(
                listOf(
                    allSongs,
                    DeviceListRow("Low Tide Radio", opensSubmenu = true),
                    DeviceListRow("Paper Satellites", opensSubmenu = true),
                ),
            ),
            rows,
        )
    }

    @Test
    fun anArtistWithoutAlbums_stillHasAllSongs() {
        assertEquals(
            ListUiState.Loaded(listOf(allSongs)),
            ListUiState.Loaded(emptyList<Album>()).withAllSongs("All Songs"),
        )
    }

    @Test
    fun loadingAndFailed_areKept() {
        assertEquals(ListUiState.Loading, ListUiState.Loading.withAllSongs("All Songs"))
        assertEquals(ListUiState.Failed, ListUiState.Failed.withAllSongs("All Songs"))
    }
}
