import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/**
 * ktlint with the Compose rule set. Style settings live in the root `.editorconfig`,
 * which ktlint and the IDE both read.
 */
class RolaboxKtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<KtlintExtension> {
                version.set(libs.findVersion("ktlint").get().requiredVersion)
                android.set(true)
                ignoreFailures.set(false)
                reporters {
                    reporter(ReporterType.PLAIN)
                    reporter(ReporterType.CHECKSTYLE)
                }
            }

            dependencies {
                add("ktlintRuleset", libs.findLibrary("compose-rules-ktlint").get())
            }
        }
    }
}
