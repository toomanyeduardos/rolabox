package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the phone's default network is, as far as an episode's bytes are concerned. */
internal enum class NetworkKind { None, Unmetered, Metered }

/** The phone's default network, which the tests set. */
internal interface PhoneNetwork {
    val kind: StateFlow<NetworkKind>
}

/** The default network, from `ConnectivityManager`'s callback, which answers with the current one when registered. */
internal class ConnectivityPhoneNetwork @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) : PhoneNetwork {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override val kind: StateFlow<NetworkKind> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.kind())
            }

            override fun onLost(network: Network) {
                trySend(NetworkKind.None)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        // What changed before the callback was registered.
        trySend(current())
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, current())

    private fun current(): NetworkKind =
        connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)?.kind() ?: NetworkKind.None
}

internal fun NetworkCapabilities.kind(): NetworkKind = when {
    !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.None
    hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.Unmetered
    else -> NetworkKind.Metered
}

/** The network now, and the Use Mobile Data setting, which together decide whether an episode's bytes may come. */
internal data class EpisodeNetworkState(val kind: NetworkKind, val mobileData: Boolean) {
    /** Use Mobile Data is Off on a metered network (ADR-031, Mobile data). */
    val needsWifi: Boolean get() = kind == NetworkKind.Metered && !mobileData

    /** What an episode that ran out of bytes waits for now, or `null` when they may come (ADR-031, rule 15). */
    val waitsFor: PlaybackError.Waiting?
        get() = when {
            kind == NetworkKind.None -> PlaybackError.NoConnection
            needsWifi -> PlaybackError.MobileDataOff
            else -> null
        }
}

/**
 * Whether an episode's bytes may be fetched now (ADR-031, rule 9): the phone's network and the Use Mobile Data setting.
 * The download's requirements, the player's stream and `play` all follow it, so the rule is in one place. A setting
 * that can't be read is its default, On.
 */
@Singleton
internal class EpisodeNetworkPolicy @Inject constructor(
    network: PhoneNetwork,
    userData: UserDataRepository,
    @ApplicationScope scope: CoroutineScope,
) : EpisodeNetwork {
    val state: StateFlow<EpisodeNetworkState> = combine(
        network.kind,
        userData.observeEpisodesOnMobileData().map { it.getOrNull() != EpisodesOnMobileData.OFF },
    ) { kind, mobileData -> EpisodeNetworkState(kind, mobileData) }
        .stateIn(scope, SharingStarted.Eagerly, EpisodeNetworkState(network.kind.value, mobileData = true))

    override fun observeNeedsWifi(): Flow<Boolean> = state.map { it.needsWifi }.distinctUntilChanged()
}
