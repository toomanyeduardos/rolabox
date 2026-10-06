package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceComponentOutsideDesignSystemTest {
    private val rule = DeviceComponentOutsideDesignSystem(Config.empty)

    @Test
    fun `flags a copy of the wheel's event vocabulary`() {
        val findings = rule.lint(
            """
            sealed interface WheelEvent {
                data object Center : WheelEvent
            }

            enum class HoldState { Held, Released }
            """.trimIndent(),
        )

        assertEquals(listOf(1, 5), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-018 rule 12:") })
    }

    @Test
    fun `flags a device component declared as a composable or as a row type`() {
        val findings = rule.lint(
            """
            @Composable
            fun Wheel(onEvent: (Int) -> Unit) = Unit

            @Composable
            internal fun DeviceList(rows: List<DeviceListRow>) = Unit

            data class DeviceListRow(val label: String)
            """.trimIndent(),
        )

        assertEquals(3, findings.size)
    }

    @Test
    fun `passes declarations that use the design system or are the host's own`() {
        val findings = rule.lint(
            """
            @Composable
            fun DeviceScreen(title: String, onWheelEvent: (WheelEvent) -> Unit) {
                DeviceBody { DeviceDisplay { } }
            }

            @Composable
            internal fun WheelList(rows: List<DeviceListRow>) = Unit

            class WheelEventRouter {
                fun route(event: WheelEvent) = Unit
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
