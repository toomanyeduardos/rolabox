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
import java.io.File

private const val APP_PATH = ":app"
private const val DESIGNSYSTEM_PATH = ":common:designsystem"
private const val DESIGNSYSTEM_PREFIX = "ds_"
private const val TASK_NAME = "checkDesignSystemResources"

// Value resources that carry a name, and so can be overridden by a same-named one in another module.
private val NAMED_VALUE = Regex("""<(string|plurals|string-array|color|dimen|style|integer|bool)\s[^>]*name="([^"]+)"""")
private val COLOR_VALUE = Regex("""<color\s""")
private val STYLE_VALUE = Regex("""<style\s""")

/**
 * Checks the resource rules of ADR-015. It runs before every build of the module (`preBuild`), and
 * reads the resource folders in a task rather than while configuring, so it doesn't invalidate the
 * configuration cache.
 */
internal fun Project.registerDesignSystemResourceCheck() {
    val projectPath = path
    val check = tasks.register(TASK_NAME, CheckDesignSystemResourcesTask::class.java) {
        group = "verification"
        description = "Checks that Rolabox's visual resources live only in $DESIGNSYSTEM_PATH (ADR-015)."
        modulePath.set(projectPath)
        resources.from(fileTree("src") { include("*/res/**") })
        marker.set(layout.buildDirectory.file("intermediates/$TASK_NAME/ok"))
    }
    tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach { dependsOn(check) }
}

@CacheableTask
internal abstract class CheckDesignSystemResourcesTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val module = modulePath.get()
        val violations = resources.files.flatMap { file ->
            if (module == DESIGNSYSTEM_PATH) prefixViolations(file) else ownershipViolations(module, file)
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Design system rule violated in $module:\n" + violations.sorted().joinToString("\n") { "  - $it" },
            )
        }
        marker.get().asFile.writeText("ok")
    }

    // ADR-015 rules 2 and 3: fonts, colors and styles are the design system's.
    private fun ownershipViolations(module: String, file: File): List<String> {
        val folder = file.parentFile.name
        return when {
            folder == "font" || folder.startsWith("font-") ->
                listOf("${file.relative()}: ADR-015 rule 2: fonts live only in $DESIGNSYSTEM_PATH")

            folder.isValuesFolder() -> buildList {
                val text = file.readText()
                if (COLOR_VALUE.containsMatchIn(text)) {
                    add("${file.relative()}: ADR-015 rule 2: color resources live only in $DESIGNSYSTEM_PATH")
                }
                if (module != APP_PATH && STYLE_VALUE.containsMatchIn(text)) {
                    add(
                        "${file.relative()}: ADR-015 rule 3: styles live only in $DESIGNSYSTEM_PATH, " +
                            "and the window themes in $APP_PATH",
                    )
                }
            }

            else -> emptyList()
        }
    }

    // ADR-015 rule 7: every design system resource starts with ds_, so no other module overrides it by accident.
    private fun prefixViolations(file: File): List<String> {
        val folder = file.parentFile.name
        return if (folder.isValuesFolder()) {
            NAMED_VALUE.findAll(file.readText())
                .map { it.groupValues[2] }
                .filterNot { it.startsWith(DESIGNSYSTEM_PREFIX) }
                .map { "${file.relative()}: '$it': ADR-015 rule 7: design system resources start with $DESIGNSYSTEM_PREFIX" }
                .toList()
        } else if (!file.name.startsWith(DESIGNSYSTEM_PREFIX)) {
            listOf("${file.relative()}: ADR-015 rule 7: design system resources start with $DESIGNSYSTEM_PREFIX")
        } else {
            emptyList()
        }
    }

    private fun String.isValuesFolder() = this == "values" || startsWith("values-")

    private fun File.relative() = "${parentFile.parentFile.parentFile.name}/res/${parentFile.name}/$name"
}
