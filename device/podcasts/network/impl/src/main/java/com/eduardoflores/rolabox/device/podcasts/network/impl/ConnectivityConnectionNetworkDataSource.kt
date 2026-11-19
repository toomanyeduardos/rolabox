package com.eduardoflores.rolabox.device.podcasts.network.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.eduardoflores.rolabox.device.podcasts.network.api.ConnectionNetworkDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone's default network reaches the internet, from `ConnectivityManager`'s callback, as playback reads
 * it for downloads. Registered only while it is collected.
 */
internal class ConnectivityConnectionNetworkDataSource @Inject constructor(@ApplicationContext context: Context) :
    ConnectionNetworkDataSource {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun observeConnected(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(isConnected())
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun isConnected(): Boolean = connectivity.activeNetwork
        ?.let(connectivity::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
