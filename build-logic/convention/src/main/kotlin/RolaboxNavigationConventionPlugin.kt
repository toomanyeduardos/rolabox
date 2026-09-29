import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * What a module needs to declare Navigation 3 keys and entries (ADR-012): the runtime, and
 * kotlinx-serialization for the `@Serializable` keys. `:app` adds the UI on top.
 */
class RolaboxNavigationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", libs.findLibrary("androidx-navigation3-runtime").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-core").get())
            }
        }
    }
}
