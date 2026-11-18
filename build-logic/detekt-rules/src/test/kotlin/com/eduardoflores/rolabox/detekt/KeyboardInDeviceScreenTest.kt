package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardInDeviceScreenTest {
    private val rule = KeyboardInDeviceScreen(Config.empty)

    @Test
    fun `flags the keyboard, focus and the keyboard's inset in a device screen`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun SearchScreen() {
                HandleWheelEvents { }
                val keyboard = LocalSoftwareKeyboardController.current
                val focus = remember { FocusRequester() }
                Box(Modifier.imePadding().focusRequester(focus))
                val bottom = WindowInsets.ime
            }
            """.trimIndent(),
        )

        assertEquals(listOf(6, 7, 8, 8, 9), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-032 rule 2:") })
    }

    @Test
    fun `flags the design system's field called by a device screen`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.DeviceList

            @Composable
            fun SearchScreen(state: TextFieldState) {
                DeviceTextField(state, hint = "Search", shortHint = "Search")
                DeviceList(rows, 0)
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `passes a device screen that types through the host's field`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun SearchScreen(state: TextFieldState) {
                HandleWheelEvents { }
                Column(Modifier.fillMaxSize()) { Text("Search") }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a full screen, which uses the keyboard as any Material screen`() {
        val findings = rule.lint(
            """
            import androidx.compose.ui.focus.FocusRequester

            @Composable
            fun SignInScreen() {
                val focus = remember { FocusRequester() }
                Column(Modifier.imePadding()) { TextField("", {}, Modifier.focusRequester(focus)) }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
