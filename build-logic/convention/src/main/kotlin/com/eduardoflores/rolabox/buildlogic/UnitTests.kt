package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.Project

private const val UNIT_TEST_TASK = "unitTest"

/** The default flavor's debug unit tests, the variant a fresh clone builds (ADR-008). */
internal val DEFAULT_ANDROID_UNIT_TEST_TASK = "test" +
    BackendFlavor.entries.single { it.isDefault }.flavorName.replaceFirstChar(Char::uppercaseChar) +
    "DebugUnitTest"

/**
 * Every module gets a `unitTest` task, so `./gradlew unitTest` runs the whole codebase's unit tests
 * with one name: Android modules run [DEFAULT_ANDROID_UNIT_TEST_TASK], and JVM modules, which have
 * no variants, run `test`. Adding a module needs no change to any task list.
 */
internal fun Project.registerUnitTestTask(testTaskName: String) {
    tasks.register(UNIT_TEST_TASK) {
        group = "verification"
        description = "Runs this module's unit tests ($testTaskName)."
        dependsOn(testTaskName)
    }
}
