import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.eduardoflores.rolabox.buildlogic.DEFAULT_ANDROID_UNIT_TEST_TASK
import com.eduardoflores.rolabox.buildlogic.ProjectConfig
import com.eduardoflores.rolabox.buildlogic.applyStaticAnalysis
import com.eduardoflores.rolabox.buildlogic.enforceModuleRules
import com.eduardoflores.rolabox.buildlogic.enforceNoProductFlavors
import com.eduardoflores.rolabox.buildlogic.registerUnitTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RolaboxAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            enforceModuleRules()
            applyStaticAnalysis()
            registerUnitTestTask(DEFAULT_ANDROID_UNIT_TEST_TASK)

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
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                finalizeDsl { it.enforceNoProductFlavors(path) }
            }
        }
    }
}
