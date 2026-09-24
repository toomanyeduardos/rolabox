package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

private const val FEATURE_PREFIX = ":feature:"
private const val MODEL_PATH = ":core:model"

internal fun Project.enforceModuleRules() {
    val modulePath = path
    configurations.configureEach {
        val configurationName = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            val violation = when {
                path == modulePath -> null

                modulePath == MODEL_PATH ->
                    "$MODEL_PATH must not depend on any other module"

                modulePath.startsWith(FEATURE_PREFIX) && path.startsWith(FEATURE_PREFIX) ->
                    "feature modules must not depend on other feature modules"

                else -> null
            }
            if (violation != null) {
                throw GradleException(
                    "Module rule violated: $modulePath -> $path ($configurationName). $violation."
                )
            }
        }
    }
}
