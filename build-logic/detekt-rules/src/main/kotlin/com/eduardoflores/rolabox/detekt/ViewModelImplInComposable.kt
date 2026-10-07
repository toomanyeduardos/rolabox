package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * ADR-021 rule 2: no composable function has a parameter or a `hiltViewModel` call typed with a
 * `…ViewModelImpl`. A composable takes the abstract ViewModel, and only the part's entry, which
 * isn't a composable function, names the class that Hilt creates.
 *
 * It reads the syntax only: a composable function is one annotated `@Composable`, and a concrete
 * ViewModel is a type named `…ViewModelImpl`. The content lambda of an `entry<Key> { … }` is not a
 * composable function, so the entry passes.
 */
class ViewModelImplInComposable(config: Config) :
    Rule(config, "A composable function takes the abstract ViewModel, not the …ViewModelImpl that implements it.") {

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.annotationEntries.none { it.shortName?.asString() == COMPOSABLE }) return

        function.valueParameters
            .filter { it.typeReference?.text.namesViewModelImpl() }
            .forEach { parameter ->
                report(
                    parameter,
                    "ADR-021 rule 2: `${function.name}` takes `${parameter.typeReference?.text}`. Type the " +
                        "parameter with the abstract ViewModel, and create the concrete one in the part's entry.",
                )
            }

        function.bodyExpression
            ?.collectDescendantsOfType<KtCallExpression> { it.calleeExpression?.text == HILT_VIEW_MODEL }
            ?.filter { it.createsViewModelImpl() }
            ?.forEach { call ->
                report(
                    call,
                    "ADR-021 rule 2: `${function.name}` creates a `…ViewModelImpl` with `hiltViewModel`. " +
                        "Only the part's entry does, and it passes the ViewModel to the route.",
                )
            }
    }

    // hiltViewModel<FooViewModelImpl>(), or val viewModel: FooViewModelImpl = hiltViewModel().
    private fun KtCallExpression.createsViewModelImpl() =
        typeArguments.any { it.typeReference?.text.namesViewModelImpl() } ||
            (parent as? KtProperty)?.typeReference?.text.namesViewModelImpl()

    private fun String?.namesViewModelImpl() = this != null && VIEW_MODEL_IMPL.containsMatchIn(this)

    private fun report(element: KtElement, message: String) = report(Finding(Entity.from(element), message))

    private companion object {
        const val COMPOSABLE = "Composable"
        const val HILT_VIEW_MODEL = "hiltViewModel"
        val VIEW_MODEL_IMPL = Regex("""\b\w+ViewModelImpl\b""")
    }
}
