package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val RELEASE = "release"

/**
 * Release builds go through R8: code shrinking, optimization and obfuscation (full mode, obfuscation
 * on even though the source is public), plus resource shrinking. Without it the release APK was 42.6
 * MB, almost all of it unshrunk Firebase, Play Services, Compose, Media3, Hilt and Room code.
 *
 * - `optimization.enable` turns on both code and resource shrinking, with the default
 *   `proguard-android-optimize.txt` rules. Libraries bring their own consumer rules.
 * - The app's own keep rules, if it ever needs any, go in `app/src/main/keepRules/`, which AGP reads
 *   when it exists. Each rule carries a comment saying why. Today there are none.
 * - Each release's `mapping.txt` goes to Play inside the app bundle, so Play Console deobfuscates
 *   crashes. Nothing else is archived.
 * - Instrumented tests stay on debug (ADR-008 allows only debug and release). A minified build is
 *   covered by a manual smoke test before each release, not by a test build type.
 *
 * [enforceReleaseOptimization] fails the build if anything turns this off again.
 */
internal fun Project.configureReleaseOptimization() {
    extensions.configure<ApplicationExtension> {
        buildTypes.named(RELEASE) {
            optimization {
                enable = true
            }
            // Until real release signing exists, a release is signed with the debug key so a minified
            // build can be installed and smoke-tested locally. Play rejects debug-signed uploads, so
            // this can't be shipped by mistake. Real signing replaces this line.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

/**
 * Fails the build when the release variant is not minified or doesn't shrink resources, for example
 * because a module set `optimization { enable = false }`. It reads the variant's final values, so a
 * change anywhere in the DSL is caught.
 */
internal fun Project.enforceReleaseOptimization() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType(RELEASE)) { variant ->
            releaseOptimizationViolation(variant.isMinifyEnabled, variant.shrinkResources)?.let { reason ->
                throw GradleException("Release build rule violated in $path (${variant.name}): $reason")
            }
        }
    }
}

/** Why a release variant with these settings is not allowed, or null when it is. */
internal fun releaseOptimizationViolation(isMinifyEnabled: Boolean, shrinkResources: Boolean): String? {
    val off = listOfNotNull(
        "code shrinking (R8)".takeUnless { isMinifyEnabled },
        "resource shrinking".takeUnless { shrinkResources },
    )
    return if (off.isEmpty()) {
        null
    } else {
        "${off.joinToString(" and ")} turned off. The application convention turns on `optimization` for " +
            "release, and no module turns it off. If R8 breaks something, add a keep rule with a comment " +
            "saying why in app/src/main/keepRules/."
    }
}
