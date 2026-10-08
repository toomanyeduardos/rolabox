package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.playback.ui.api.NowPlayingKey
import com.eduardoflores.rolabox.device.playback.ui.api.PlaybackEntries
import javax.inject.Inject

/**
 * The entries of the playback screens. This is the one place that names their concrete ViewModels, since
 * Hilt creates a ViewModel by its class. The routes take the abstract ones (ADR-021).
 */
internal class DefaultPlaybackEntries @Inject constructor() : PlaybackEntries {
    override fun screenStackEntries(scope: EntryProviderScope<NavKey>) = with(scope) {
        entry<NowPlayingKey> { NowPlayingRoute(viewModel = hiltViewModel<NowPlayingViewModelImpl>()) }
    }
}
