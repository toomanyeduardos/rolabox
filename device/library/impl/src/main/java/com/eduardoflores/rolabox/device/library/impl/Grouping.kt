package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.GenreGroup
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import java.util.Locale

/** Between the parts of a key. It can't be typed in a tag by accident. */
private const val KEY_SEPARATOR = '\u001F'

private val WHITESPACE = Regex("\\s+")

/** The tags that mean "no one artist", as the album's artist or the song's. */
private val VARIOUS_NAMES = setOf("various artists", "various")

private const val SORT_PREFIX = "the "

private val UNKNOWN_ARTIST = ArtistGroup(key = "", name = null, sortName = null)

private val VARIOUS_ARTISTS = ArtistGroup(
    key = "${KEY_SEPARATOR}various",
    name = null,
    sortName = "various artists",
    isVarious = true,
)

/** The genre of songs with none, which the screens call Unknown Genre and sort last. */
private val UNKNOWN_GENRE = GenreGroup(key = "", name = null)

/** A song with its fields resolved, before the rules have placed it. [id] is null for a file new to the index. */
internal data class SongDraft(
    val id: SongId?,
    val file: SongFile,
    val metadata: SongMetadata,
    val hasEmbeddedPicture: Boolean?,
    val tagsReadAt: Long?,
    val isProtected: Boolean = false,
)

/**
 * The grouping rules (ADR-024, Grouping), over the whole library since what an album is depends on all its songs. The
 * writes are in the order of [drafts]. The result depends only on the songs, so a rescan of the same songs changes
 * nothing, and a key that stays keeps its id.
 */
internal fun groupSongs(drafts: List<SongDraft>): List<SongWrite> {
    val looseArtists = looseArtistsByAlbumAndFolder(drafts)
    val spellings = Spellings(drafts, looseArtists)
    val placed = drafts.map { draft ->
        val albumArtistKey = albumArtistKeyOf(draft, looseArtists)
        val albumArtist = spellings.artist(albumArtistKey)
        val albumTitle = normalized(draft.metadata.album.value)
        val album = if (albumTitle == null) {
            AlbumGroup(KEY_SEPARATOR.toString(), title = null, year = null, artist = UNKNOWN_ARTIST)
        } else {
            AlbumGroup(
                key = "$albumTitle$KEY_SEPARATOR$albumArtistKey",
                title = spellings.album(albumTitle),
                year = spellings.yearOf(albumTitle, albumArtistKey),
                artist = albumArtist,
            )
        }
        Placed(draft, spellings.artist(songArtistKeyOf(draft.metadata)), album)
    }
    val shown = shownOfEachSong(placed)
    return placed.map { (draft, artist, album) ->
        SongWrite(
            id = draft.id,
            file = draft.file,
            metadata = draft.metadata,
            artist = artist,
            album = album,
            hasEmbeddedPicture = draft.hasEmbeddedPicture,
            tagsReadAt = draft.tagsReadAt,
            isDuplicate = duplicateKeyOf(draft, album)?.let { shown[it] !== draft } == true,
            genres = genreKeysOf(draft.metadata).map(spellings::genre).ifEmpty { listOf(UNKNOWN_GENRE) },
            isProtected = draft.isProtected,
        )
    }
}

private data class Placed(val draft: SongDraft, val artist: ArtistGroup, val album: AlbumGroup)

/** Lower case and single spaces, which is all that makes two names one (ADR-024, Grouping). Blank is no name. */
private fun normalized(name: String?): String? = name?.let(::cleaned)?.lowercase(Locale.ROOT)?.ifEmpty { null }

private fun cleaned(name: String): String = name.trim().replace(WHITESPACE, " ")

/** A song's genres by their keys, each once: two names are one genre as two artists' are. */
private fun genreKeysOf(metadata: SongMetadata): List<String> =
    metadata.genres.value.orEmpty().mapNotNull(::normalized).distinct()

/** A song's own artist: its tag, or the album's artist when it has none. A tag of "Various Artists" names no one. */
private fun songArtistKeyOf(metadata: SongMetadata): String =
    personKey(metadata.artist.value) ?: personKey(metadata.albumArtist.value) ?: UNKNOWN_ARTIST.key

private fun personKey(name: String?): String? = normalized(name)?.takeUnless { it in VARIOUS_NAMES }

/**
 * The album artist's key: the tag, then the compilation flag. A song with neither is placed by the other such songs
 * with its title in its folder: one artist there is the album's, and several are Various Artists. The folder only
 * tells apart two artists' albums of the same name, so tagged songs are one album wherever they are.
 */
