import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class RolaboxAndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rolabox.android.library")
            pluginManager.apply("rolabox.android.compose")
            pluginManager.apply("rolabox.hilt")

            // Only what every feature needs. Each feature declares the :api modules it uses itself,
            // so its build file shows which capabilities it depends on (ADR-003).
            dependencies {
                add("implementation", project(":core:designsystem"))
            }
        }
    }
}
