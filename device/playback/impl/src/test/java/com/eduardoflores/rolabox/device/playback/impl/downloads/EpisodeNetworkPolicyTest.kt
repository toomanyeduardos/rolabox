package com.eduardoflores.rolabox.device.playback.impl.downloads

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether an episode's bytes may come, from the network and Use Mobile Data (ADR-031, rules 9 and 15). */
class EpisodeNetworkPolicyTest {
    private val network = object : PhoneNetwork {
        override val kind = MutableStateFlow(NetworkKind.Unmetered)
    }
    private val userData = FakeUserDataRepository()

    @Test
    fun episodesNeedWifi_onlyOnAMeteredNetworkWithMobileDataOff() = runTest {
        val policy = EpisodeNetworkPolicy(network, userData, backgroundScope)

        network.kind.value = NetworkKind.Metered
        runCurrent()
        assertFalse(policy.observeNeedsWifi().first())

        userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF)
        runCurrent()
        assertTrue(policy.observeNeedsWifi().first())
        assertEquals(PlaybackError.MobileDataOff, policy.state.value.waitsFor)

        network.kind.value = NetworkKind.Unmetered
        runCurrent()
        assertFalse(policy.observeNeedsWifi().first())
        assertEquals(null, policy.state.value.waitsFor)
    }

    @Test
    fun withNoNetwork_episodesNeedAConnection_notWifi() = runTest {
        userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF)
        val policy = EpisodeNetworkPolicy(network, userData, backgroundScope)

        network.kind.value = NetworkKind.None
        runCurrent()

        assertFalse(policy.observeNeedsWifi().first())
        assertEquals(PlaybackError.NoConnection, policy.state.value.waitsFor)
    }

    @Test
    fun aSettingThatCantBeRead_isOn() = runTest {
        userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF)
        userData.setReadError(StorageError.Corrupted)
        network.kind.value = NetworkKind.Metered
        val policy = EpisodeNetworkPolicy(network, userData, backgroundScope)
        runCurrent()

        assertFalse(policy.observeNeedsWifi().first())
    }
}
