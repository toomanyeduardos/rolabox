package com.eduardoflores.rolabox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint

/**
 * An activity that instrumented tests put [content] in. It is the activity that calls `setContent`,
 * so recreating it restores the content from saved state, as the system does.
 *
 * Hilt creates ViewModels only for an `@AndroidEntryPoint`, and an activity declared by the test APK
 * can't be started in the app's process, so this is part of the debug build.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content() }
    }

    companion object {
        /** What the next activity shows. Tests set it before launching the activity. */
        var content: @Composable () -> Unit = {}
    }
}
