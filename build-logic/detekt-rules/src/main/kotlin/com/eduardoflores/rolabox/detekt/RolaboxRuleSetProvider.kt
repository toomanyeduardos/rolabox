package com.eduardoflores.rolabox.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Rolabox's own detekt rules, each one the check of an ADR rule. Configured under `rolabox` in
 * `detekt.yml`. A new rule is added to the list in [instance].
 *
 * Nothing calls this class by name. detekt finds it through
 * `src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider`, a file that names it. If this
 * class is renamed or moved, that file has to change with it, or detekt silently stops running the rules.
 */
class RolaboxRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("rolabox")

    override fun instance() = RuleSet(
        ruleSetId,
        listOf(::FixedHeightAroundText, ::UseCaseOrRepositoryClassInApi, ::DefaultViewModelInComposable),
    )
}
