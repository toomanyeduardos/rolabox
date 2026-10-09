package com.eduardoflores.rolabox.device.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import kotlinx.coroutines.flow.StateFlow

/** Why a song click couldn't play, as the device tells the user (ADR-007, rule 8). */
internal enum class PlayFailure {
    /** The list the song is in couldn't be read from the library. */
    LibraryUnavailable,

    /** The song is no longer in the list it was clicked on. */
    SongNotInList,
}

/**
 * What the device does with playback: the song click, the ⏮ and ⏭ buttons, and whether something is
 * loaded for the main menu's row (ADR-018). [DeviceViewModelImpl] implements it (ADR-021).
 */
internal abstract class DeviceViewModel : ViewModel() {
    /** Whether a song is loaded, so the main menu shows its Now Playing row. */
    abstract val hasLoadedSong: StateFlow<Boolean>

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
}
