package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * ADR-018 rule 11: a screen that needs touch or the keyboard is a full screen, and no screen mixes the
 * two. The rule reports a call to a component that is operated by touch or by the keyboard, such as a
 * button, a key or a text field, in a device screen file.
 *
 * `TouchInputInDeviceScreen` (rule 3) sees a touch modifier that a screen writes. It doesn't see one
 * that is inside a component, which is how a settings screen moved into the display could keep its keys.
 *
 * It reads the syntax only. A file is a device screen file when it imports the device host's API or one
 * of the design system's device components. The components are the rule's `components`, which
 * `config/detekt/detekt.yml` repeats, and that file is also where it is told not to run on `:device:host`
 * and `:common:designsystem`. A new touch component of the design system is added to both.
 */
class TouchComponentInDeviceScreen(config: Config) :
    Rule(config, "A device screen has no component that is operated by touch or by the keyboard.") {

    private val components: Set<String> by config(DEFAULT_COMPONENTS) { it.toSet() }

    private var isDeviceScreenFile = false

    override fun visitKtFile(file: KtFile) {
        isDeviceScreenFile = file.importDirectives.any { import ->
            val path = import.importPath?.pathStr.orEmpty()
            DEVICE_IMPORT_PREFIXES.any { path.startsWith(it) }
        }
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text ?: return
        if (!isDeviceScreenFile || name !in components) return

        report(
            Finding(
                Entity.from(expression),
                "ADR-018 rule 11: a device screen calls `$name`, which is operated by touch or by the keyboard. " +
                    "Use the display's rows and the wheel, or make it a full screen in a file of its own.",
            ),
        )
    }

    private companion object {
        val DEVICE_IMPORT_PREFIXES = listOf(
            "com.eduardoflores.rolabox.device.host.",
            "com.eduardoflores.rolabox.common.designsystem.component.Device",
        )

        val DEFAULT_COMPONENTS = listOf(
            "PrimaryButton",
            "SecondaryButton",
            "BackKey",
            "WordmarkHeader",
            "RolaboxTopBar",
            "RowKey",
            "ChoiceKey",
            "ChoiceGroup",
            "RecessedField",
            "FooterLink",
            "UnderlinedLink",
            "TextAction",
            "Button",
            "TextButton",
            "OutlinedButton",
            "IconButton",
            "TextField",
            "OutlinedTextField",
            "BasicTextField",
            "Switch",
            "Checkbox",
            "RadioButton",
            "Slider",
        )
    }
}
