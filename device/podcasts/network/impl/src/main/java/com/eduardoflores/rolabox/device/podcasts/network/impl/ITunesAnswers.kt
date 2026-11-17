package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.raise.nullable
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** The kind of an episode the directory lists as audio. Another, `video`, isn't one Rolabox plays. */
private const val AUDIO_CONTENT_TYPE = "audio"

private val Lenient = Json { ignoreUnknownKeys = true }

/**
 * The shows of the directory's answer to `entity=podcast`, in its order, or `null` when it isn't one. A show with no
 * `collectionId`, name or `feedUrl` is dropped: it can't be played (ADR-031, The directory).
 */
internal fun parseShows(json: String): List<DirectoryShow>? =
    resultsOf(json)?.mapNotNull { (it as? JsonObject)?.toShow() }

/**
 * The episodes of the directory's answer to `entity=podcastEpisode`, each with its show, in its order, or `null` when
 * it isn't one. An episode with no audio URL, whose show has no `feedUrl`, or that isn't audio, is dropped.
 */
internal fun parseEpisodes(json: String): List<FoundEpisode>? = resultsOf(json)?.mapNotNull { result ->
    val episode = result as? JsonObject ?: return@mapNotNull null
    val contentType = episode.string("episodeContentType")
    if (contentType != null && contentType != AUDIO_CONTENT_TYPE) return@mapNotNull null
    nullable {
        FoundEpisode(
            show = episode.toShow().bind(),
            episode = DirectoryEpisode(
                guid = episode.string("episodeGuid"),
                title = episode.string("trackName"),
                publishedAt = episode.string("releaseDate")?.let(::instantOrNull),
                length = episode.long("trackTimeMillis")?.takeIf { it > 0 }?.milliseconds,
                // The directory's description is plain text already.
                description = episode.string("description"),
                audioUrl = episode.string("episodeUrl").bind(),
            ),
        )
    }
}

private fun resultsOf(json: String): JsonArray? = try {
    (Lenient.parseToJsonElement(json) as? JsonObject)?.get("results") as? JsonArray
} catch (_: SerializationException) {
    null
}

// What a result says of its show. An episode's result has no artistName, so its creator is the feed's, once read.
private fun JsonObject.toShow(): DirectoryShow? = nullable {
    DirectoryShow(
        directoryId = DirectoryId.itunes(long("collectionId").bind()),
        title = string("collectionName").bind(),
        creator = string("artistName"),
        feedUrl = string("feedUrl").bind(),
    )
}

private fun instantOrNull(text: String): Instant? = try {
    Instant.parse(text)
} catch (_: DateTimeParseException) {
    null
}

/** The text at [key], or `null` when it is missing, isn't text, or is blank. */
private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
