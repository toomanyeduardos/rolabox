import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * The `:impl` of a part with screens (ADR-020): a library with Compose, Hilt for its ViewModels and
 * its entry contract, and Navigation 3 for its keys and entries.
 */
class RolaboxAndroidScreensConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rolabox.android.library")
            pluginManager.apply("rolabox.android.compose")
            pluginManager.apply("rolabox.hilt")
            pluginManager.apply("rolabox.navigation")

            // Only what every part with screens needs. Each one declares the :api modules it uses
            // itself, so its build file shows what it depends on (ADR-003, rule 15).
            dependencies {
                add("implementation", project(":common:designsystem"))
            }
        }
    }
}
