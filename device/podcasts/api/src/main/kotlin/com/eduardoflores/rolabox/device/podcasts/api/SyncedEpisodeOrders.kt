package com.eduardoflores.rolabox.device.podcasts.api

import com.eduardoflores.rolabox.common.sync.api.SyncedValue

/**
 * The user's choice of a show's episode order, as sync keeps it (ADR-043, Syncing the choice). [Feed] is a choice
 * cleared, kept as the record that wins over an older choice from another phone: the show follows its feed again.
 */
enum class EpisodeOrderChoice {
    Newest,
    Oldest,
    Feed,
}

/** The order [this] choice sets, or `null` for [EpisodeOrderChoice.Feed], which leaves it to the feed. */
val EpisodeOrderChoice.order: EpisodeOrder?
    get() = when (this) {
        EpisodeOrderChoice.Newest -> EpisodeOrder.Newest
        EpisodeOrderChoice.Oldest -> EpisodeOrder.Oldest
        EpisodeOrderChoice.Feed -> null
    }

/**
 * Every choice the user made, by the directory id that names its show across phones, each with when the user last
 * made it. Only the podcasts' sync reads and writes it: the local and the network side both speak it (ADR-022, rule 7).
 */
typealias SyncedEpisodeOrders = Map<DirectoryId, SyncedValue<EpisodeOrderChoice>>
