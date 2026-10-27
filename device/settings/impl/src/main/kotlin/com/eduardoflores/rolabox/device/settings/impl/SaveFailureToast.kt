package com.eduardoflores.rolabox.device.settings.impl

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * A change that couldn't be saved says so, once, in a toast, and then calls [onShow]: the value on the
 * row stays what it was, and a failed action is never silent (ADR-007). A toast is the system's and is
 * drawn over the device, as for a song that can't play (ADR-018).
 */
@Composable
internal fun SaveFailureToast(saveFailed: Boolean, onShow: () -> Unit) {
    val context = LocalContext.current
    val currentOnShow by rememberUpdatedState(onShow)
    LaunchedEffect(saveFailed) {
        if (!saveFailed) return@LaunchedEffect
        Toast.makeText(context, R.string.settings_save_failed, Toast.LENGTH_SHORT).show()
        currentOnShow()
    }
}
