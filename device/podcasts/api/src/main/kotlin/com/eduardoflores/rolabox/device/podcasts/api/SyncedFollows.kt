package com.eduardoflores.rolabox.device.podcasts.api

import com.eduardoflores.rolabox.common.sync.api.SyncedValue

/**
 * A show's Follow switch as sync keeps it (ADR-031, Syncing follows): whether it is [followed], and the [feedUrl] a
 * phone that hasn't the show reads it from. Off is kept, with when it was turned Off, as the record that wins over an
 * older On from another phone.
 */
data class Follow(val feedUrl: String, val followed: Boolean)

/**
 * Every follow, On and Off, by the directory id that names its show across phones, each with when the user last set
 * it. Only the podcasts' sync reads and writes it: the local and the network side both speak it (ADR-022, rule 7).
 */
typealias SyncedFollows = Map<DirectoryId, SyncedValue<Follow>>
