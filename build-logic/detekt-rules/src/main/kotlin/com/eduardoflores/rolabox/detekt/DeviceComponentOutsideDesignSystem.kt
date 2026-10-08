package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * ADR-018 rule 12: the device's body, display, rows and wheel, and the wheel's event vocabulary, live
 * in `:common:designsystem`. The rule reports a class, object, interface or function declared with the
 * name of one of them, in a place that isn't the design system.
 *
 * The names are the rule's `names`, which `config/detekt/detekt.yml` repeats, and that file is also where
 * it is told not to run on `:common:designsystem`. A new device component or event type is added to both.
 */
class DeviceComponentOutsideDesignSystem(config: Config) :
    Rule(config, "The device's parts and the wheel's event vocabulary are declared in :common:designsystem only.") {

    private val names: Set<String> by config(DEFAULT_NAMES) { it.toSet() }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        classOrObject.nameIdentifier?.let { check(it.text, classOrObject) }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        function.name?.let { check(it, function) }
    }

    private fun check(name: String, declaration: KtNamedDeclaration) {
        if (name !in names) return
        report(
            Finding(
                Entity.atName(declaration),
                "ADR-018 rule 12: `$name` is declared outside :common:designsystem. The device's parts and the " +
                    "wheel's events are defined there only: use the design system's, or add the change to it.",
            ),
        )
    }

    private companion object {
        val DEFAULT_NAMES = listOf(
            "WheelEvent",
            "HoldState",
            "Wheel",
            "DeviceBody",
            "DeviceDisplay",
            "DeviceHeader",
            "DeviceList",
            "DeviceListRow",
            "DeviceMessage",
            "DeviceProgressBar",
            "ProgressBarMode",
            "DeviceCoverArtPlaceholder",
        )
    }
}
