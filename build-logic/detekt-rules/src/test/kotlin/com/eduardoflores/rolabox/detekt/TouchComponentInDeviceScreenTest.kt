package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchComponentInDeviceScreenTest {
    private val rule = TouchComponentInDeviceScreen(Config.empty)

    @Test
    fun `flags keys and a text field in a settings screen that is in the display`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
            import com.eduardoflores.rolabox.common.designsystem.component.RowKey
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun SettingsScreen(state: SettingsUiState, onRowClick: (NavKey) -> Unit) {
                HandleWheelEvents { }
                DeviceList(state.rows, state.highlightedIndex)
                RowKey(title = "Account", detail = null, onClick = { })
                ChoiceGroup { ChoiceKey("Dark", selected = true, onClick = { }) }
                RecessedField(value = "", onValueChange = { })
            }
            """.trimIndent(),
        )

        assertEquals(listOf(9, 10, 10, 11), findings.map { it.entity.location.source.line }.sorted())
        assertTrue(findings.all { it.message.startsWith("ADR-018 rule 11:") })
    }

    @Test
    fun `flags a Material control in a file that imports the device host`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun ThemeScreen() {
                HandleWheelEvents { }
                Switch(checked = true, onCheckedChange = { })
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `passes a device screen made of the display's rows`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun SettingsScreen(state: SettingsUiState, onAccountClick: () -> Unit) {
                HandleWheelEvents { }
                DeviceList(state.rows, state.highlightedIndex)
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a full screen, which is operated by touch and the keyboard`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.RecessedField
            import com.eduardoflores.rolabox.common.designsystem.component.RowKey

            @Composable
            fun AccountScreen(onBack: () -> Unit) {
                WordmarkHeader(onBack = onBack)
                RowKey(title = "Sign out", detail = null, onClick = { })
                RecessedField(value = "", onValueChange = { })
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `reads its components from the configuration`() {
        val configured = TouchComponentInDeviceScreen(TestConfig("components" to listOf("AccountKey")))

        val findings = configured.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun SettingsScreen() {
                AccountKey(onClick = { })
                RowKey(title = "Account", detail = null, onClick = { })
            }
            """.trimIndent(),
        )

        assertEquals(listOf(5), findings.map { it.entity.location.source.line })
    }
}
