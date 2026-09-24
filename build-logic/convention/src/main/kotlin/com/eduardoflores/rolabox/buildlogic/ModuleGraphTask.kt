package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private const val START_MARKER = "<!-- module-graph:start -->"
private const val END_MARKER = "<!-- module-graph:end -->"

/** Each entry in [edges] is "fromPath -> toPath", optionally followed by " [flavor]" for a flavor-only dependency. */
@DisableCachingByDefault(because = "Rewrites a section of a checked-in file")
abstract class ModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val edges: ListProperty<String>

    @get:Internal
    abstract val readme: RegularFileProperty

    @TaskAction
    fun write() {
        val file = readme.get().asFile
        val text = file.readText()
        val start = text.indexOf(START_MARKER)
        val end = text.indexOf(END_MARKER)
        if (start == -1 || end < start) {
            throw GradleException("${file.name} needs '$START_MARKER' and '$END_MARKER' markers")
        }
        val updated = text.substring(0, start + START_MARKER.length) +
            "\n" + mermaid() + "\n" +
            text.substring(end)
        file.writeText(updated)
    }

    private fun mermaid(): String = buildString {
        appendLine("```mermaid")
        appendLine("graph TD")
        modules.get().forEach { appendLine("    ${nodeId(it)}[\"$it\"]") }
        edges.get().forEach { edge ->
            val (from, target) = edge.split(" -> ")
            val to = target.substringBefore(" [")
            val label = target.substringAfter(" [", "").removeSuffix("]")
            val arrow = if (label.isEmpty()) "-->" else "-->|$label|"
            appendLine("    ${nodeId(from)} $arrow ${nodeId(to)}")
        }
        append("```")
    }

    private fun nodeId(path: String) = path.trimStart(':').replace(':', '_')
}
