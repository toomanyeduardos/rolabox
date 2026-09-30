plugins {
    base
    id("rolabox.module.graph")
}

// ADR-free guard for the text styling rule: how text looks is defined once, in the design system's
// Type.kt, and screens pick a role by name. detekt can't match named arguments, so this scans the
// sources for the inline appearance parameters that used to be scattered around.
val checkTextStyling by tasks.registering {
    group = "verification"
    description = "Fails if text appearance is set inline anywhere but the design system's Type.kt"
    val sources = fileTree(layout.projectDirectory) {
        include("**/src/**/*.kt")
        exclude("**/build/**", "build-logic/**", "core/designsystem/src/main/**/theme/Type.kt")
    }
    inputs.files(sources)
    val root = layout.projectDirectory.asFile
    doLast {
        val forbidden = Regex(
            """\b(fontSize|fontWeight|fontFamily|letterSpacing|lineHeight|textDecoration)\s*=|""" +
                """\b(TextStyle|SpanStyle|ParagraphStyle)\(|\.(sp|em)\b""",
        )
        val violations = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (!line.trimStart().startsWith("//") && forbidden.containsMatchIn(line)) {
                    "${file.relativeTo(root)}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Text appearance must come from a named style in core/designsystem's Type.kt " +
                    "(RolaboxType.styles). Found inline:\n" + violations.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") { dependsOn(checkTextStyling) }
