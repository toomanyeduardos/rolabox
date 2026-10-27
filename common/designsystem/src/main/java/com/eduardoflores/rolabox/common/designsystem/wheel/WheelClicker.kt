package com.eduardoflores.rolabox.common.designsystem.wheel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eduardoflores.rolabox.common.designsystem.R

/**
 * Plays the wheel's click (ADR-018). The sounds are loaded once and played from memory, so a click starts with
 * its step. They play as system sounds, like the keyboard's, at the system volume. No audio focus is asked
 * for, so music from this or another app is never ducked or paused. In silent and vibrate mode it plays
 * nothing, which the system doesn't promise for these sounds on every phone. A click that comes while
 * [MAX_STREAMS] are still sounding takes the place of the oldest, so a fast spin drops clicks and never
 * queues them.
 */
internal class WheelClicker(context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val tick = soundPool.load(context, R.raw.ds_wheel_tick, 1)
    private val press = soundPool.load(context, R.raw.ds_wheel_press, 1)

    /** Plays the click for [haptic]. Until the sounds have loaded, nothing plays. */
    fun play(haptic: WheelHaptic) {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val sound = when (haptic) {
            WheelHaptic.Tick -> tick
            WheelHaptic.Press -> press
        }
        soundPool.play(sound, 1f, 1f, 1, 0, 1f)
    }

    fun release() = soundPool.release()

    private companion object {
        const val MAX_STREAMS = 2
    }
}

/** A [WheelClicker] while [enabled], released when it is turned off or leaves the composition. */
@Composable
internal fun rememberWheelClicker(enabled: Boolean): WheelClicker? {
    val context = LocalContext.current
    val clicker = remember(context, enabled) { if (enabled) WheelClicker(context) else null }
    DisposableEffect(clicker) { onDispose { clicker?.release() } }
    return clicker
}
