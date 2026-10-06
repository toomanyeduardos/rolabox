package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * ADR-018 rule 3: a device screen has no tap, click, drag or scroll handling. Its only inputs are the
 * wheel events the device host gives it.
 *
 * It reads the syntax only. A file is a device screen file when it imports the device host's API or one
 * of the design system's device components, which is what a screen needs to be on the display. A full
 * screen, such as Settings, imports neither and is left alone. The rule reports a call to a modifier
 * that reacts to touch, in such a file. It knows nothing about modules: that it doesn't run on
 * `:device:host` and `:common:designsystem`, which implement the wheel, is its `excludes` in
 * `config/detekt/detekt.yml`.
 */
class TouchInputInDeviceScreen(config: Config) :
    Rule(config, "A device screen is operated by the wheel only, so it has no tap, click, drag or scroll handling.") {

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
        if (!isDeviceScreenFile || name !in TOUCH_MODIFIERS) return

        report(
            Finding(
                Entity.from(expression),
                "ADR-018 rule 3: a device screen calls `$name`. Remove it: the screen receives the wheel's " +
                    "events through HandleWheelEvents. A screen that needs touch is a full screen (rule 11).",
            ),
        )
    }

    private companion object {
        val DEVICE_IMPORT_PREFIXES = listOf(
            "com.eduardoflores.rolabox.device.host.",
            "com.eduardoflores.rolabox.common.designsystem.component.Device",
        )

        val TOUCH_MODIFIERS = setOf(
            "clickable",
            "combinedClickable",
            "selectable",
            "toggleable",
            "triStateToggleable",
            "pointerInput",
            "draggable",
            "draggable2D",
            "anchoredDraggable",
            "transformable",
            "scrollable",
            "verticalScroll",
            "horizontalScroll",
            "swipeable",
            "nestedScroll",
        )
    }
}
