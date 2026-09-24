import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class RolaboxHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            val hiltCompiler = libs.findLibrary("hilt-compiler").get()

            dependencies {
                add("ksp", hiltCompiler)
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.findLibrary("hilt-core").get())
                }
            }

            listOf("com.android.application", "com.android.library").forEach { androidPlugin ->
                pluginManager.withPlugin(androidPlugin) {
                    pluginManager.apply("com.google.dagger.hilt.android")
                    val hiltTesting = libs.findLibrary("hilt-android-testing").get()

                    dependencies {
                        add("implementation", libs.findLibrary("hilt-android").get())
                        add("testImplementation", hiltTesting)
                        add("kspTest", hiltCompiler)
                        add("androidTestImplementation", hiltTesting)
                        add("kspAndroidTest", hiltCompiler)
                    }
                }
            }
        }
    }
}
