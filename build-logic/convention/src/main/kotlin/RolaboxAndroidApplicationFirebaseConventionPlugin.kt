import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.gms.googleservices.GoogleServicesTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

/**
 * Applies the google-services plugin with the Firebase config from ADR-008: the developer's own
 * `google-services.json` when it exists (git-ignored, never committed), and otherwise the committed
 * placeholder, which points at no real project. So a fresh clone builds and runs with no setup. With
 * the placeholder, Firebase requests fail and sign-in doesn't work, but offline mode does.
 *
 * The placeholder has a different name, so the plugin never finds it by itself, and adding a real
 * file never changes a tracked one. It's handed to the plugin's tasks here, never copied into place.
 */
class RolaboxAndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.gms.google-services")

            // The plugin would also read a google-services.json under src/, and this plugin would
            // silently ignore it. Only one place is read, so a file anywhere else is a mistake.
            val misplaced = file("src").listFiles().orEmpty()
                .map { it.resolve(REAL_CONFIG) }
                .plus(file("src").resolve(REAL_CONFIG))
                .filter { it.isFile }
            if (misplaced.isNotEmpty()) {
                throw GradleException(
                    "Firebase config found at ${misplaced.joinToString { it.relativeTo(rootDir).path }}. " +
                        "ADR-008: the only place it's read from is ${file(REAL_CONFIG).relativeTo(rootDir).path}.",
                )
            }

            val real = file(REAL_CONFIG)
            val config = if (real.isFile) real else file(PLACEHOLDER_CONFIG)
            if (config == real) {
                logger.info("Firebase config: ${real.relativeTo(rootDir).path}")
            } else {
                logger.lifecycle(
                    "Firebase config: the placeholder (${config.relativeTo(rootDir).path}). The app works " +
                        "offline, but sign-in and sync won't. See README, 'Building with Firebase'.",
                )
            }

            // The plugin registers each variant's task in its own onVariants callback, which was added
            // first, and sets the files there. Configuring the task from here runs after that.
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    val taskName = "process${variant.name.replaceFirstChar(Char::uppercaseChar)}GoogleServices"
                    tasks.named<GoogleServicesTask>(taskName).configure {
                        googleServicesJsonFiles.set(listOf(config))
                    }
                }
            }
        }
    }

    private companion object {
        const val REAL_CONFIG = "google-services.json"
        const val PLACEHOLDER_CONFIG = "google-services.placeholder.json"
    }
}
