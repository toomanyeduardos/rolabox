package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * ADR-018 rule 9: on every device screen the system's back leaves the app. Neither the device host nor
 * a screen handles it, so it reaches the stack the device is on from any depth.
 *
 * It reads the syntax only. A file belongs to the device when it is in the device host's package, or
 * when it imports the host's API or one of the design system's device components, as a device screen
 * does. A full screen, such as Settings, is neither, and its back is ADR-012's. In such a file the rule
 * reports a back handler, and a `NavDisplay` that isn't built from its `sceneState`: every other
 * overload installs the library's back handler, which takes back whenever a screen is under the top one.
 */
class SystemBackInDevice(config: Config) :
    Rule(config, "The system's back leaves the app from every device screen, so the device never handles it.") {

    private var isDeviceFile = false

    override fun visitKtFile(file: KtFile) {
        isDeviceFile = file.packageFqName.asString().startsWith(DEVICE_HOST_PACKAGE) ||
            file.importDirectives.any { import ->
                val path = import.importPath?.pathStr.orEmpty()
                DEVICE_IMPORT_PREFIXES.any { path.startsWith(it) }
            }
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!isDeviceFile) return
        val name = expression.calleeExpression?.text ?: return

        if (name in BACK_HANDLERS) {
            report(
                Finding(
                    Entity.from(expression),
                    "ADR-018 rule 9: the device calls `$name`. Remove it: the system's back leaves the app from " +
                        "every device screen, and MENU is the only way back inside the display.",
                ),
            )
        } else if (name == NAV_DISPLAY && !expression.isBuiltFromSceneState()) {
            report(
                Finding(
                    Entity.from(expression),
                    "ADR-018 rule 9: this `NavDisplay` handles the system's back. Build it from a `sceneState`, " +
                        "passed by name, which is the overload with no back handler.",
                ),
            )
        }
    }

    private fun KtCallExpression.isBuiltFromSceneState() =
        valueArguments.any { it.getArgumentName()?.asName?.asString() == SCENE_STATE }

    private companion object {
        const val DEVICE_HOST_PACKAGE = "com.eduardoflores.rolabox.device.host"
        const val NAV_DISPLAY = "NavDisplay"
        const val SCENE_STATE = "sceneState"

        val DEVICE_IMPORT_PREFIXES = listOf(
            "$DEVICE_HOST_PACKAGE.",
            "com.eduardoflores.rolabox.common.designsystem.component.Device",
        )

        val BACK_HANDLERS = setOf(
            "BackHandler",
            "PredictiveBackHandler",
            "NavigationBackHandler",
            "NavigationEventHandler",
        )
    }
}
