import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * An area's `:ui` module (ADR-014): a library with Compose. Unlike a feature it has no Hilt, since it
 * has no ViewModels and injects nothing.
 */
class RolaboxAndroidUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rolabox.android.library")
            pluginManager.apply("rolabox.android.compose")

            // Only what every :ui needs. Each one declares the :api modules it uses itself (ADR-003).
            dependencies {
                add("implementation", project(":core:designsystem"))
            }
        }
    }
}
