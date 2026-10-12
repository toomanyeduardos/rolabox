package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * ADR-019 rule 9: the display and its header are drawn by the device host only. A screen gives the
 * host its title, and never draws a display or a header of its own.
 *
 * The rule reports a call to the design system's `DeviceDisplay` or `DeviceHeader`. It knows nothing
 * about modules: that it doesn't run on `:device:host`, which assembles the device, and on
 * `:common:designsystem`, which declares and previews the two, is its `excludes` in
 * `config/detekt/detekt.yml`. A preview of a screen in the device uses the host's `DeviceScreen`.
 */
class DeviceDisplayOutsideHost(config: Config) :
    Rule(config, "Only the device host draws the display and its header.") {

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text ?: return
        if (name !in DISPLAY_COMPONENTS) return

        report(
            Finding(
                Entity.from(expression),
                "ADR-019 rule 9: `$name` is called outside :device:host. The host draws the display and its " +
                    "header: a screen gives its title through the host's contract, and a preview uses DeviceScreen.",
            ),
        )
    }

    private companion object {
        val DISPLAY_COMPONENTS = setOf("DeviceDisplay", "DeviceHeader")
    }
}
