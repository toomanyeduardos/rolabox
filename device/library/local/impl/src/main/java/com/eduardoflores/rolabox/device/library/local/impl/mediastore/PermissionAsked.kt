package com.eduardoflores.rolabox.device.library.local.impl.mediastore

import androidx.annotation.VisibleForTesting

/**
 * Whether Android asked for the audio permission on this install: [stored] is the install time it was asked on.
 * A value from another install, restored with the preferences, or the `true` older versions stored, is not.
 */
@VisibleForTesting
internal fun askedOnThisInstall(stored: String?, installedAt: Long): Boolean = stored == installedAt.toString()
