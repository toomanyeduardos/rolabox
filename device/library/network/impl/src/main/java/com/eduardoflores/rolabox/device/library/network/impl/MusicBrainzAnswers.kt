package com.eduardoflores.rolabox.device.library.network.impl

import arrow.core.raise.nullable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A release group of MusicBrainz's answer, with what the match reads of it (ADR-027, How a match is judged).
 * [firstReleaseDate] is as MusicBrainz writes it, `YYYY`, `YYYY-MM` or `YYYY-MM-DD`, so it sorts as text.
 */
internal data class ReleaseGroup(
    val id: String,
    val score: Int,
    val title: String,
    val credits: List<Credit>,
    val primaryType: String? = null,
    val secondaryTypes: List<String> = emptyList(),
    val firstReleaseDate: String? = null,
)

/** One artist of a release group's credit: the name it is credited as, what joins it to the next, and who it is. */
internal data class Credit(val name: String, val joinPhrase: String, val artistId: String, val artistName: String)

private val Lenient = Json { ignoreUnknownKeys = true }

/** The release groups of MusicBrainz's answer to a search, or `null` when it isn't one. */
internal fun parseReleaseGroups(json: String): List<ReleaseGroup>? = nullable {
    val groups = (parse(json)?.get("release-groups") as? JsonArray).bind()
    groups.map { element -> (element as? JsonObject)?.toReleaseGroup().bind() }
}

/**
 * The track counts of the releases in MusicBrainz's answer to a search of releases, or `null` when it isn't one. A
 * release with no count is left out.
 */
internal fun parseTrackCounts(json: String): List<Int>? = nullable {
    val releases = (parse(json)?.get("releases") as? JsonArray).bind()
    releases.mapNotNull { release -> ((release as? JsonObject)?.get("track-count") as? JsonPrimitive)?.intOrNull }
}

private fun parse(json: String): JsonObject? = try {
    Lenient.parseToJsonElement(json) as? JsonObject
} catch (_: SerializationException) {
    null
}

private fun JsonObject.toReleaseGroup(): ReleaseGroup? = nullable {
    ReleaseGroup(
        id = string("id").bind(),
        score = (get("score") as? JsonPrimitive)?.intOrNull ?: 0,
        title = string("title").bind(),
        credits = (get("artist-credit") as? JsonArray).orEmpty().map { credit ->
            (credit as? JsonObject)?.toCredit().bind()
        },
        primaryType = string("primary-type"),
        secondaryTypes = (get("secondary-types") as? JsonArray).orEmpty().mapNotNull { it.text() },
        firstReleaseDate = string("first-release-date")?.takeIf { it.isNotBlank() },
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

private fun JsonObject.string(key: String): String? = get(key)?.text()

private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
