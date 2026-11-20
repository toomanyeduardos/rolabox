package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The Gradle property, in the repo's `gradle.properties`, that the app's version comes from (ADR-035). */
internal const val VERSION_NAME_PROPERTY = "rolabox.versionName"

private const val MAX_MAJOR = 209_999
private const val MAX_MINOR_OR_PATCH = 99
private const val MAJOR_STEP = 10_000
private const val MINOR_STEP = 100
private val SEMVER = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d?)\.(0|[1-9]\d?)""")

/** The app's version: a semver [name], and the `versionCode` derived from it. */
internal data class AppVersion(val major: Int, val minor: Int, val patch: Int) {
    val name: String get() = "$major.$minor.$patch"

    /** `MAJOR * 10000 + MINOR * 100 + PATCH`, so it grows whenever the name does, and stays under Play's limit. */
    val code: Int get() = major * MAJOR_STEP + minor * MINOR_STEP + patch
}

/** The [AppVersion] that [value] names, or why it names none. */
internal sealed interface AppVersionResult {
    data class Valid(val version: AppVersion) : AppVersionResult

    data class Invalid(val reason: String) : AppVersionResult
}

/** Parses `rolabox.versionName`: `MAJOR.MINOR.PATCH`, with MAJOR from 1 to 209999 and MINOR and PATCH from 0 to 99. */
internal fun appVersion(value: String?): AppVersionResult {
    if (value.isNullOrBlank()) {
        return AppVersionResult.Invalid("$VERSION_NAME_PROPERTY is not set. Set it in gradle.properties, e.g. 1.0.0.")
    }
    val match = SEMVER.matchEntire(value)
    val major = match?.groupValues?.get(1)?.toIntOrNull()
    if (match == null || major == null) {
        return AppVersionResult.Invalid(
            "$VERSION_NAME_PROPERTY is \"$value\", which isn't MAJOR.MINOR.PATCH with no leading zeros, " +
                "and MINOR and PATCH from 0 to $MAX_MINOR_OR_PATCH.",
        )
    }
    if (major !in 1..MAX_MAJOR) {
        return AppVersionResult.Invalid(
            "$VERSION_NAME_PROPERTY is \"$value\": MAJOR goes from 1 to $MAX_MAJOR, so versionCode stays under " +
                "Play's limit.",
        )
    }
    return AppVersionResult.Valid(AppVersion(major, match.groupValues[2].toInt(), match.groupValues[3].toInt()))
}

/** Why a variant whose final version is [versionCode] and [versionName] isn't [expected], or null when it is. */
internal fun appVersionViolation(expected: AppVersion, versionCode: Int?, versionName: String?): String? =
    if (versionCode == expected.code && versionName == expected.name) {
        null
    } else {
        "the version is $versionName ($versionCode) instead of ${expected.name} (${expected.code}) from " +
            "$VERSION_NAME_PROPERTY. Only the rolabox.android.application convention sets versionCode and " +
            "versionName (ADR-035, rule 4)."
    }

/**
 * Sets the app's `versionName` and `versionCode` from `rolabox.versionName` (ADR-035), and fails the build when
 * it's missing or malformed, or when a variant ends up with any other version, for example because a module set
 * `versionCode`.
 */
internal fun Project.configureAppVersion() {
    val version = when (val result = appVersion(providers.gradleProperty(VERSION_NAME_PROPERTY).orNull)) {
        is AppVersionResult.Valid -> result.version
        is AppVersionResult.Invalid -> throw GradleException("App version in $path: ${result.reason}")
    }

    extensions.configure<ApplicationExtension> {
        defaultConfig {
            versionCode = version.code
            versionName = version.name
        }
    }

    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                appVersionViolation(version, output.versionCode.orNull, output.versionName.orNull)?.let { reason ->
                    throw GradleException("Version rule violated in $path (${variant.name}): $reason")
                }
            }
        }
    }
}
