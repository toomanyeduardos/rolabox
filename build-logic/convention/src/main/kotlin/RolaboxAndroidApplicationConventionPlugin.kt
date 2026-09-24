import com.android.build.api.dsl.ApplicationExtension
import com.eduardoflores.rolabox.buildlogic.ProjectConfig
import com.eduardoflores.rolabox.buildlogic.applyStaticAnalysis
import com.eduardoflores.rolabox.buildlogic.configureBackendFlavors
import com.eduardoflores.rolabox.buildlogic.enforceModuleRules
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RolaboxAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            enforceModuleRules()
            applyStaticAnalysis()

            extensions.configure<ApplicationExtension> {
                compileSdk = ProjectConfig.COMPILE_SDK

                defaultConfig {
                    minSdk = ProjectConfig.MIN_SDK
                    targetSdk = ProjectConfig.TARGET_SDK
                }

                compileOptions {
                    sourceCompatibility = ProjectConfig.JAVA_VERSION
                    targetCompatibility = ProjectConfig.JAVA_VERSION
                }

                configureBackendFlavors()
            }
        }
    }
}
