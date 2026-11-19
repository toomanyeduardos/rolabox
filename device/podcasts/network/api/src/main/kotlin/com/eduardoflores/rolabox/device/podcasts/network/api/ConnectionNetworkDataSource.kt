package com.eduardoflores.rolabox.device.podcasts.network.api

import kotlinx.coroutines.flow.Flow

/**
 * Whether the phone has a network that reaches the internet, for search to come back by itself when the connection
 * does (ADR-031, Search). It sends nothing: it reads the phone's state.
 */
interface ConnectionNetworkDataSource {
    /** The state now when collected, and each change after it. */
    fun observeConnected(): Flow<Boolean>
}
