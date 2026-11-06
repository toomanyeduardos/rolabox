package com.eduardoflores.rolabox.device.library.impl

import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Genre
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeTagsLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixture
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixtures
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.library.testing.audio.Tags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The genre of a song, from its sources to the lists of a genre (ADR-024, Genre). */
@OptIn(ExperimentalCoroutinesApi::class)
class GenresTest {
    private val files = FakeAudioFilesLocalDataSource()
    private val index = FakeLibraryIndexLocalDataSource()
    private val tags = FakeTagsLocalDataSource()

    // Resolution

    @Test
    fun mediaStoresGenres_areTheFallback_withTheirSource() {
        val metadata = audioFile(1).copy(genres = listOf("Rock")).toMetadata()

        assertEquals(Sourced(listOf("Rock"), FieldSource.MediaStore), metadata.genres)
    }

    @Test
    fun noGenreAnywhere_isUnknown() {
        assertEquals(Sourced<List<String>>(null, FieldSource.Unknown), audioFile(1).toMetadata().genres)
    }

    @Test
    fun theTagsGenres_replaceMediaStores_andNoneInTheTagsKeepsThem() {
        val fromMediaStore = audioFile(1).copy(genres = listOf("Rock")).toMetadata()

        assertEquals(
            Sourced(listOf("Jazz", "Pop"), FieldSource.Tags),
            fromMediaStore.withTags(FileTags(genres = listOf("Jazz", "Pop"))).genres,
        )
        assertEquals(fromMediaStore.genres, fromMediaStore.withTags(FileTags()).genres)
    }

    // Grouping

    @Test
    fun genresThatDifferOnlyInCaseOrWhitespace_areOne_withTheSpellingOfMostSongs() {
        val writes = group(listOf("Dream Pop"), listOf("dream  pop"), listOf(" Dream Pop "))

        assertEquals(1, writes.flatMap { it.genres }.map { it.key }.distinct().size)
        assertEquals("Dream Pop", writes.first().genres.single().name)
    }

    @Test
    fun aSongWithNoGenre_isInTheUnknownGenre_whichHasNoName() {
        val genre = group(emptyList()).single().genres.single()

        assertEquals(null, genre.name)
    }

    @Test
    fun aSongWithSeveralGenres_isInEachOnce() {
        val genres = group(listOf("Rock", "Jazz", "ROCK")).single().genres

        assertEquals(listOf("Rock", "Jazz"), genres.map { it.name })
    }

    private fun group(vararg genres: List<String>) = groupSongs(
        genres.mapIndexed { index, names ->
            audioFile(index.toLong(), artist = "A").copy(genres = names).toDraft(null)
        },
    )

    // The broken set, scanned and read through the repository

