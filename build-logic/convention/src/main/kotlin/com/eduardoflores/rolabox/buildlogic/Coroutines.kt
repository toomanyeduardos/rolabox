package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

// The configurations a module declares dependencies in, main and test. A platform added to each reaches every
// classpath built from them, the instrumented tests' included, which don't inherit main's platforms.
private val DECLARING_CONFIGURATIONS =
    listOf("implementation", "testImplementation", "androidTestImplementation", "testFixturesImplementation")

/**
 * Aligns every kotlinx.coroutines artifact of the module on the catalog's version, through the coroutines BOM.
 * Without it, a classpath that declares no coroutines of its own takes whatever version AndroidX brings, older
 * than the catalog's, and a module's instrumented tests can resolve a different version from its main code.
 */
internal fun Project.alignCoroutines() {
    val bom = libs.findLibrary("kotlinx-coroutines-bom").get()
    DECLARING_CONFIGURATIONS.forEach { name ->
        configurations.matching { it.name == name }.configureEach {
            dependencies { add(name, platform(bom)) }
        }
    }
}
