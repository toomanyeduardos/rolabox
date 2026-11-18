package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * The phone's network on Robolectric, which has one that isn't validated unless told. Media3's downloads wait for a
 * validated network, and Mobile Data looks at whether it is metered (ADR-031).
 */
internal object TestNetwork {
    /** An unmetered, validated network: Wi-Fi. */
    fun wifi(context: Context) = set(context, NetworkCapabilities.TRANSPORT_WIFI, metered = false)

    /** A metered, validated network: mobile data. */
    fun mobile(context: Context) = set(context, NetworkCapabilities.TRANSPORT_CELLULAR, metered = true)

    private fun set(context: Context, transport: Int, metered: Boolean) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            addTransportType(transport)
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!metered) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        // What Media3's requirements read: the active network's info, whose type says whether it is metered.
        @Suppress("DEPRECATION") // NetworkInfo is what Media3 reads, and what Robolectric decides metered from.
        val type = if (metered) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
        @Suppress("DEPRECATION")
        shadowOf(connectivity).setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                type,
                0,
                true,
                NetworkInfo.State.CONNECTED,
            ),
        )
        val network = connectivity.activeNetwork
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities)
        shadowOf(connectivity).networkCallbacks.forEach { it.onCapabilitiesChanged(network!!, capabilities) }
    }
}
