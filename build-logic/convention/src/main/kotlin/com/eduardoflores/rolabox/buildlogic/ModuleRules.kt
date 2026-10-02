package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

private const val APP_PATH = ":app"
private const val CORE_PREFIX = ":core:"
private const val FEATURE_PREFIX = ":feature:"
private const val COMMON_PATH = ":core:common"
private const val DESIGNSYSTEM_PATH = ":core:designsystem"

// The device host: a utility module that features build their device screens on (ADR-019).
private const val DEVICE_PATH = ":core:device"

// Holds the use cases that combine more than one area (ADR-003).
private const val DOMAIN_PATH = ":core:domain"

// Each area is split into :core:<area>:api, :core:<area>:impl and :core:<area>:testing (ADR-003), and has
// a :core:<area>:ui when it has UI-bound code (ADR-014).
private const val API_SUFFIX = ":api"
private const val IMPL_SUFFIX = ":impl"
private const val TESTING_SUFFIX = ":testing"
private const val UI_SUFFIX = ":ui"

private val SHARED_JVM_PATHS = setOf(COMMON_PATH, DOMAIN_PATH)

// Besides *:api modules. :core:common is a utility module with nothing to hide behind an :api.
private val API_ALLOWED_PATHS = setOf(COMMON_PATH)
private val IMPL_ALLOWED_PATHS = setOf(COMMON_PATH)
private val FEATURE_ALLOWED_PATHS = setOf(COMMON_PATH, DESIGNSYSTEM_PATH, DEVICE_PATH, DOMAIN_PATH)
private val UI_ALLOWED_PATHS = setOf(COMMON_PATH, DESIGNSYSTEM_PATH, DOMAIN_PATH)
private val DEVICE_ALLOWED_PATHS = setOf(COMMON_PATH, DESIGNSYSTEM_PATH)

private val ANDROID_PLUGINS = listOf("com.android.application", "com.android.library")
private const val DAGGER_GROUP = "com.google.dagger"
private const val PAPARAZZI_GROUP = "app.cash.paparazzi"
private const val PAPARAZZI_PLUGIN = "app.cash.paparazzi"

/** Checks the `[enforced]` dependency rules of ADR-001, ADR-003, ADR-005 and ADR-016 while the build is configured. */
internal fun Project.enforceModuleRules() {
    val modulePath = path

    if (modulePath.isJvmOnly()) {
        ANDROID_PLUGINS.forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                throw GradleException(
                    "Module rule violated: $modulePath applies $pluginId. " +
                        "ADR-003 rule 5: *:api modules, $COMMON_PATH and $DOMAIN_PATH must be JVM modules.",
                )
            }
        }
    }

    configurations.configureEach {
        val configurationName = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            val violation = projectDependencyViolation(modulePath, path, configurationName)
            if (violation != null) {
                throw GradleException(
                    "Module rule violated: $modulePath -> $path ($configurationName). $violation.",
                )
            }
        }
    }

    enforceExternalDependencyRules()
}

// Catalog dependencies are added lazily, and resolution doesn't pass them through configureEach,
// so they're checked once the build file has been evaluated.
private fun Project.enforceExternalDependencyRules() = afterEvaluate {
    configurations.forEach { configuration ->
        configuration.dependencies.withType(ExternalModuleDependency::class.java).forEach { dependency ->
            val violation = externalDependencyViolation(path, dependency.group, pluginManager.hasPlugin(PAPARAZZI_PLUGIN))
            if (violation != null) {
                throw GradleException(
                    "Module rule violated: $path -> ${dependency.group}:${dependency.name} (${configuration.name}). " +
                        "$violation.",
                )
            }
        }
    }
}

private fun externalDependencyViolation(modulePath: String, group: String?, appliesPaparazzi: Boolean): String? =
    when {
        (modulePath.isApi() || modulePath == DOMAIN_PATH) && group == DAGGER_GROUP ->
            "ADR-005 rule 3: *:api modules and $DOMAIN_PATH use only javax.inject and contain no Hilt modules"

        group == PAPARAZZI_GROUP && !appliesPaparazzi ->
            "ADR-016 rule 1: Paparazzi comes only from the rolabox.android.paparazzi plugin, which the module doesn't apply"

        else -> null
    }

/**
 * ADR-008 rule 1: Rolabox is one app, so no module declares product flavors. Called from the
 * Android convention plugins once the module's `android {}` block is final.
 */