    private fun TestScope.scannedRepository(): IndexLibraryRepository {
        files.files = AudioFixtures.broken.fixtures.mapIndexed(::audioFileOf)
        AudioFixtures.broken.fixtures.forEachIndexed { number, fixture ->
            tags.results[uriOf(number)] = FileTags(genres = genresAsParsed(tagsOf(fixture.content))).right()
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scanner = LibraryScanner(files, index, tags, backgroundScope, dispatcher)
        return IndexLibraryRepository(index, files, scanner, backgroundScope, dispatcher).also {
            it.checkPermission()
            runCurrent()
        }
    }

    private suspend fun IndexLibraryRepository.genre(name: String?): Genre =
        observeGenres().first().getOrNull().orEmpty().single { it.name == name }

    @Test
    fun theBrokenSet_hasEachGenreOnce_andUnknownGenre() = runTest {
        val names = scannedRepository().observeGenres().first().getOrNull().orEmpty().map { it.name }

        assertEquals(names.size, names.distinct().size)
        assertTrue(names.containsAll(listOf("Rock", "Pop", "Jazz", "Dream Pop", null)))
    }

    @Test
    fun theBrokenSet_isBrowsedFromAGenre_toItsArtists_albums_andSongs() = runTest {
        val repository = scannedRepository()
        val rock = repository.genre("Rock").id

        val genreLab = repository.observeArtistsByGenre(rock).first().getOrNull().orEmpty()
            .single { it.name == "Genre Lab" }
        val albums = repository.observeAlbumsByGenreAndArtist(rock, genreLab.id).first().getOrNull().orEmpty()
        val songs = repository.observeSongsByGenreAndAlbum(rock, albums.single().id).first().getOrNull().orEmpty()

        assertEquals(listOf("Genres"), albums.map { it.title })
        assertEquals(setOf("Parenthesised", "Number", "Several"), songs.map { it.title }.toSet())
    }

    @Test
    fun theBrokenSet_hasASongWithSeveralGenres_underEach() = runTest {
        val repository = scannedRepository()

        listOf("Rock", "Pop", "Jazz").forEach { name ->
            val genre = repository.genre(name).id
            val artist = repository.observeArtistsByGenre(genre).first().getOrNull().orEmpty()
                .single { it.name == "Genre Lab" }
            val songs = repository.observeSongsByGenreAndArtist(genre, artist.id).first().getOrNull().orEmpty()
            assertTrue(name, songs.any { it.title == "Several" })
        }
    }

    @Test
    fun theBrokenSet_spellingsOfOneGenre_areOneGenre() = runTest {
        val repository = scannedRepository()
        val dreamPop = repository.genre("Dream Pop").id
        val artist = repository.observeArtistsByGenre(dreamPop).first().getOrNull().orEmpty().single()

        val songs = repository.observeSongsByGenreAndArtist(dreamPop, artist.id).first().getOrNull().orEmpty()

        assertEquals(setOf("Text", "Spelled Apart"), songs.map { it.title }.toSet())
    }

    @Test
    fun theBrokenSet_untaggedSongs_areInUnknownGenre() = runTest {
        val repository = scannedRepository()
        val unknown = repository.genre(null).id

        val artists = repository.observeArtistsByGenre(unknown).first().getOrNull().orEmpty()

        assertTrue(artists.isNotEmpty())
    }

    @Test
    fun aGenreThatIsntThere_isNotFound_andEnds() = runTest {
        val repository = scannedRepository()
        val missing = GenreId(Long.MAX_VALUE)

        assertEquals(
            listOf(LibraryError.GenreNotFound(missing)),
            repository.observeArtistsByGenre(missing).toList().map { it.leftOrNull() },
        )
        assertEquals(LibraryError.GenreNotFound(missing), repository.getGenre(missing).leftOrNull())
    }

    private fun uriOf(number: Int) = "content://$number"

    /** What MediaStore would list for a fixture, with no genre: the tags give it, as pass 2 reads them. */
    private fun audioFileOf(number: Int, fixture: AudioFixture): AudioFile {
        val tags = tagsOf(fixture.content)
        val length = fixture.bytes().size.toLong()
        val folder = "Music/${fixture.path.substringBeforeLast('/')}/"
        return AudioFile(
            file = SongFile(number.toLong(), uriOf(number), folder, fixture.fileName, length, DATE),
            title = tags?.title,
            artist = tags?.artist,
            albumArtist = tags?.albumArtist,
            album = tags?.album,
            trackNumber = tags?.track?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = null,
            year = tags?.year?.toIntOrNull(),
            compilation = tags?.compilation?.takeIf { it },
            durationMillis = length,
        )
    }

    /**
     * A fixture's genres as the parser reads them. Its own tests cover every form; here, the numbers the broken set
     * writes are the only ones named.
     */
    private fun genresAsParsed(tags: Tags?): List<String> = tags?.genre.orEmpty()
        .split('\u0000')
        .map { ID3V1_NAMES[it.removeSurrounding("(", ")")] ?: it }
        .filter { it.isNotBlank() }

    private fun tagsOf(content: FixtureContent): Tags? = when (content) {
        is FixtureContent.Mp3 -> when (val tags = content.tags) {
            Mp3Tags.None -> null
            is Mp3Tags.Id3v1 -> tags.tags
            is Mp3Tags.Id3v2 -> tags.tags
        }

        is FixtureContent.M4a -> content.tags

        is FixtureContent.Truncated -> tagsOf(content.of)

        else -> null
    }

    private companion object {
        const val DATE = 100L
        val ID3V1_NAMES = mapOf("13" to "Pop", "17" to "Rock")
    }
}
