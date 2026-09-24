import com.eduardoflores.rolabox.buildlogic.ProjectConfig
import com.eduardoflores.rolabox.buildlogic.applyStaticAnalysis
import com.eduardoflores.rolabox.buildlogic.enforceModuleRules
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class RolaboxJvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            enforceModuleRules()
            applyStaticAnalysis()

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = ProjectConfig.JAVA_VERSION
                targetCompatibility = ProjectConfig.JAVA_VERSION
            }

            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(ProjectConfig.JAVA_VERSION.toString()))
            }
        }
    }
}
