import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** What an Android library's unit tests need to run on Robolectric. */
class RolaboxAndroidRobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    testOptions {
                        // Robolectric reads the module's merged manifest and resources.
                        unitTests.isIncludeAndroidResources = true
                        // Robolectric reads the internals of a FileDescriptor, which the JDK no longer exports.
                        unitTests.all { it.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED") }
                    }
                }
            }
        }
    }
}
