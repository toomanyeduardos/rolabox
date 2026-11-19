package com.eduardoflores.rolabox.device.podcasts.testing

import com.eduardoflores.rolabox.device.podcasts.network.api.ConnectionNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A connection that tests turn on and off with [connected]. Connected until they do. */
@Singleton
class FakeConnectionNetworkDataSource @Inject constructor() : ConnectionNetworkDataSource {
    val connected = MutableStateFlow(true)

    override fun observeConnected(): Flow<Boolean> = connected
}
