package com.eduardoflores.rolabox.common.designsystem.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system's animations are off, when a preview sets it. Null, the default, reads the
 * system's setting.
 */
internal val LocalAnimationsOff = staticCompositionLocalOf<Boolean?> { null }

/** Whether the user has turned the system's animations off, read once per composition. */
@Composable
internal fun rememberAnimationsOff(): Boolean {
    LocalAnimationsOff.current?.let { return it }
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
