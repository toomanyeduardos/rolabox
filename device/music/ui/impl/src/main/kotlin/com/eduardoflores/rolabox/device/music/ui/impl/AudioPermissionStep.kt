package com.eduardoflores.rolabox.device.music.ui.impl

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Asking for the audio permission, which needs an Activity (ADR-009). The library only checks the result. */
internal class AudioPermissionStep(
    private val activity: Activity?,
    private val launcher: ActivityResultLauncher<String>,
) {
    /** Android's rationale check is false after a second denial. Before any ask it is false too: see the ViewModel. */
    fun canAskAgain(): Boolean = activity?.canAskForAudioPermission() ?: true

    fun request() = launcher.launch(audioPermission())

    fun openSettings() {
        val app = activity ?: return
        app.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
        )
    }
}

@Composable
internal fun rememberAudioPermissionStep(onResult: (canAskAgain: Boolean) -> Unit): AudioPermissionStep {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(activity?.canAskForAudioPermission() ?: true)
    }
    return remember(activity, launcher) { AudioPermissionStep(activity, launcher) }
}

private fun Activity.canAskForAudioPermission() = shouldShowRequestPermissionRationale(audioPermission())

private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}
