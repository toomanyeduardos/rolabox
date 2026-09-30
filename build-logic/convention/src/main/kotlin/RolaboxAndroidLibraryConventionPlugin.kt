import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.eduardoflores.rolabox.buildlogic.DEFAULT_ANDROID_UNIT_TEST_TASK
import com.eduardoflores.rolabox.buildlogic.ProjectConfig
import com.eduardoflores.rolabox.buildlogic.applyStaticAnalysis
import com.eduardoflores.rolabox.buildlogic.enforceModuleRules
import com.eduardoflores.rolabox.buildlogic.enforceNoProductFlavors
import com.eduardoflores.rolabox.buildlogic.registerDesignSystemResourceCheck
import com.eduardoflores.rolabox.buildlogic.registerUnitTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RolaboxAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            enforceModuleRules()
            applyStaticAnalysis()
            registerUnitTestTask(DEFAULT_ANDROID_UNIT_TEST_TASK)
            registerDesignSystemResourceCheck()

            extensions.configure<LibraryExtension> {
                compileSdk = ProjectConfig.COMPILE_SDK

                defaultConfig {
                    minSdk = ProjectConfig.MIN_SDK
                }

                compileOptions {
                    sourceCompatibility = ProjectConfig.JAVA_VERSION
                    targetCompatibility = ProjectConfig.JAVA_VERSION
                }
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                finalizeDsl { it.enforceNoProductFlavors(path) }
            }
        }
    }
}
