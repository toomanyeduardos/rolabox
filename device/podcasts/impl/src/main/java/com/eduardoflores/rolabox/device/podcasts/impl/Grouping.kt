package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import java.util.Locale

private val WHITESPACE = Regex("\\s+")

/** A name with single spaces and no blank around it. Blank is no name: "Unknown" is never stored (ADR-031). */
internal fun cleanedName(name: String?): String? = name?.trim()?.replace(WHITESPACE, " ")?.ifEmpty { null }

/**
 * The owner that groups shows: the name ignoring case and whitespace, as an artist's key is (ADR-031, The database).
 * Shows with no owner share the empty key.
 */
internal fun ownerIdOf(name: String?): OwnerId = OwnerId(cleanedName(name)?.lowercase(Locale.ROOT).orEmpty())

/** What identifies an episode within its show: its GUID, or its enclosure URL when it has none (ADR-031, rule 2). */
internal val DirectoryEpisode.itemKey: String
    get() = guid?.takeIf { it.isNotBlank() } ?: audioUrl

/** What identifies an item of a feed within its show, as [DirectoryEpisode.itemKey] does (ADR-031, rule 2). */
internal val FeedEpisode.itemKey: String
    get() = guid?.takeIf { it.isNotBlank() } ?: audioUrl
