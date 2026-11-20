package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * ADR-034 rule 3: plain HTTP is allowed for podcast hosts, which Rolabox doesn't choose, and only there. A plain URL
 * anywhere else is a mistake, since the services Rolabox talks to are all HTTPS.
 *
 * The rule reports a string literal, plain or raw, whose text starts with `http://`, in any case. It knows nothing
 * about modules: that it doesn't run on the podcasts area or on tests is its `excludes` in `config/detekt/detekt.yml`.
 * A literal that has to stay, such as an XML namespace, is a `@Suppress` naming
 * the rule.
 */
class CleartextUrlOutsidePodcasts(config: Config) :
    Rule(config, "Only the podcasts area writes http:// URLs; everything else Rolabox talks to is HTTPS.") {

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        val first = expression.entries.firstOrNull() as? KtLiteralStringTemplateEntry ?: return
        if (first.text.startsWith(HTTP, ignoreCase = true)) {
            report(
                Finding(
                    Entity.from(expression),
                    "ADR-034 rule 3: an http:// URL outside the podcasts area. Use https://.",
                ),
            )
        }
    }

    private companion object {
        const val HTTP = "http://"
    }
}