private fun albumArtistKeyOf(draft: SongDraft, looseArtists: Map<LooseAlbum, Set<String>>): String {
    val metadata = draft.metadata
    val tagged = normalized(metadata.albumArtist.value)
    return when {
        tagged != null -> if (tagged in VARIOUS_NAMES) VARIOUS_ARTISTS.key else tagged

        metadata.compilation.value == true || normalized(metadata.artist.value) in VARIOUS_NAMES -> VARIOUS_ARTISTS.key

        else -> {
            val artists = looseArtists[looseAlbumOf(draft)].orEmpty()
            if (artists.size > 1) VARIOUS_ARTISTS.key else artists.firstOrNull() ?: UNKNOWN_ARTIST.key
        }
    }
}

private data class LooseAlbum(val title: String?, val folder: String)

private fun looseAlbumOf(draft: SongDraft) = LooseAlbum(normalized(draft.metadata.album.value), draft.file.relativePath)

private fun looseArtistsByAlbumAndFolder(drafts: List<SongDraft>): Map<LooseAlbum, Set<String>> {
    val artists = HashMap<LooseAlbum, MutableSet<String>>()
    drafts.filter { normalized(it.metadata.albumArtist.value) == null && it.metadata.compilation.value != true }
        .forEach { draft ->
            val set = artists.getOrPut(looseAlbumOf(draft)) { HashSet() }
            personKey(draft.metadata.artist.value)?.let(set::add)
        }
    return artists
}

/** The names a row shows: of the spellings of one key, the one on most songs, the same one on every scan. */
private class Spellings(drafts: List<SongDraft>, looseArtists: Map<LooseAlbum, Set<String>>) {
    private val artists = HashMap<String, MutableMap<String, Int>>()
    private val albums = HashMap<String, MutableMap<String, Int>>()
    private val genres = HashMap<String, MutableMap<String, Int>>()
    private val years: Map<Pair<String, String>, Int>

    init {
        drafts.forEach { draft ->
            count(artists, draft.metadata.artist.value)
            count(artists, draft.metadata.albumArtist.value)
            count(albums, draft.metadata.album.value)
            draft.metadata.genres.value.orEmpty().distinctBy(::normalized).forEach { count(genres, it) }
        }
        years = drafts.mapNotNull { draft ->
            val title = normalized(draft.metadata.album.value)
            val year = draft.metadata.year.value
            if (title == null || year == null) null else (title to albumArtistKeyOf(draft, looseArtists)) to year
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> mostCommon(values) }
    }

    private fun count(into: HashMap<String, MutableMap<String, Int>>, name: String?) {
        val key = normalized(name) ?: return
        into.getOrPut(key) { HashMap() }.merge(cleaned(name.orEmpty()), 1, Int::plus)
    }

    fun artist(key: String): ArtistGroup = when (key) {
        UNKNOWN_ARTIST.key -> UNKNOWN_ARTIST
        VARIOUS_ARTISTS.key -> VARIOUS_ARTISTS
        else -> ArtistGroup(key, spelling(artists, key), key.removePrefix(SORT_PREFIX).ifEmpty { key })
    }

    fun album(key: String): String = spelling(albums, key)

    fun genre(key: String): GenreGroup = GenreGroup(key, spelling(genres, key))

    fun yearOf(title: String, artistKey: String): Int? = years[title to artistKey]

    private fun spelling(of: Map<String, Map<String, Int>>, key: String): String =
        of.getValue(key).entries.minWith(compareBy({ -it.value }, { it.key })).key

    private fun mostCommon(values: List<Int>): Int =
        values.groupingBy { it }.eachCount().entries.minWith(compareBy({ -it.value }, { it.key })).key
}

/**
 * The same song in two places: the same tags and duration. The artist has to be one the file states, so two files
 * named alike with no tags stay two songs. A title can be the file's name, which is what MediaStore's looks like when
 * the two are equal, and a duration nobody knows says nothing.
 */
private fun duplicateKeyOf(draft: SongDraft, album: AlbumGroup): List<Any?>? {
    val metadata = draft.metadata
    val stated = setOf(FieldSource.Tags, FieldSource.MediaStore)
    val duration = metadata.durationMillis.value?.takeIf { it > 0 }
    return if (metadata.title.value != null && metadata.artist.source in stated && duration != null) {
        listOf(
            normalized(metadata.title.value),
            normalized(metadata.artist.value),
            album.key,
            metadata.trackNumber.value,
            metadata.discNumber.value,
            duration,
        )
    } else {
        null
    }
}

/** Of the copies of a song, the largest file plays. On a tie the oldest row does, so the choice doesn't move. */
private fun shownOfEachSong(placed: List<Placed>): Map<List<Any?>, SongDraft> {
    val order = compareBy<SongDraft>(
        { -it.file.size },
        { it.id?.value ?: Long.MAX_VALUE },
        { it.file.mediaStoreId },
    )
    return placed.mapNotNull { (draft, _, album) -> duplicateKeyOf(draft, album)?.let { it to draft } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, copies) -> copies.minWith(order) }
}
