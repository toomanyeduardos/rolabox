package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * ADR-016 rule 10: a preview is in the file that creates what it shows, so no file holds previews alone.
 *
 * It reports a file named `*Previews.kt`, and each preview in a file that creates no screen or component.
 * A preview is a function with an annotation whose name starts with `Preview` (`@Preview`,
 * `@PreviewLightDark`, …). What a file creates is a composable that is neither a preview nor a helper
 * named `…Preview`, and that draws (PascalCase, even when `private`, like a screen that only its file's
 * entry calls) or is used outside the file (such as a public `Modifier.brushedMetal()`). A `private`
 * camelCase composable returns a value for the previews (`ownerRows()`, `showWords()`) and doesn't
 * count. It reads the syntax only, so whether the preview is in the file of the right screen
 * is left to review.
 */
class PreviewOutsideItsFile(config: Config) :
    Rule(config, "A preview lives in the file that creates the screen or component it shows.") {

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val functions = file.collectDescendantsOfType<KtNamedFunction>()
        val previews = functions.filter { it.isPreview() }
        if (file.name.endsWith(PREVIEWS_FILE_SUFFIX)) {
            report(
                Finding(
                    Entity.atPackageOrFirstDecl(file),
                    "ADR-016 rule 10: `${file.name}` is a file of previews. Move each preview into the file " +
                        "that creates the screen or component it shows, and delete this file.",
                ),
            )
            return
        }
        if (previews.isEmpty()) return
        if (functions.any { it.createsUi() }) return
        previews.forEach { preview ->
            report(
                Finding(
                    Entity.atName(preview),
                    "ADR-016 rule 10: `${preview.name}` is in a file that creates no screen or component. " +
                        "Move it into the file of the composable it shows.",
                ),
            )
        }
    }

    private fun KtNamedFunction.isPreview() =
        annotationEntries.any { it.shortName?.asString()?.startsWith(PREVIEW) == true }

    private fun KtNamedFunction.isComposable() =
        annotationEntries.any { it.shortName?.asString() == COMPOSABLE }

    private fun KtNamedFunction.createsUi(): Boolean {
        val name = name ?: return false
        if (!isComposable() || isPreview() || name.endsWith(PREVIEW)) return false
        return name.first().isUpperCase() || !hasModifier(KtTokens.PRIVATE_KEYWORD)
    }

    private companion object {
        const val COMPOSABLE = "Composable"
        const val PREVIEW = "Preview"
        const val PREVIEWS_FILE_SUFFIX = "Previews.kt"
    }
}
