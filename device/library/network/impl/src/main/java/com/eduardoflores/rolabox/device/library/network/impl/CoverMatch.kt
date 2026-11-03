package com.eduardoflores.rolabox.device.library.network.impl

import arrow.core.raise.nullable
import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** MusicBrainz's own id of Various Artists, the artist of its compilations. */
internal const val VARIOUS_ARTISTS_ID = "89ad4ac3-39f7-470e-963a-56509c546377"

/** The lowest score of MusicBrainz's search that a match can have (ADR-027, How a match is judged). */
internal const val MIN_SCORE = 95

private val Punctuation = Regex("[\\p{P}\\p{S}]+")
private val Whitespace = Regex("\\s+")

/** A release group of MusicBrainz's answer, with what the match reads of it. */
internal data class ReleaseGroup(val id: String, val score: Int, val title: String, val credits: List<Credit>)

/** One artist of a release group's credit: the name it is credited as, what joins it to the next, and who it is. */
internal data class Credit(val name: String, val joinPhrase: String, val artistId: String, val artistName: String)

/**
 * The release group of [answer] that is [album], or `null` when none is, or more than one is: a wrong cover is worse
 * than none (ADR-027). Its title and artist must be equal to the album's, after [normalized] on both sides, with a
 * score of at least [MIN_SCORE].
 */
internal fun matchOf(answer: List<ReleaseGroup>, album: CoverQuery): ReleaseGroup? {
    val title = normalized(album.title)
    return answer.singleOrNull { it.score >= MIN_SCORE && normalized(it.title) == title && it.isBy(album.artist) }
}

private fun ReleaseGroup.isBy(artist: CoverArtist): Boolean = when (artist) {
    CoverArtist.Various -> credits.any { it.artistId == VARIOUS_ARTISTS_ID }

    is CoverArtist.Named -> {
        val wanted = normalizedArtist(artist.name)
        val names = listOfNotNull(
            credits.joinToString("") { it.name + it.joinPhrase },
            credits.firstOrNull()?.name,
            credits.firstOrNull()?.artistName,
        )
        names.any { normalizedArtist(it) == wanted }
    }
}

/** A name as a match compares it: Unicode NFKC, lower case, punctuation dropped and whitespace collapsed. */
internal fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(Punctuation, " ")
    .replace(Whitespace, " ")
    .trim()

/** An artist's name as a match compares it: [normalized], and without a leading "The". */
internal fun normalizedArtist(name: String): String = normalized(name).removePrefix("the ")

private val Lenient = Json { ignoreUnknownKeys = true }

/** The release groups of MusicBrainz's answer to a search, or `null` when it isn't one. */
internal fun parseReleaseGroups(json: String): List<ReleaseGroup>? = nullable {
    val root = try {
        Lenient.parseToJsonElement(json)
    } catch (_: SerializationException) {
        null
    }
    val groups = ((root as? JsonObject)?.get("release-groups") as? JsonArray).bind()
    groups.map { element -> (element as? JsonObject)?.toReleaseGroup().bind() }
}

private fun JsonObject.toReleaseGroup(): ReleaseGroup? = nullable {
    ReleaseGroup(
        id = string("id").bind(),
        score = (get("score") as? JsonPrimitive)?.intOrNull ?: 0,
        title = string("title").bind(),
        credits = (get("artist-credit") as? JsonArray).orEmpty().map { credit ->
            (credit as? JsonObject)?.toCredit().bind()
        },
    )
}

private fun JsonObject.toCredit(): Credit? = nullable {
    val artist = (get("artist") as? JsonObject).bind()
    Credit(
        name = string("name").bind(),
        joinPhrase = string("joinphrase").orEmpty(),
        artistId = artist.string("id").bind(),
        artistName = artist.string("name").bind(),
    )
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf {
    it.isString
}?.contentOrNull

/**
 * The search for [album], in the query syntax of MusicBrainz: its title as a phrase, and its artist as one, or the id
 * of Various Artists.
 */
internal fun searchQueryOf(album: CoverQuery): String {
    val artist = when (val artist = album.artist) {
        CoverArtist.Various -> "arid:$VARIOUS_ARTISTS_ID"
        is CoverArtist.Named -> "artist:${phrase(artist.name)}"
    }
    return "releasegroup:${phrase(album.title)} AND $artist"
}

private fun phrase(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
