package com.eduardoflores.rolabox.device.settings.impl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The installed app's version, as About shows it. An interface so the ViewModel's tests don't need Android. */
internal fun interface AppVersion {
    fun name(): String
}

/** Reads the version name the app was built with. `:app` sets it, and Settings can't see `:app` (ADR-020). */
internal class PackageAppVersion @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppVersion {
    override fun name(): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
