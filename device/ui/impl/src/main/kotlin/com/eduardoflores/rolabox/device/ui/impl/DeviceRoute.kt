package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceHost
import com.eduardoflores.rolabox.device.host.rememberScreenStack
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistAlbumsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistsKey
import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.music.ui.api.MusicMenuKey
import com.eduardoflores.rolabox.device.playback.ui.api.NowPlayingKey
import com.eduardoflores.rolabox.device.playback.ui.api.PlaybackEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey

/**
 * The device's destination (ADR-018): the device host with this product's screens. The device
 * assembles itself here (ADR-020): it gives the host its first screen and its entries, maps their
 * exits to keys, pushes on the screen stack, and handles the playback buttons.
 *
 * [musicEntries] adds the music screens, whose exits are mapped to keys here and pushed.
 * [playbackEntries] adds Now Playing, which a song click and the main menu's row push. [viewModel] acts on
 * playback for them and for ⏮ and ⏭.
 * [onOpenFullScreen] is the generic exit, which leaves the display for a full screen on the app stack.
 */
@Composable
internal fun DeviceRoute(
    viewModel: DeviceViewModel,
    musicEntries: MusicEntries,
    playbackEntries: PlaybackEntries,
    onOpenFullScreen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = rememberScreenStack(MainMenuKey)
    DeviceHost(
        stack = stack,
        title = stringResource(R.string.device_title),
        entryProvider = entryProvider {
            mainMenuEntry(
                hasLoadedSong = viewModel.hasLoadedSong,
                onNowPlayingClick = { stack.push(NowPlayingKey) },
                onMusicClick = { stack.push(MusicMenuKey) },
                onSettingsClick = { onOpenFullScreen(SettingsKey) },
            )
            musicEntries.screenStackEntries(
                scope = this,
                onArtistsClick = { stack.push(ArtistsKey) },
                onArtistClick = { artistId -> stack.push(ArtistAlbumsKey(artistId)) },
                onAllSongsClick = { artistId -> stack.push(ArtistSongsKey(artistId)) },
                onAlbumClick = { albumId -> stack.push(AlbumSongsKey(albumId)) },
                onSongClick = { songId ->
                    playbackSourceOf(stack.top)?.let { source ->
                        viewModel.onSongClick(source, SongId(songId)) { stack.push(NowPlayingKey) }
                    }
                },
            )
            playbackEntries.screenStackEntries(scope = this)
        },
        // ⏯ and the holds of ⏮ and ⏭ are received and ignored until they have their tickets.
        onPlaybackEvent = { event ->
            when (event) {
                WheelEvent.Next -> viewModel.onNext()
                WheelEvent.Previous -> viewModel.onPrevious()
                else -> Unit
            }
        },
        modifier = modifier,
    )
}
