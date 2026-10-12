import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.eduardoflores.rolabox.buildlogic.DEFAULT_ANDROID_UNIT_TEST_TASK
import com.eduardoflores.rolabox.buildlogic.ProjectConfig
import com.eduardoflores.rolabox.buildlogic.applyStaticAnalysis
import com.eduardoflores.rolabox.buildlogic.enforceModuleRules
import com.eduardoflores.rolabox.buildlogic.enforceNoProductFlavors
import com.eduardoflores.rolabox.buildlogic.registerDesignSystemResourceCheck
import com.eduardoflores.rolabox.buildlogic.registerScreenshotPluginCheck
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
            registerScreenshotPluginCheck()

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
                // A module without instrumented tests builds no androidTest APK. One with test fixtures would
                // package them in it, and Paparazzi's are for the JVM only (ADR-016).
                val hasAndroidTests = file("src/androidTest").exists()
                beforeVariants { it.androidTest.enable = hasAndroidTests }
            }
        }
    }
}
