package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastEpisodeKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastOwnerKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastShowKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastsEntries
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastsKey
import javax.inject.Inject

/**
 * The entries of the podcasts screens. This is the one place that names their concrete ViewModels, since Hilt creates
 * a ViewModel by its class. The routes take the abstract ones (ADR-021). A ViewModel that needs the id of its key gets
 * it here, from the key.
 */
internal class DefaultPodcastsEntries @Inject constructor() : PodcastsEntries {
    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onSearchClick: () -> Unit,
        onOwnerClick: (ownerId: String) -> Unit,
        onShowClick: (showId: Long) -> Unit,
        onEpisodeClick: (episodeId: Long) -> Unit,
    ) = with(scope) {
        entry<PodcastsKey> {
            PodcastsRoute(
                viewModel = hiltViewModel<PodcastsViewModelImpl>(),
                onSearchClick = onSearchClick,
                onOwnerClick = onOwnerClick,
            )
        }
        entry<PodcastOwnerKey> { key ->
            OwnerRoute(
                viewModel = hiltViewModel<OwnerViewModelImpl, OwnerViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.ownerId) },
                ),
                onShowClick = onShowClick,
            )
        }
        entry<PodcastShowKey> { key ->
            ShowRoute(
                viewModel = hiltViewModel<ShowViewModelImpl, ShowViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.showId) },
                ),
                onEpisodeClick = onEpisodeClick,
            )
        }
        entry<PodcastEpisodeKey> { key ->
            EpisodeRoute(
                viewModel = hiltViewModel<EpisodeViewModelImpl, EpisodeViewModelImpl.Factory>(
                    creationCallback = { factory -> factory.create(key.episodeId) },
                ),
            )
        }
    }
}
