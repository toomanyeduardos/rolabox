package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.GenreId
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Genres, and the screens after it, which are the artists', albums' and songs' screens given the genre. */
class GenresViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = filledLibrary()

    private fun TestScope.genres() = GenresViewModelImpl(library).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    private fun TestScope.artists(genre: GenreId) = ArtistsViewModelImpl(ArtistsSource.Genre(genre), library).also {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.title.collect() }
    }

    private fun TestScope.albums(genre: GenreId, artistId: Long) =
        ArtistAlbumsViewModelImpl(artistId, genre.value, library).also {
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
        }

    private fun TestScope.songs(source: SongsSource) = SongsViewModelImpl(source, library).also {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.title.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, GenresViewModelImpl(library).uiState.value)
    }

    @Test
    fun genres_areLoaded_inTheLibrarysOrder() = runTest {
        assertEquals(ListUiState.Loaded(listOf(Jazz, Rock, UnknownGenre)), genres().uiState.value)
    }

    @Test
    fun noGenres_isLoadedAndEmpty() = runTest {
        library.genres = emptyList()

        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), genres().uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, genres().uiState.value)
    }

    @Test
    fun aGenre_isTheArtistsWithASongInIt() = runTest {
        assertEquals(ListUiState.Loaded(listOf(MarenHolt)), artists(Jazz.id).uiState.value)
        assertEquals(ListUiState.Loaded(listOf(MarenHolt, BlueHarbor)), artists(Rock.id).uiState.value)
    }

    @Test
    fun aGenresArtists_areTitledWithItsName_andUnknownGenreHasNone() = runTest {
        assertEquals(ListTitle.Genre("Rock"), artists(Rock.id).title.value)
        assertEquals(ListTitle.Genre(null), artists(UnknownGenre.id).title.value)
    }

    @Test
    fun aGenreThatIsGone_isGone() = runTest {
        val viewModel = artists(Jazz.id)

        library.genres = listOf(Rock, UnknownGenre)

        assertEquals(ListUiState.Gone, viewModel.uiState.value)
    }

    @Test
    fun anArtistInAGenre_hasOnlyTheirAlbumsWithASongInIt() = runTest {
        assertEquals(ListUiState.Loaded(listOf(PaperSatellites)), albums(Rock.id, MarenHolt.id.value).uiState.value)
        assertEquals(
            ListUiState.Loaded(listOf(LowTideRadio)),
            albums(UnknownGenre.id, MarenHolt.id.value).uiState.value,
        )
    }

    @Test
    fun allSongsOfAnArtistInAGenre_areOnlyThoseInIt_byTitle() = runTest {
        val state = songs(SongsSource.GenreArtist(Rock.id, MarenHolt.id)).uiState.value

        assertEquals(ListUiState.Loaded(listOf(Coastline, Glasshouse)), state)
    }

    @Test
    fun anAlbumInAGenre_isOnlyItsSongsInIt() = runTest {
        val viewModel = songs(SongsSource.GenreAlbum(Jazz.id, PaperSatellites.id))

        assertEquals(ListUiState.Loaded(listOf(Glasshouse)), viewModel.uiState.value)
        assertEquals(ListTitle.Album("Paper Satellites"), viewModel.title.value)
    }

    @Test
    fun aSongWithSeveralGenres_isUnderEach() = runTest {
        val inRock = songs(SongsSource.GenreAlbum(Rock.id, PaperSatellites.id)).uiState.value
        val inJazz = songs(SongsSource.GenreAlbum(Jazz.id, PaperSatellites.id)).uiState.value

        assertEquals(true, (inRock as ListUiState.Loaded).items.contains(Glasshouse))
        assertEquals(true, (inJazz as ListUiState.Loaded).items.contains(Glasshouse))
    }

    @Test
    fun anArtistsSongsInAGenre_areSortedByName_butAnAlbumsAreNot() {
        assertEquals(true, SongsSource.GenreArtist(Rock.id, MarenHolt.id).isSortedByName)
        assertEquals(false, SongsSource.GenreAlbum(Rock.id, PaperSatellites.id).isSortedByName)
    }
}
