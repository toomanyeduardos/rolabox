package com.eduardoflores.rolabox.device.settings.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Hands a link or an email over to another app: the browser for the privacy policy, the email app for feedback
 * (launch/06). This leaves Rolabox for that app, and the system's back comes back to the same device screen. It
 * opens none of Rolabox's own screens, so it isn't a full screen and doesn't leave the display in the sense of
 * ADR-018, rule 11.
 *
 * When no app can take it, a toast says so: a failed action is never silent (ADR-018, rule 18). A toast is the
 * system's and is drawn over the device, as in [SaveFailureToast].
 */
internal class ExternalApps(private val context: Context) {
    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun sendEmail(to: String, subject: String) = start(
        // The address is in the URI too: some email apps read it only from there.
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject),
    )

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.settings_about_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun rememberExternalApps(): ExternalApps {
    val context = LocalContext.current
    return remember(context) { ExternalApps(context) }
}
