import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * For the JVM `:api` of a part with screens whose contract carries something composable, such as the
 * row of a settings section (ADR-020). It adds the Compose compiler and the runtime's JVM variant,
 * and nothing of Android's.
 */
class RolaboxJvmComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            dependencies {
                add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
                add("implementation", libs.findLibrary("androidx-compose-runtime").get())
            }
        }
    }
}
