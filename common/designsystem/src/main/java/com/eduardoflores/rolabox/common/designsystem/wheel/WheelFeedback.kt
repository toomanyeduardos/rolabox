package com.eduardoflores.rolabox.common.designsystem.wheel

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the user feels and hears for a wheel event: a tick per step of a turn, a stronger one for a press or the
 * start of a hold, and nothing for a release. Each one [vibrate]s, and also [click]s when [clicks] says the
 * wheel clicks. The vibration doesn't depend on it, since it is the wheel's feel.
 */
internal class WheelFeedback(
    private val scope: CoroutineScope,
    private val clicks: () -> Boolean,
    private val vibrate: (WheelHaptic) -> Unit,
    private val click: (WheelHaptic) -> Unit,
) {
    fun perform(event: WheelEvent) {
        val ticks = event.tickCount()
        if (ticks > 0) {
            // Spaced out, since the system folds ticks that arrive together into one.
            scope.launch {
                repeat(ticks) {
                    feel(WheelHaptic.Tick)
                    delay(TickSpacing)
                }
            }
        } else {
            event.haptic()?.let(::feel)
        }
    }

    private fun feel(haptic: WheelHaptic) {
        vibrate(haptic)
        if (clicks()) click(haptic)
    }
}

private val TickSpacing = 12.milliseconds
