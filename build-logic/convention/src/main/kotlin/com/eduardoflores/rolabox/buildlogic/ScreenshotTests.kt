package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.io.File

private const val PAPARAZZI_PLUGIN = "app.cash.paparazzi"
private const val TASK_NAME = "checkScreenshotTests"

// Set by CI's `build` job, which leaves the screenshot tests to their own job (ADR-016).
private const val SKIP_PROPERTY = "rolabox.skipScreenshotTests"

// Every screenshot test class is named *SnapshotTest (ADR-016 rule 4), which is how it is skipped.
private const val SNAPSHOT_TEST_PATTERN = "*SnapshotTest"

// Store and README screenshots (ADR-039): the task that renders them, their tests, and where their files are.
private const val STORE_TASK = "storeScreenshots"
private const val STORE_TEST_TASK = "testDebugUnitTest"
private const val STORE_TEST_PATTERN = "*StoreScreenshotTest"
private const val STORE_OUTPUT = "build/store-screenshots"
private const val STORE_ART = ".screenshots-local"

// A preview annotation at the start of a line: @Preview, @PreviewLightDark, @PreviewFontScale, …
private val PREVIEW_ANNOTATION = Regex("""^\s*@Preview\w*\b""")

/**
 * Checks ADR-016 rule 2: a module with `@Preview` composables applies `rolabox.android.paparazzi` and
 * has a screenshot test (`*SnapshotTest.kt` in `src/test`) that snapshots them.
 * It runs before every build of the module (`preBuild`), and reads the sources in a task rather than
 * while configuring, so it doesn't invalidate the configuration cache.
 */
internal fun Project.registerScreenshotPluginCheck() {
    val projectPath = path
    val check = tasks.register(TASK_NAME, CheckScreenshotPluginTask::class.java) {
        group = "verification"
        description = "Checks that modules with @Preview composables apply rolabox.android.paparazzi (ADR-016)."
        modulePath.set(projectPath)
        appliesPaparazzi.set(provider { pluginManager.hasPlugin(PAPARAZZI_PLUGIN) })
        sources.from(fileTree("src") { include("main/**/*.kt", "debug/**/*.kt") })
        tests.from(fileTree("src") { include("test/**/*SnapshotTest.kt") })
        marker.set(layout.buildDirectory.file("intermediates/$TASK_NAME/ok"))
    }
    tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach { dependsOn(check) }
}

/**
 * `-Prolabox.skipScreenshotTests` leaves the screenshot tests out of the module's test runs. Store and README
 * screenshots (ADR-039) run only under `./gradlew storeScreenshots`, and every other test run leaves them out.
 */
internal fun Project.configureScreenshotTests() {
    if (providers.gradleProperty(SKIP_PROPERTY).isPresent) {
        tasks.withType(Test::class.java).configureEach {
            filter.excludeTestsMatching(SNAPSHOT_TEST_PATTERN)
            filter.isFailOnNoMatchingTests = false
        }
    }
    configureStoreScreenshots()
}

/**
 * ADR-039 rule 2. Asking for `storeScreenshots` makes the unit test tasks run only the `StoreScreenshotTest`s, never
 * up to date, and tells them where to write the images and where the README's real art is. Without it, those tests
 * are excluded, so `check`, `recordPaparazziDebug` and `verifyPaparazziDebug` never run them.
 */
private fun Project.configureStoreScreenshots() {
    val storeRun = gradle.startParameter.taskNames.any { it.substringAfterLast(':') == STORE_TASK }
    val output = File(rootDir, STORE_OUTPUT).absolutePath
    val art = File(rootDir, STORE_ART).absolutePath
    tasks.withType(Test::class.java).configureEach {
        if (storeRun) {
            filter.setIncludePatterns(STORE_TEST_PATTERN)
            filter.isFailOnNoMatchingTests = false
            systemProperty("rolabox.storeScreenshots", "true")
            systemProperty("rolabox.storeScreenshots.out", output)
            systemProperty("rolabox.storeScreenshots.art", art)
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }
        } else {
            filter.excludeTestsMatching(STORE_TEST_PATTERN)
        }
    }
    tasks.register(STORE_TASK) {
        group = "documentation"
        description = "Renders the store and README screenshots to build/store-screenshots (ADR-039)."
        dependsOn(STORE_TEST_TASK)
    }
}

@CacheableTask
internal abstract class CheckScreenshotPluginTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val appliesPaparazzi: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tests: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val previews = sources.files.sorted().filter { file ->
            file.useLines { lines -> lines.any { PREVIEW_ANNOTATION.containsMatchIn(it) } }
        }
        val listing = previews.joinToString("\n") { "  - ${it.name}" }
        if (previews.isNotEmpty() && !appliesPaparazzi.get()) {
            throw GradleException(
                "Screenshot rule violated in ${modulePath.get()}: it has @Preview composables but doesn't apply " +
                    "rolabox.android.paparazzi. ADR-016 rule 2: add the plugin and a PreviewSnapshotTest, then " +
                    "record the goldens with ./gradlew recordPaparazziDebug.\n$listing",
            )
        }
        if (previews.isNotEmpty() && tests.isEmpty) {
            throw GradleException(
                "Screenshot rule violated in ${modulePath.get()}: it has @Preview composables but no screenshot " +
                    "test. ADR-016 rule 2: add a PreviewSnapshotTest to src/test (copy one from another module), " +
                    "then record the goldens with ./gradlew recordPaparazziDebug.\n$listing",
            )
        }
        marker.get().asFile.writeText("ok")
    }
}
