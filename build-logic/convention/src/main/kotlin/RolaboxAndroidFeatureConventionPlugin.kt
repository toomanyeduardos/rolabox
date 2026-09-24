import org.gradle.api.Plugin
import org.gradle.api.Project

class RolaboxAndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("rolabox.android.library")
            pluginManager.apply("rolabox.android.compose")
            pluginManager.apply("rolabox.hilt")
        }
    }
}
