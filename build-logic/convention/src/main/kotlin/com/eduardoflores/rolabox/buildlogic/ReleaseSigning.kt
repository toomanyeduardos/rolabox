package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val RELEASE = "release"
private const val DEBUG = "debug"
private const val UPLOAD = "upload"

internal const val UPLOAD_STORE_FILE = "rolaboxUploadStoreFile"
internal const val UPLOAD_STORE_PASSWORD = "rolaboxUploadStorePassword"
internal const val UPLOAD_KEY_ALIAS = "rolaboxUploadKeyAlias"
internal const val UPLOAD_KEY_PASSWORD = "rolaboxUploadKeyPassword"

/** The four Gradle properties the upload key is read from (ADR-035), in the order they're reported. */
internal val UPLOAD_SIGNING_PROPERTIES =
    listOf(UPLOAD_STORE_FILE, UPLOAD_STORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD)

/** How a release is signed, decided from which of the [UPLOAD_SIGNING_PROPERTIES] are set. */
internal sealed interface ReleaseSigning {
    /** All four are set: the release is signed with the upload key, and can go to Play. */
    data class Upload(
        val storeFile: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    ) : ReleaseSigning {
        // The passwords never reach a log or an exception message.
        override fun toString(): String = "Upload(storeFile=$storeFile, keyAlias=<hidden>)"
    }

    /** None is set: the release is signed with the debug key, so a fresh clone and CI build it, and Play rejects it. */
    data object Debug : ReleaseSigning

    /** Some, not all, are set: the build fails, naming the [missing] ones. */
    data class Incomplete(val missing: List<String>) : ReleaseSigning
}

/**
 * Which [ReleaseSigning] applies, given each signing property's value, or null when it isn't set. A blank value
 * counts as not set, so `rolaboxUploadKeyPassword=` doesn't pass for a password.
 */
internal fun releaseSigning(values: Map<String, String?>): ReleaseSigning {
    val set = UPLOAD_SIGNING_PROPERTIES
        .mapNotNull { name -> values[name]?.takeIf(String::isNotBlank)?.let { name to it } }
        .toMap()
    val missing = UPLOAD_SIGNING_PROPERTIES - set.keys
    return when {
        missing.isEmpty() -> ReleaseSigning.Upload(
            storeFile = set.getValue(UPLOAD_STORE_FILE),
            storePassword = set.getValue(UPLOAD_STORE_PASSWORD),
            keyAlias = set.getValue(UPLOAD_KEY_ALIAS),
            keyPassword = set.getValue(UPLOAD_KEY_PASSWORD),
        )
        set.isEmpty() -> ReleaseSigning.Debug
        else -> ReleaseSigning.Incomplete(missing)
    }
}

/** Why a release whose final signing config is [signingConfigName] breaks ADR-035 rule 1, or null when it doesn't. */
internal fun releaseSigningViolation(signing: ReleaseSigning, signingConfigName: String?): String? {
    val expected = when (signing) {
        is ReleaseSigning.Upload -> UPLOAD
        ReleaseSigning.Debug -> DEBUG
        is ReleaseSigning.Incomplete -> return null
    }
    return if (signingConfigName == expected) {
        null
    } else {
        "the release is signed with `${signingConfigName ?: "nothing"}` instead of `$expected`. Only the " +
            "rolabox.android.application convention sets the release's signing config (ADR-035, rule 1)."
    }
}

/**
 * Signs the release build type (ADR-035): with the upload key when the four `rolaboxUpload*` Gradle properties
 * are set, which they only are outside the repo, in `~/.gradle/gradle.properties` or as `ORG_GRADLE_PROJECT_*`
 * environment variables; with the debug key when none is, so a fresh clone's and CI's release builds and the
 * local smoke test need no secrets; and not at all when only some are, which fails the build.
 *
 * A debug-signed `assembleRelease` or `bundleRelease` logs a warning that the output can't go to Play. The
 * release's final signing config is checked once the DSL is final, so a module that sets its own fails the build.
 * The release steps are in `docs/releasing.md`.
 */
internal fun Project.configureReleaseSigning() {
    val signing = releaseSigning(UPLOAD_SIGNING_PROPERTIES.associateWith { providers.gradleProperty(it).orNull })

    if (signing is ReleaseSigning.Incomplete) {
        throw GradleException(
            "Release signing in $path: ${signing.missing.joinToString()} not set. Set all four of " +
                "${UPLOAD_SIGNING_PROPERTIES.joinToString()} in ~/.gradle/gradle.properties to sign with the " +
                "upload key, or none of them to sign with the debug key (ADR-035, docs/releasing.md).",
        )
    }

    extensions.configure<ApplicationExtension> {
        val config = when (signing) {
            is ReleaseSigning.Upload -> signingConfigs.create(UPLOAD) {
                storeFile = file(signing.storeFile)
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
            else -> signingConfigs.getByName(DEBUG)
        }
        buildTypes.named(RELEASE) { signingConfig = config }
    }

    extensions.configure<ApplicationAndroidComponentsExtension> {
        finalizeDsl { android ->
            val name = android.buildTypes.getByName(RELEASE).signingConfig?.name
            releaseSigningViolation(signing, name)?.let { reason ->
                throw GradleException("Release build rule violated in $path: $reason")
            }
        }
    }

    if (signing == ReleaseSigning.Debug) {
        val warning = "This release is signed with the debug key, since no rolaboxUpload* property is set. It " +
            "installs for the smoke test, but Play rejects it. See docs/releasing.md."
        tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
            doLast { logger.warn(warning) }
        }
    }
}
