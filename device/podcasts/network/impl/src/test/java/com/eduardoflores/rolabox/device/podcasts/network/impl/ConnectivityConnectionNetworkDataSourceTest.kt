package com.eduardoflores.rolabox.device.podcasts.network.impl

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Whether the phone is connected, from the platform's default network, as search reads it (ADR-031, Search). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConnectivityConnectionNetworkDataSourceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val network: Network = connectivity.activeNetwork!!
    private val dataSource = ConnectivityConnectionNetworkDataSource(context)

    private fun capabilities(internet: Boolean): NetworkCapabilities = ShadowNetworkCapabilities.newInstance().also {
        if (internet) shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** The phone's default network now has [capabilities], as the platform tells the registered callbacks. */
    private fun change(capabilities: NetworkCapabilities) {
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities)
        shadowOf(connectivity).networkCallbacks.forEach { it.onCapabilitiesChanged(network, capabilities) }
    }

    private fun lose() = shadowOf(connectivity).networkCallbacks.forEach { it.onLost(network) }

    @Test
    fun aDefaultNetworkThatReachesTheInternet_isConnected_andLosingIt_isNot() = runTest {
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities(internet = true))
        val seen = mutableListOf<Boolean>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.observeConnected().collect { seen += it }
        }

        lose()
        change(capabilities(internet = true))

        assertEquals(listOf(true, false, true), seen)
        collecting.cancel()
    }

    @Test
    fun aNetworkWithoutTheInternet_isNotConnected() = runTest {
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities(internet = false))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.observeConnected().collect { seen += it }
        }

        change(capabilities(internet = true))

        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun theSameStateAgain_isNotNews() = runTest {
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities(internet = true))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.observeConnected().collect { seen += it }
        }

        change(capabilities(internet = true))
        change(capabilities(internet = true))

        assertEquals(listOf(true), seen)
    }

    @Test
    fun theCallback_isRegisteredOnlyWhileCollected() = runTest {
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.observeConnected().collect {}
        }
        assertEquals(1, shadowOf(connectivity).networkCallbacks.size)

        collecting.cancel()

        assertEquals(0, shadowOf(connectivity).networkCallbacks.size)
    }
}
