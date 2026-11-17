package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import kotlin.time.Duration

/** The share of its length at which an episode is played: its end is mostly ads and credits (ADR-031, rule 6). */
internal const val PLAYED_SHARE = 0.9

/** Whether [position] makes an episode of [length] played. With no length, only the end does, which isn't this. */
internal fun reachesPlayed(position: Duration, length: Duration?): Boolean =
    length != null && length.isPositive() && position >= length * PLAYED_SHARE

/** The length the player knew, which replaces the feed's, else the feed's (ADR-031). */
internal val EpisodeRecord.length: Duration?
    get() = progress?.playerLength ?: feedLength

internal val EpisodeRecord.position: Duration
    get() = progress?.position ?: Duration.ZERO

internal val EpisodeRecord.playedState: PlayedState
    get() = when {
        progress?.isPlayed == true -> PlayedState.Played
        position.isPositive() -> PlayedState.PartlyPlayed
        else -> PlayedState.Unplayed
    }
