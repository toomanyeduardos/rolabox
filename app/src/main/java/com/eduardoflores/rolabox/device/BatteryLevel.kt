package com.eduardoflores.rolabox.device

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** The system's battery level, from 0 to 1, kept up to date while the caller is composed. */
@Composable
internal fun rememberBatteryLevel(): Float {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(1f) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.batteryLevel()?.let { level = it }
            }
        }

        // ACTION_BATTERY_CHANGED is sticky, so registering also returns the current level.
        // The IDE's WrongConstant inspection rejects ContextCompat's own flag here; Gradle's lint accepts it.
        @SuppressLint("WrongConstant")
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.batteryLevel()?.let { level = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

private fun Intent.batteryLevel(): Float? {
    val current = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (current >= 0 && scale > 0) current.toFloat() / scale else null
}
