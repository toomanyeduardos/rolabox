import com.android.build.api.dsl.LibraryExtension
import com.eduardoflores.rolabox.buildlogic.libs
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

private const val ANDROID_ALL_CONFIGURATION = "robolectricAndroidAll"
private val ANDROID_ALL_LIBRARIES =
    listOf("robolectric-android-all-sdk31", "robolectric-android-all-sdk36", "robolectric-android-all-sdk37")

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
            // Paparazzi's layoutlib and Robolectric's native graphics each bring their own Android classes and native code,
            // and the second to load in a JVM crashes it. A module with both runs each test class in a JVM of its own.
            pluginManager.withPlugin("app.cash.paparazzi") {
                tasks.withType<Test>().configureEach { forkEvery = 1 }
            }
            resolveAndroidAllWithGradle()
        }
    }
}

/**
 * Robolectric downloads the Android framework jar of each SDK from Maven Central the first time a test needs it, with
 * its own downloader, which doesn't retry: a single failed request fails the test class (`MavenArtifactFetcher`, in CI).
 * Gradle resolves the jars instead, with its retries and its dependency cache, and the tests read them through
 * `robolectric-deps.properties`, which maps each jar's coordinates to its file. An SDK missing from the list fails
 * the test with the coordinates to add, instead of downloading.
 */
private fun Project.resolveAndroidAllWithGradle() {
    // One configuration per SDK: the jars are versions of one module, and in one configuration Gradle would resolve
    // them to the newest.
    val perSdk =
        ANDROID_ALL_LIBRARIES.map { library ->
            configurations.create("${ANDROID_ALL_CONFIGURATION}_${library.substringAfterLast('-')}") {
                isCanBeConsumed = false
                isTransitive = false
            }.also { dependencies.add(it.name, libs.findLibrary(library).get()) }
        }
    val androidAll = files(perSdk)

    val writeDeps =
        tasks.register<WriteRobolectricDeps>("writeRobolectricDeps") {
            jars.from(androidAll)
            perSdk.forEach { configuration ->
                entries.putAll(
                    configuration.incoming.artifacts.resolvedArtifacts.map { resolved ->
                        resolved.associate { artifact ->
                            val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
                            "${id.group}:${id.module}:${id.version}" to artifact.file.absolutePath
                        }
                    },
                )
            }
            depsFile.set(layout.buildDirectory.file("robolectric/robolectric-deps.properties"))
        }

    tasks.withType<Test>().configureEach {
        dependsOn(writeDeps)
        jvmArgumentProviders.add(RobolectricDepsArgument(writeDeps.flatMap { it.depsFile }, androidAll))
    }
}

/** Writes the `robolectric-deps.properties` that maps each Android framework jar's coordinates to its file. */
abstract class WriteRobolectricDeps : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jars: ConfigurableFileCollection

    @get:Input
    abstract val entries: MapProperty<String, String>

    @get:OutputFile
    abstract val depsFile: RegularFileProperty

    @TaskAction
    fun write() {
        // Properties' own format: backslashes and colons in keys are escaped, so Windows paths survive too.
        val properties = java.util.Properties()
        entries.get().forEach { (coordinates, path) -> properties.setProperty(coordinates, path) }
        depsFile.get().asFile.outputStream().use { properties.store(it, "Written by writeRobolectricDeps") }
    }
}

/**
 * Points Robolectric at the deps file. The jars are the input rather than the file, which holds absolute paths, so where
 * the Gradle cache is on disk doesn't change the test task's inputs.
 */
private class RobolectricDepsArgument(
    @get:Internal val depsFile: Provider<RegularFile>,
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jars: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Drobolectric-deps.properties=${depsFile.get().asFile.absolutePath}")
}
