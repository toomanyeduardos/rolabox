import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.eduardoflores.rolabox.buildlogic.BACKEND_DIMENSION
import com.eduardoflores.rolabox.buildlogic.BackendFlavor
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies the google-services plugin to the cloud flavor only (ADR-008).
 *
 * The plugin itself can't be scoped to a flavor, so its tasks are disabled for offline variants.
 * Without a google-services.json, cloud variants are disabled so a fresh clone still builds and
 * `./gradlew check` passes; asking for a cloud task explicitly fails with instructions instead.
 */
class RolaboxAndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.gms.google-services")

            val offline = BackendFlavor.OFFLINE.flavorName.replaceFirstChar(Char::uppercaseChar)
            tasks.named { it.startsWith("process$offline") && it.endsWith("GoogleServices") }
                .configureEach { enabled = false }

            val hasConfig = GOOGLE_SERVICES_LOCATIONS.any { layout.projectDirectory.file(it).asFile.exists() }
            if (hasConfig) return

            val cloud = BackendFlavor.CLOUD.flavorName
            if (gradle.startParameter.taskNames.any { it.requestsCloudApp(path, cloud) }) {
                throw GradleException(
                    "The $cloud flavor needs Firebase config: add your own google-services.json at " +
                        "${projectDir.relativeTo(rootDir).path}/${GOOGLE_SERVICES_LOCATIONS.first()}. See README, 'Building the cloud flavor'.",
                )
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants(selector().withFlavor(BACKEND_DIMENSION to cloud)) { it.enable = false }
            }
        }
    }

    private companion object {
        val GOOGLE_SERVICES_LOCATIONS = listOf("google-services.json", "src/cloud/google-services.json")
    }
}

private val APP_OUTPUT_TASK_PREFIXES = listOf("assemble", "bundle", "install")

// True for a cloud task qualified with this project's path, or an unqualified cloud task that
// produces an app. Without this, `./gradlew assembleCloudDebug` would quietly build only the
// libraries; cloud tasks meant for libraries (compileCloudDebugKotlin) still run.
private fun String.requestsCloudApp(appPath: String, cloud: String): Boolean {
    if (!contains(cloud, ignoreCase = true)) return false
    val qualifiedForApp = removePrefix(":").startsWith(appPath.removePrefix(":") + ":")
    return qualifiedForApp || (':' !in this && APP_OUTPUT_TASK_PREFIXES.any { startsWith(it) })
}
