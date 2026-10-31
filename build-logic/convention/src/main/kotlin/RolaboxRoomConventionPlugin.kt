import androidx.room.gradle.RoomExtension
import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room for a `:local:impl` module (ADR-024, rule 4), with each version of its schema exported into Git (rule 10). */
class RolaboxRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-room-runtime").get())
                add("ksp", libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
