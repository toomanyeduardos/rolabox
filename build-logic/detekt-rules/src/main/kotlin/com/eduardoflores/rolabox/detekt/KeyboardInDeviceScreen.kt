package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * ADR-032 rule 2: only the device host shows or hides the keyboard, gives the field focus or reads the
 * keyboard's inset, and a device screen places a text field only through the host's field.
 *
 * It reads the syntax only. A device screen file is one that imports the device host's API or one of the
 * design system's device components, as for `TouchInputInDeviceScreen`. The rule reports a reference to
 * the keyboard, focus and key-event APIs, to the keyboard's inset, or to the design system's
 * `DeviceTextField`, in such a file. That it doesn't run on `:device:host` and `:common:designsystem`,
 * which implement them, is its `excludes` in `config/detekt/detekt.yml`.
 */
class KeyboardInDeviceScreen(config: Config) :
    Rule(config, "Only the device host shows the keyboard on the device, and a screen types only through the host's field.") {

    private var isDeviceScreenFile = false

    override fun visitKtFile(file: KtFile) {
        isDeviceScreenFile = file.importDirectives.any { import ->
            val path = import.importPath?.pathStr.orEmpty()
            DEVICE_IMPORT_PREFIXES.any { path.startsWith(it) }
        }
        super.visitKtFile(file)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        if (!isDeviceScreenFile || expression !is KtNameReferenceExpression) return
        val name = expression.getReferencedName()
        if (name !in KEYBOARD_NAMES) return

        report(
            Finding(
                Entity.from(expression),
                "ADR-032 rule 2: a device screen uses `$name`. Only the device host shows the keyboard, gives " +
                    "focus and reads the keyboard's inset: place the host's field and let it say when typing starts and ends.",
            ),
        )
    }

    private companion object {
        val DEVICE_IMPORT_PREFIXES = listOf(
            "com.eduardoflores.rolabox.device.host.",
            "com.eduardoflores.rolabox.common.designsystem.component.Device",
        )

        val KEYBOARD_NAMES = setOf(
            "LocalSoftwareKeyboardController",
            "SoftwareKeyboardController",
            "LocalTextInputService",
            "FocusRequester",
            "focusRequester",
            "requestFocus",
            "onKeyEvent",
            "onPreviewKeyEvent",
            "imePadding",
            "imeNestedScroll",
            "ime",
            "DeviceTextField",
        )
    }
}
