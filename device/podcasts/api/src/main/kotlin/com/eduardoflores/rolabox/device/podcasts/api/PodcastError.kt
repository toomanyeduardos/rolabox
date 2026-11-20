package com.eduardoflores.rolabox.device.podcasts.api

import com.eduardoflores.rolabox.common.storage.api.StorageError

/** What a call of the podcasts part can end in (ADR-031, Errors). No results is an empty answer, not an error. */
sealed interface PodcastError {
    /** There is no connection to ask the directory or a feed. */
    data object NoConnection : PodcastError

    /** The directory refused for the rate of requests (HTTP 403 or 429). */
    data object DirectoryBusy : PodcastError

    /** The directory gave any other answer that can't be used. */
    data object DirectoryFailed : PodcastError

    /** A feed couldn't be fetched, isn't RSS, or ended early. */
    data object FeedUnreadable : PodcastError

    /**
     * The publisher's server refuses the episode's audio, with a 401, 403 or 410, as it does a paid episode kept in a
     * public feed (ADR-031, Search).
     */
    data object AudioRefused : PodcastError

    data class OwnerNotFound(val id: OwnerId) : PodcastError

    data class ShowNotFound(val id: ShowId) : PodcastError

    data class EpisodeNotFound(val id: EpisodeId) : PodcastError

    /** The podcasts' database couldn't be read or written. */
    data class Storage(val cause: StorageError) : PodcastError
}
