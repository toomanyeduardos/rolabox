package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * ADR-024 rule 2: only the library's local side finds the music on the phone. Every other module reads
 * the library through `LibraryRepository`, and playback opens the URIs it gives.
 *
 * The rule reports an import of `android.provider.MediaStore` or of anything inside it, and a fully
 * qualified use of it with no import. It knows nothing about modules: that it doesn't run on
 * `:device:library:local:impl` is its `excludes` in `config/detekt/detekt.yml`.
 */
class MediaStoreOutsideLibrary(config: Config) :
    Rule(config, "Only the library's local side (:device:library:local:impl) uses MediaStore.") {

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importPath?.pathStr ?: return
        if (path == MEDIA_STORE || path.startsWith("$MEDIA_STORE.")) reportAt(importDirective)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        // Only the innermost `android.provider.MediaStore` of a chain, so a use is reported once. An
        // import's path is the same expression, and is reported as the import.
        if (expression.text == MEDIA_STORE && expression.getStrictParentOfType<KtImportDirective>() == null) {
            reportAt(expression)
        }
    }

    private fun reportAt(element: KtElement) {
        report(
            Finding(
                Entity.from(element),
                "ADR-024 rule 2: MediaStore is used outside :device:library:local:impl. Read the library " +
                    "through LibraryRepository, and play a song from its URI.",
            ),
        )
    }

    private companion object {
        const val MEDIA_STORE = "android.provider.MediaStore"
    }
}
