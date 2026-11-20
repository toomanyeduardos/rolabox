package com.eduardoflores.rolabox.auth.data.impl

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Erases everything the app keeps on the phone, as Settings → Apps → Clear storage does, once the account is deleted
 * (ADR-037). It's an interface so that the use case's tests can check when it runs, without erasing anything.
 */
internal fun interface AppDataEraser {
    /**
     * Asks Android to erase the app's data. When it accepts, it returns `true` and Android closes the app shortly
     * after: what runs between the two may not finish. `false` means Android refused, and nothing was erased.
     */
    fun erase(): Boolean
}

/**
 * Android's own erase: every database, preference, file, cache and download of the app, and what the SDKs keep
 * (Firebase, Firestore's cache, WorkManager), including anything added later, with nothing to list here. The next
 * launch is a first run.
 */
internal class AndroidAppDataEraser @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppDataEraser {
    override fun erase(): Boolean = context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
}
