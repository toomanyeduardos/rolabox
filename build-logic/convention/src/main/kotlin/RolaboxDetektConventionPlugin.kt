import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * detekt on top of its default rules, with project overrides in `config/detekt/detekt.yml`.
 * No baseline: findings must be fixed or suppressed at the call site.
 */
class RolaboxDetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
                parallel.set(true)
            }

            // `check` only runs the plain `detekt` task by default; rules that need
            // type resolution (e.g. UnusedImport) only run in the per-variant tasks.
            tasks.named("check").configure {
                dependsOn("detektMain", "detektTest")
            }
        }
    }
}
