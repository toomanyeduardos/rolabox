import com.eduardoflores.rolabox.buildlogic.configureScreenshotTests
import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

private const val DESIGNSYSTEM_PATH = ":common:designsystem"

/**
 * Screenshot tests with Paparazzi (ADR-016). Opt-in: a module applies this next to
 * `rolabox.android.library` and `rolabox.android.compose`, and the build fails if a module with
 * `@Preview` composables doesn't.
 *
 * The test harness that turns every `@Preview` into a snapshot lives in the test fixtures of
 * `:common:designsystem`, so a module's own test is a few lines naming the packages to scan.
 */
class RolaboxAndroidPaparazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.cash.paparazzi")

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                // The design system's own tests get its fixtures from the module itself.
                if (path != DESIGNSYSTEM_PATH) {
                    add("testImplementation", testFixtures(project(DESIGNSYSTEM_PATH)))
                }
            }

            configureScreenshotTests()
        }
    }
}
