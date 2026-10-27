package com.eduardoflores.rolabox.device.ui.impl

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.common.designsystem.wheel.HoldState
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
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.ui.api.NowPlayingKey
import com.eduardoflores.rolabox.device.playback.ui.api.PlaybackEntries
import com.eduardoflores.rolabox.device.settings.api.AboutKey
import com.eduardoflores.rolabox.device.settings.api.MainMenuSettingsKey
import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey

/**
 * The device's destination (ADR-018): the device host with this product's screens. The device
 * assembles itself here (ADR-020): it gives the host its first screen and its entries, maps their
 * exits to keys, pushes on the screen stack, handles the playback buttons, and asks the host to take
 * Now Playing off the display when the queue ends.
 *
 * [musicEntries] adds the music screens, whose exits are mapped to keys here and pushed.
 * [playbackEntries] adds Now Playing, which a song click and the main menu's row push. [viewModel] acts on
 * playback for them and for the playback buttons, and gives the host the state of the header's record. A song
 * click that couldn't play is told in a toast.
 * [settingsEntries] adds the settings screens, which the main menu's Settings row and their own rows push.
 * [onOpenFullScreen] is the generic exit, which leaves the display for a full screen on the app stack. Only a
 * contributed settings section's row uses it (ADR-020, rule 17).
 */
@Composable
internal fun DeviceRoute(
    viewModel: DeviceViewModel,
    musicEntries: MusicEntries,
    playbackEntries: PlaybackEntries,
    settingsEntries: SettingsEntries,
    onOpenFullScreen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = rememberScreenStack(MainMenuKey)
    val nowPlayingShown by viewModel.hasLoadedSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playFailure by viewModel.playFailure.collectAsStateWithLifecycle()
    val mainMenuRows by viewModel.mainMenuRows.collectAsStateWithLifecycle()
    val clicks by viewModel.clicks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A song click that couldn't play says why (ADR-007, rule 7), once for each failure.
    LaunchedEffect(playFailure) {
        val failure = playFailure ?: return@LaunchedEffect
        Toast.makeText(context, failure.message, Toast.LENGTH_SHORT).show()
        viewModel.onPlayFailureShown()
    }
    // The queue ended: nothing is loaded, so Now Playing leaves the display if it is showing (ADR-018, rule 8).
    // Only a song that was loaded and no longer is counts, not the state before the first one is known.
    var wasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlayingShown) {
        if (wasLoaded && !nowPlayingShown) stack.popIfTop(NowPlayingKey)
        wasLoaded = nowPlayingShown
    }
    DeviceHost(
        stack = stack,
        isItemLoaded = nowPlayingShown,
        isItemPlaying = isPlaying,
        clicks = clicks,
        entryProvider = entryProvider {
            mainMenuEntry(
                shownRows = shownMenuRows(nowPlayingShown, mainMenuRows),
                onNowPlayingClick = { stack.push(NowPlayingKey) },
                onMusicClick = { stack.push(MusicMenuKey) },
                onSettingsClick = { stack.push(SettingsKey) },
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
            settingsEntries.screenStackEntries(
                scope = this,
                onMainMenuClick = { stack.push(MainMenuSettingsKey) },
                onAboutClick = { stack.push(AboutKey) },
                onOpenFullScreen = onOpenFullScreen,
            )
        },
        onPlaybackEvent = { event -> viewModel.onPlaybackEvent(event) },
        modifier = modifier,
    )
}

/**
 * What each playback button does (ADR-018, the control table). ⏯ arrives here only when the top screen
 * didn't play its highlighted row with it, so here it plays or pauses what is loaded. The host keeps the
 * other wheel events to itself and its screens.
 */
internal fun DeviceViewModel.onPlaybackEvent(event: WheelEvent) {
    when (event) {
        WheelEvent.Next -> onNext()
        WheelEvent.Previous -> onPrevious()
        WheelEvent.PlayPause -> onPlayPause()
        is WheelEvent.HoldPrevious -> onSeekHold(SeekDirection.Backward, event.state)
        is WheelEvent.HoldNext -> onSeekHold(SeekDirection.Forward, event.state)
        is WheelEvent.Turn, WheelEvent.Center, WheelEvent.Menu, WheelEvent.HoldMenu -> Unit
    }
}

private fun DeviceViewModel.onSeekHold(direction: SeekDirection, state: HoldState) = when (state) {
    HoldState.Held -> onSeekStart(direction)
    HoldState.Released -> onSeekEnd()
}

@get:StringRes
private val PlayFailure.message: Int
    get() = when (this) {
        PlayFailure.LibraryUnavailable -> R.string.device_play_failed_library
        PlayFailure.SongNotInList -> R.string.device_play_failed_song
    }
