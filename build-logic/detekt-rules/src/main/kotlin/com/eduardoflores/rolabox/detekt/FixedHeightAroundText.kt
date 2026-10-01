package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * ADR-017 rule 3: a container of text grows with it. Reports `.height(…)`, `.size(…)`,
 * `.requiredHeight(…)` and `.requiredSize(…)` in the modifier of a composable call that shows text:
 * a text composable itself, or a call whose content lambda has one somewhere inside. Spacers, rules,
 * icons and other calls without text pass.
 *
 * It reads the syntax only, so it knows a text composable by its name ([textComposables]) and doesn't
 * follow a modifier that was built somewhere else and passed in.
 */
class FixedHeightAroundText(config: Config) :
    Rule(config, "A fixed height around text clips the text when the system font scale grows.") {

    private val textComposables: Set<String> by config(DEFAULT_TEXT_COMPOSABLES) { it.toSet() }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val (content, arguments) = expression.valueArguments
            .mapNotNull { it.getArgumentExpression() }
            .partition { it is KtLambdaExpression }
        if (!expression.isTextComposable() && content.none { it.hasTextComposable() }) return

        arguments.flatMap { it.fixedHeights() }.forEach { fixed ->
            report(
                Finding(
                    Entity.from(fixed),
                    "ADR-017 rule 3: `.${fixed.name()}(…)` fixes the height of " +
                        "`${expression.name()}`, which shows text, so the text is clipped at a larger font " +
                        "scale. Use `heightIn(min = …)` or `sizeIn(…)` so it grows with its text.",
                ),
            )
        }
    }

    private fun KtCallExpression.isTextComposable() = name() in textComposables

    private fun KtElement.hasTextComposable() = collectDescendantsOfType<KtCallExpression>().any { it.isTextComposable() }

    // The calls of a modifier chain written in this argument. A lambda inside it (drawBehind { … })
    // is not part of the chain.
    private fun KtElement.fixedHeights(): List<KtCallExpression> =
        (listOfNotNull(this as? KtCallExpression) + collectDescendantsOfType<KtCallExpression>()).filter { call ->
            call.name() in FIXED_HEIGHT_MODIFIERS &&
                (call.parent as? KtQualifiedExpression)?.selectorExpression == call &&
                !call.sizesToIntrinsics() &&
                call.getStrictParentOfType<KtLambdaExpression>()?.let { !isAncestorOf(it) } != false
        }

    private fun KtElement.isAncestorOf(other: KtElement) = other.textRange.let { textRange.contains(it) && this != other }

    // height(IntrinsicSize.Min) follows the content, it doesn't fix a height.
    private fun KtCallExpression.sizesToIntrinsics() =
        valueArguments.any { it.getArgumentExpression()?.text?.contains("IntrinsicSize") == true }

    private fun KtCallExpression.name() = calleeExpression?.text.orEmpty()

    private companion object {
        val FIXED_HEIGHT_MODIFIERS = setOf("height", "size", "requiredHeight", "requiredSize")

        // Compose's own text composables. The design system's are listed in config/detekt/detekt.yml.
        val DEFAULT_TEXT_COMPOSABLES = listOf("Text", "BasicText", "TextField", "OutlinedTextField", "BasicTextField")
    }
}
