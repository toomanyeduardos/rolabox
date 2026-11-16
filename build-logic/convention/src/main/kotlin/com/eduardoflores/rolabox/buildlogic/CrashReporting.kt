package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure

/** The manifest placeholder that `app/src/main/AndroidManifest.xml` sets Crashlytics' collection flag from. */
internal const val CRASHLYTICS_COLLECTION_PLACEHOLDER = "crashlyticsCollectionEnabled"

private const val RELEASE = "release"

/**
 * Crashlytics for every build type of the app (ADR-030): collection only in release (rule 7), and the release's
 * mapping file uploaded when the build has a real Firebase config, so the upload can find its app (rule 8).
 */
internal fun Project.configureCrashReporting(usesRealConfig: Boolean) {
    pluginManager.apply("com.google.firebase.crashlytics")
    extensions.configure<ApplicationExtension> {
        buildTypes.configureEach {
            manifestPlaceholders[CRASHLYTICS_COLLECTION_PLACEHOLDER] = crashlyticsCollectionEnabled(name).toString()
            (this as ExtensionAware).extensions.configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = mappingFileUploadEnabled(name, usesRealConfig)
            }
        }
    }
}

/** Whether Crashlytics collects anything in [buildType]: only in release, so testing never shows up in it. */
internal fun crashlyticsCollectionEnabled(buildType: String): Boolean = buildType == RELEASE

/** Whether [buildType] uploads its mapping file: a release with a real Firebase config, the only kind it can reach. */
internal fun mappingFileUploadEnabled(buildType: String, usesRealConfig: Boolean): Boolean =
    buildType == RELEASE && usesRealConfig
