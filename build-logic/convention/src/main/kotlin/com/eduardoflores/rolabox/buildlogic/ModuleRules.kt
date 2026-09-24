package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

private const val APP_PATH = ":app"
private const val CORE_PREFIX = ":core:"
private const val FEATURE_PREFIX = ":feature:"
private const val MODEL_PATH = ":core:model"
private const val COMMON_PATH = ":core:common"
private const val DOMAIN_PATH = ":core:domain"
private const val DESIGNSYSTEM_PATH = ":core:designsystem"
private const val TESTING_PATH = ":core:testing"

// Includes planned modules, so the rules already hold when they are added.
private val DATA_LAYER_PATHS = setOf(":core:data", ":core:database", ":core:datastore", ":core:auth", ":core:sync")
private val JVM_ONLY_PATHS = setOf(DOMAIN_PATH, MODEL_PATH, COMMON_PATH)
private val DOMAIN_ALLOWED_PATHS = setOf(MODEL_PATH, COMMON_PATH)
private val ANDROID_PLUGINS = listOf("com.android.application", "com.android.library")
private const val DAGGER_GROUP = "com.google.dagger"
private const val FIREBASE_GROUP = "com.google.firebase"

/** Checks the `[enforced]` rules of ADR-001, ADR-003, ADR-005 and ADR-008 while the build is configured. */
internal fun Project.enforceModuleRules() {
    val modulePath = path

    if (modulePath in JVM_ONLY_PATHS) {
        ANDROID_PLUGINS.forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                throw GradleException(
                    "Module rule violated: $modulePath applies $pluginId. " +
                        "ADR-003 rule 6: :core:domain, :core:model and :core:common must be JVM modules.",
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
            val violation = externalDependencyViolation(path, dependency.group, configuration.name)
            if (violation != null) {
                throw GradleException(
                    "Module rule violated: $path -> ${dependency.group}:${dependency.name} (${configuration.name}). " +
                        "$violation.",
                )
            }
        }
    }
}

private fun externalDependencyViolation(modulePath: String, group: String?, configurationName: String): String? =
    when {
        modulePath == DOMAIN_PATH && group == DAGGER_GROUP ->
            "ADR-005 rule 3: $DOMAIN_PATH uses only javax.inject and contains no Hilt modules"

        group == FIREBASE_GROUP && !configurationName.isCloudConfiguration() ->
            "ADR-008 rule 2: Firebase dependencies are only declared in cloud configurations (cloudImplementation)"

        else -> null
    }

private fun projectDependencyViolation(modulePath: String, dependencyPath: String, configurationName: String): String? =
    when {
        dependencyPath == modulePath -> null

        modulePath == MODEL_PATH ->
            "ADR-003 rule 2: $MODEL_PATH must not depend on any other module"

        modulePath.startsWith(FEATURE_PREFIX) && dependencyPath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 1: feature modules must not depend on other feature modules"

        modulePath.startsWith(CORE_PREFIX) && dependencyPath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 3: :core modules must not depend on feature modules"

        modulePath != APP_PATH && dependencyPath.startsWith(FEATURE_PREFIX) ->
            "ADR-003 rule 4: only $APP_PATH may depend on feature modules"

        modulePath.startsWith(FEATURE_PREFIX) && dependencyPath in DATA_LAYER_PATHS ->
            "ADR-003 rule 5: feature modules must not depend on data-layer modules; depend on $DOMAIN_PATH instead"

        modulePath == DOMAIN_PATH && dependencyPath !in DOMAIN_ALLOWED_PATHS ->
            "ADR-001 rule 2: $DOMAIN_PATH may only depend on $MODEL_PATH and $COMMON_PATH"

        modulePath == DESIGNSYSTEM_PATH && (dependencyPath == DOMAIN_PATH || dependencyPath in DATA_LAYER_PATHS) ->
            "ADR-003 rule 7: $DESIGNSYSTEM_PATH must not depend on $DOMAIN_PATH or data-layer modules"

        dependencyPath == TESTING_PATH && !configurationName.isTestConfiguration() ->
            "ADR-003 rule 8: $TESTING_PATH may only be used from test configurations " +
                "(testImplementation, androidTestImplementation)"

        else -> null
    }

// Covers testImplementation, androidTestImplementation and their per-variant forms (testDebugImplementation, …).
private fun String.isTestConfiguration() = startsWith("test") || startsWith("androidTest")
