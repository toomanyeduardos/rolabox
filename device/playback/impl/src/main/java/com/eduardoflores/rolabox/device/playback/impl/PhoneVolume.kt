package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Changes the phone's media volume (ADR-023, rule 6). A `MediaController` can read the volume of a local
 * player but not set it, and Media3 asks apps to set their own device's volume with [AudioManager]. The
 * state reads it back through the session, which follows this as it follows the phone's keys.
 */
internal class PhoneVolume @Inject constructor(@ApplicationContext context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Moves the music stream by [steps] of its own, between its ends, without the system's panel. */
    fun change(steps: Int) {
        val level = audio.getStreamVolume(STREAM) + steps
        audio.setStreamVolume(
            STREAM,
            level.coerceIn(audio.getStreamMinVolume(STREAM), audio.getStreamMaxVolume(STREAM)),
            0,
        )
    }

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
