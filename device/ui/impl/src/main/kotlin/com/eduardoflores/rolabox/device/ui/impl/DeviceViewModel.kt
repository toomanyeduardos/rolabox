package com.eduardoflores.rolabox.device.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import kotlinx.coroutines.flow.StateFlow

/** Why a song click couldn't play, as the device tells the user (ADR-007, rule 8). */
internal enum class PlayFailure {
    /** The list the song is in couldn't be read from the library. */
    LibraryUnavailable,

    /** The song is no longer in the list it was clicked on. */
    SongNotInList,
}

/**
 * What the device does with playback: the song click, the ⏮, ⏭ and ⏯ buttons, and whether something is
 * loaded and playing, for the main menu's row and the header's record (ADR-018). [DeviceViewModelImpl]
 * implements it (ADR-021).
 */
internal abstract class DeviceViewModel : ViewModel() {
    /** Whether a song is loaded, so the main menu shows its Now Playing row. */
    abstract val hasLoadedSong: StateFlow<Boolean>

    /** Whether the loaded song is playing, so the header's record spins. */
    abstract val isPlaying: StateFlow<Boolean>

    /**
     * Whether each row of the main menu that can be hidden is shown, a preference (ADR-020, rule 15). Every
     * row is shown until the preferences have loaded, and if they can't be read.
     */
    abstract val mainMenuRows: StateFlow<Map<MainMenuRow, MainMenuRowVisibility>>

    /** Why the last song click couldn't play, until [onPlayFailureShown]. `null` when there is nothing to show. */
    abstract val playFailure: StateFlow<PlayFailure?>

    /**
     * Plays the list [source] from [songId], in place of what was loaded, and then calls [onPlaying]. If playing
     * fails it isn't called, and [playFailure] says why.
     */
    abstract fun onSongClick(source: PlaybackSource, songId: SongId, onPlaying: () -> Unit)

    /** The message of [playFailure] was shown, so it isn't shown again. */
    abstract fun onPlayFailureShown()

    /** ⏭: starts the next song at 0:00. Does nothing on the last song, or when nothing is loaded. */
    abstract fun onNext()

    /** ⏮: starts the previous song at 0:00. Does nothing on the first song, or when nothing is loaded. */
    abstract fun onPrevious()

    /**
     * ⏯ where it isn't the highlighted row's: pauses the loaded song when it is playing, and plays it from
     * where it was when it is paused. Does nothing when nothing is loaded.
     */
    abstract fun onPlayPause()

    /** ⏮ or ⏭ is held: seeks in the loaded song towards [direction] until [onSeekEnd]. Nothing if none is loaded. */
    abstract fun onSeekStart(direction: SeekDirection)

    /** The held ⏮ or ⏭ was released. */
    abstract fun onSeekEnd()
}