internal fun CommonExtension.enforceNoProductFlavors(modulePath: String) {
    if (flavorDimensions.isNotEmpty() || productFlavors.isNotEmpty()) {
        throw GradleException(
            "Module rule violated: $modulePath declares product flavors ${productFlavors.names}. " +
                "ADR-008 rule 1: there are no product flavors; offline is a choice the user makes in the app.",
        )
    }
}

private fun projectDependencyViolation(modulePath: String, dependencyPath: String, configurationName: String): String? =
    when {
        dependencyPath == modulePath -> null

        dependencyPath.isTesting() && !configurationName.isTestConfiguration() ->
            "ADR-003 rule 10: testing modules (:core:testing, :core:<area>:testing) may only be used from test " +
                "configurations (testImplementation, androidTestImplementation)"

        modulePath.startsWith(FEATURE_PREFIX) && dependencyPath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 1: feature modules must not depend on other feature modules"

        modulePath != APP_PATH && dependencyPath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 2: only $APP_PATH may depend on feature modules"

        modulePath != APP_PATH && dependencyPath.isImpl() ->
            "ADR-003 rule 3: only $APP_PATH may depend on *:impl modules; depend on the area's :api instead"

        modulePath.startsWith(FEATURE_PREFIX) && !dependencyPath.isTesting() && !dependencyPath.isUi() &&
            !dependencyPath.isApiOrIn(FEATURE_ALLOWED_PATHS) ->
            "ADR-003 rule 4: feature modules may only depend on *:api and *:ui modules, $DOMAIN_PATH, " +
                "$COMMON_PATH, $DESIGNSYSTEM_PATH and $DEVICE_PATH"

        dependencyPath.isUi() && modulePath != APP_PATH && !modulePath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 17: only feature modules and $APP_PATH may depend on *:ui modules"

        modulePath.isUi() && !dependencyPath.isTesting() && !dependencyPath.isApiOrIn(UI_ALLOWED_PATHS) ->
            "ADR-003 rule 16: *:ui modules may only depend on *:api modules, $DOMAIN_PATH, $COMMON_PATH " +
                "and $DESIGNSYSTEM_PATH"

        dependencyPath == DEVICE_PATH && modulePath != APP_PATH && !modulePath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 19: only feature modules and $APP_PATH may depend on $DEVICE_PATH"

        modulePath == DEVICE_PATH && !dependencyPath.isTesting() && dependencyPath !in DEVICE_ALLOWED_PATHS ->
            "ADR-003 rule 18: $DEVICE_PATH may only depend on $DESIGNSYSTEM_PATH and $COMMON_PATH"

        modulePath.isApi() && !dependencyPath.isApiOrIn(API_ALLOWED_PATHS) ->
            "ADR-003 rule 6: *:api modules may only depend on other *:api modules and $COMMON_PATH"

        modulePath.isImpl() && !dependencyPath.isTesting() && !dependencyPath.isApiOrIn(IMPL_ALLOWED_PATHS) ->
            "ADR-003 rule 7: *:impl modules may only depend on *:api modules and $COMMON_PATH"

        modulePath == DOMAIN_PATH && !dependencyPath.isTesting() && !dependencyPath.isApiOrIn(API_ALLOWED_PATHS) ->
            "ADR-003 rule 8: $DOMAIN_PATH may only depend on *:api modules and $COMMON_PATH"

        modulePath == DESIGNSYSTEM_PATH && dependencyPath !in API_ALLOWED_PATHS ->
            "ADR-003 rule 9: $DESIGNSYSTEM_PATH may only depend on $COMMON_PATH"

        else -> null
    }

private fun String.isApi() = startsWith(CORE_PREFIX) && endsWith(API_SUFFIX)

private fun String.isImpl() = startsWith(CORE_PREFIX) && endsWith(IMPL_SUFFIX)

private fun String.isUi() = startsWith(CORE_PREFIX) && endsWith(UI_SUFFIX)

// :core:testing and every :core:<area>:testing.
private fun String.isTesting() = startsWith(CORE_PREFIX) && endsWith(TESTING_SUFFIX)

private fun String.isJvmOnly() = isApi() || this in SHARED_JVM_PATHS

private fun String.isApiOrIn(paths: Set<String>) = isApi() || this in paths

// Covers testImplementation, androidTestImplementation and their per-variant forms (testDebugImplementation, …).
private fun String.isTestConfiguration() = startsWith("test") || startsWith("androidTest")
