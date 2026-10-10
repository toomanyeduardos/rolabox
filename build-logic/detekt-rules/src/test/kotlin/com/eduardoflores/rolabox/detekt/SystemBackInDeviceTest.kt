package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBackInDeviceTest {
    private val rule = SystemBackInDevice(Config.empty)

    @Test
    fun `flags a back handler in a device screen`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun NowPlayingRoute(viewModel: NowPlayingViewModel) {
                HandleWheelEvents { }
                BackHandler(enabled = scrubbing) { viewModel.onMenu() }
                PredictiveBackHandler { }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(6, 7), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-018 rule 9:") })
    }

    @Test
    fun `flags a back handler in a file that imports a device component`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.DeviceList

            @Composable
            fun Songs() {
                BackHandler { }
                DeviceList(rows, 0)
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags a NavDisplay that pops on back in the device host`() {
        val findings = rule.lint(
            """
            package com.eduardoflores.rolabox.device.host

            @Composable
            internal fun ScreenStackDisplay(stack: ScreenStack) {
                NavDisplay(backStack = stack.keys, onBack = stack::pop, entryProvider = { entry(it) })
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.startsWith("ADR-018 rule 9:"))
    }

    @Test
    fun `flags a NavDisplay with the default back in the device host`() {
        val findings = rule.lint(
            """
            package com.eduardoflores.rolabox.device.host

            @Composable
            internal fun ScreenStackDisplay(stack: ScreenStack) {
                NavDisplay(stack.keys, entryProvider = { entry(it) })
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `passes a NavDisplay built from its scene state`() {
        val findings = rule.lint(
            """
            package com.eduardoflores.rolabox.device.host

            @Composable
            internal fun ScreenStackDisplay(stack: ScreenStack) {
                val sceneState = rememberSceneState(entries, SinglePaneSceneStrategy(), onBack = {})
                NavDisplay(
                    sceneState = sceneState,
                    navigationEventState = rememberNavigationEventState(sceneState),
                )
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a device screen that handles no back`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleMenu
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun NowPlayingRoute(viewModel: NowPlayingViewModel) {
                HandleWheelEvents { }
                HandleMenu(enabled = scrubbing, onMenu = viewModel::onMenu)
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a full screen and the app stack, where back is ADR-012's`() {
        val findings = rule.lint(
            """
            package com.eduardoflores.rolabox

            @Composable
            fun RolaboxNavigation(navigator: AppNavigator) {
                BackHandler { }
                NavDisplay(backStack = navigator.currentBackStack, onBack = navigator::pop, entryProvider = { entry(it) })
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
