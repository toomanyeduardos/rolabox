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
 * ADR-031 rule 13: only the podcasts' network side reads feeds. Every other module reads shows and
 * episodes through the podcasts' `:api`, which the parsed feed reaches as the part's own models.
 *
 * The rule reports an import of the XML pull parser, `org.xmlpull.v1` or anything inside it, or of
 * `android.util.Xml`, the platform's factory of it, and a fully qualified use of either with no import.
 * It knows nothing about modules: that it doesn't run on `:device:podcasts:network:impl` is its
 * `excludes` in `config/detekt/detekt.yml`.
 */
class FeedParserOutsidePodcastsNetwork(config: Config) :
    Rule(config, "Only the podcasts' network side (:device:podcasts:network:impl) parses XML feeds.") {

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importPath?.pathStr ?: return
        if (isParser(path)) reportAt(importDirective)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        // Only the innermost match of a chain, so a use is reported once. An import's path is the same
        // expression, and is reported as the import.
        if (expression.getStrictParentOfType<KtImportDirective>() != null) return
        if (expression.text == XML || expression.text == XML_PULL) reportAt(expression)
    }

    private fun isParser(path: String) =
        path == XML || path.startsWith("$XML.") || path == XML_PULL || path.startsWith("$XML_PULL.")

    private fun reportAt(element: KtElement) {
        report(
            Finding(
                Entity.from(element),
                "ADR-031 rule 13: an XML pull parser is used outside :device:podcasts:network:impl. Read " +
                    "shows and episodes through the podcasts' :api.",
            ),
        )
    }

    private companion object {
        const val XML = "android.util.Xml"
        const val XML_PULL = "org.xmlpull.v1"
    }
}
