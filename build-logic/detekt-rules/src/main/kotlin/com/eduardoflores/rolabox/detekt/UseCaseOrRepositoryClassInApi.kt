package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClass

/**
 * ADR-021 rule 1: an `:api` module declares no class whose name ends in `UseCase` or `Repository`.
 * Those are interfaces there, and the class that implements one is in the part's `:impl`.
 *
 * The rule reports every such class it is run on, and knows nothing about modules. That it runs on
 * `:api` modules only is its `includes` in `config/detekt/detekt.yml`.
 */
class UseCaseOrRepositoryClassInApi(config: Config) :
    Rule(config, "A use case or a repository is an interface in an :api module, implemented in the :impl.") {

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        val name = klass.name ?: return
        if (klass.isInterface() || CONTRACT_SUFFIXES.none { name.endsWith(it) }) return

        report(
            Finding(
                Entity.atName(klass),
                "ADR-021 rule 1: `$name` is a class in an :api module. Declare it as an interface here, and " +
                    "move the class to the part's :impl as `Default$name`, bound with @Binds.",
            ),
        )
    }

    private companion object {
        val CONTRACT_SUFFIXES = listOf("UseCase", "Repository")
    }
}
