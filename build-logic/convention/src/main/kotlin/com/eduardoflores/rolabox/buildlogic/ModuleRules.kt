package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

private const val APP_PATH = ":app"

// The product areas, and what they share (ADR-020).
private const val AUTH_PREFIX = ":auth:"
private const val DEVICE_PREFIX = ":device:"
private const val COMMON_PREFIX = ":common:"

// The modules that aren't split, because they have nothing to hide behind an interface.
private const val UTIL_PATH = ":common:util"
private const val DESIGNSYSTEM_PATH = ":common:designsystem"
private const val DEVICE_HOST_PATH = ":device:host"

// The one place where auth depends on the device: the account section of the settings list.
private const val AUTH_SETTINGS_PATH = ":auth:settings:impl"
private const val DEVICE_SETTINGS_API_PATH = ":device:settings:api"

// A part is split into :api, :impl and :testing, at any depth.
private const val API_SUFFIX = ":api"
private const val IMPL_SUFFIX = ":impl"
private const val TESTING_SUFFIX = ":testing"

// The playback screens never see the playback engine's side of the area (ADR-018 rule 15).
private const val PLAYBACK_UI_PREFIX = ":device:playback:ui:"
private const val PLAYBACK_IMPL_PATH = ":device:playback:impl"
private const val PLAYBACK_TESTING_PATH = ":device:playback:testing"

// What :app's own code uses from the product areas (ADR-020 rule 13). It also uses :common, and lists every :impl.
private val APP_ALLOWED_API_PATHS = setOf(":auth:data:api", ":auth:ui:api", ":device:ui:api")

private val IMPL_ALLOWED_PATHS = setOf(UTIL_PATH, DESIGNSYSTEM_PATH)
private val DEVICE_HOST_ALLOWED_PATHS = setOf(UTIL_PATH, DESIGNSYSTEM_PATH)

private val ANDROID_PLUGINS = listOf("com.android.application", "com.android.library")
private const val DAGGER_GROUP = "com.google.dagger"
private const val PAPARAZZI_GROUP = "app.cash.paparazzi"
private const val PAPARAZZI_PLUGIN = "app.cash.paparazzi"

/** Checks the `[enforced]` dependency rules of ADR-020, ADR-005 and ADR-016 while the build is configured. */
internal fun Project.enforceModuleRules() {
    val modulePath = path

    if (modulePath.isApi()) {
        ANDROID_PLUGINS.forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                throw GradleException(
                    "Module rule violated: $modulePath applies $pluginId. " +
                        "ADR-020 rule 6: an :api is a JVM module with no Android dependencies.",
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

internal fun externalDependencyViolation(modulePath: String, group: String?, appliesPaparazzi: Boolean): String? =
    when {
        modulePath.isApi() && group == DAGGER_GROUP ->
            "ADR-005 rule 3: :api modules use only javax.inject and contain no Hilt modules"

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

/** ADR-020 rules 1 to 8 and 13, and ADR-018 rule 15, in the order a violation is most useful to read. Null when the dependency is allowed. */
@Suppress("CyclomaticComplexMethod") // One branch per rule, which is what makes it readable.
internal fun projectDependencyViolation(modulePath: String, dependencyPath: String, configurationName: String): String? =
    when {
        // A module's own test fixtures.
        dependencyPath == modulePath -> null

        // More specific than rules 4 and 7 below, so a playback screen gets the reason in its own terms.
        modulePath.startsWith(PLAYBACK_UI_PREFIX) &&
            (dependencyPath == PLAYBACK_IMPL_PATH ||
                dependencyPath == PLAYBACK_TESTING_PATH && !configurationName.isTestConfiguration()) ->
            "ADR-018 rule 15: the playback screens ($PLAYBACK_UI_PREFIX*) depend on the playback state only " +
                "through :device:playback:api, never on $PLAYBACK_IMPL_PATH, and on $PLAYBACK_TESTING_PATH " +
                "only from test configurations"

        dependencyPath.isTesting() && !configurationName.isTestConfiguration() ->
            "ADR-020 rule 7: testing modules (:common:testing and every :testing) may only be used from test " +
                "configurations (testImplementation, androidTestImplementation)"

        modulePath.startsWith(COMMON_PREFIX) && !dependencyPath.startsWith(COMMON_PREFIX) ->
            "ADR-020 rule 1: :common:* modules may only depend on :common:* modules. Declare an interface in " +
                "the common module's :api, and let the product area implement it"

        modulePath.startsWith(DEVICE_PREFIX) && dependencyPath.startsWith(AUTH_PREFIX) ->
            "ADR-020 rule 2: no :device module may depend on an :auth module"

        modulePath.startsWith(AUTH_PREFIX) && dependencyPath.startsWith(DEVICE_PREFIX) &&
            !(modulePath == AUTH_SETTINGS_PATH && dependencyPath == DEVICE_SETTINGS_API_PATH) ->
            "ADR-020 rule 3: the only :auth module that may depend on a :device module is $AUTH_SETTINGS_PATH, " +
                "and only on $DEVICE_SETTINGS_API_PATH"

        modulePath != APP_PATH && dependencyPath.isImpl() ->
            "ADR-020 rule 4: only $APP_PATH may depend on :impl modules; depend on the part's :api instead"

        dependencyPath == DEVICE_HOST_PATH && !(modulePath.startsWith(DEVICE_PREFIX) && modulePath.isImpl()) ->
            "ADR-020 rule 8: only :device :impl modules may depend on $DEVICE_HOST_PATH"

        modulePath == DEVICE_HOST_PATH && !dependencyPath.isTesting() &&
            dependencyPath !in DEVICE_HOST_ALLOWED_PATHS ->
            "ADR-020 rule 8: $DEVICE_HOST_PATH may only depend on $DESIGNSYSTEM_PATH and $UTIL_PATH"

        modulePath == APP_PATH && !configurationName.isTestConfiguration() && !dependencyPath.isImpl() &&
            !dependencyPath.startsWith(COMMON_PREFIX) && dependencyPath !in APP_ALLOWED_API_PATHS ->
            "ADR-020 rule 13: $APP_PATH's code may only use ${APP_ALLOWED_API_PATHS.joinToString()} and :common " +
                "modules. Only its tests may name another part's :api, to assert how the part is assembled"

        modulePath.isApi() && !dependencyPath.isApi() && dependencyPath != UTIL_PATH ->
            "ADR-020 rule 6: :api modules may only depend on other :api modules and $UTIL_PATH"

        modulePath.isImpl() && !dependencyPath.isTesting() && !dependencyPath.isApi() &&
            dependencyPath !in IMPL_ALLOWED_PATHS &&
            !(modulePath.startsWith(DEVICE_PREFIX) && dependencyPath == DEVICE_HOST_PATH) ->
            "ADR-020 rule 5: :impl modules may only depend on :api modules, $UTIL_PATH and $DESIGNSYSTEM_PATH, " +
                "and a :device :impl also on $DEVICE_HOST_PATH"

        else -> null
    }

private fun String.isApi() = endsWith(API_SUFFIX)

private fun String.isImpl() = endsWith(IMPL_SUFFIX)

// :common:testing and every part's :testing.
private fun String.isTesting() = endsWith(TESTING_SUFFIX)

// Covers testImplementation, androidTestImplementation and their per-variant forms (testDebugImplementation, …).
private fun String.isTestConfiguration() = startsWith("test") || startsWith("androidTest")
