package com.eduardoflores.rolabox.device.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import kotlinx.coroutines.flow.StateFlow

/**
 * What the device does with playback: the song click, the ⏮ and ⏭ buttons, and whether something is
 * loaded for the main menu's row (ADR-018). [DeviceViewModelImpl] implements it (ADR-021).
 */
internal abstract class DeviceViewModel : ViewModel() {
    /** Whether a song is loaded, so the main menu shows its Now Playing row. */
    abstract val hasLoadedSong: StateFlow<Boolean>

    /**
     * Plays the list [source] from [songId], in place of what was loaded, and then calls [onPlaying]. It isn't
     * called if playing fails.
     */
    abstract fun onSongClick(source: PlaybackSource, songId: SongId, onPlaying: () -> Unit)

    /** ⏭: starts the next song at 0:00. Does nothing on the last song, or when nothing is loaded. */
    abstract fun onNext()

    /** ⏮: starts the previous song at 0:00. Does nothing on the first song, or when nothing is loaded. */
    abstract fun onPrevious()
}
