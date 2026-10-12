package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayOutsideHostTest {
    private val rule = DeviceDisplayOutsideHost(Config.empty)

    @Test
    fun `flags a screen that draws a display and a header of its own`() {
        val findings = rule.lint(
            """
            @Composable
            fun ArtistAlbumsScreen(state: ListUiState) {
                DeviceDisplay(title = state.artistName, batteryLevel = 1f) {
                    DeviceList(state.rows, state.highlightedIndex)
                }
            }

            @Composable
            fun SongsHeader() {
                DeviceHeader("Songs", batteryLevel = 1f, record = null)
            }
            """.trimIndent(),
        )

        assertEquals(listOf(3, 10), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-019 rule 9:") })
    }

    @Test
    fun `passes a screen that draws its content, and a preview in the host's device`() {
        val findings = rule.lint(
            """
            @Composable
            fun ArtistAlbumsScreen(state: ListUiState) {
                DeviceList(state.rows, state.highlightedIndex)
            }

            @Composable
            private fun ArtistAlbumsPreview() {
                DeviceScreen(title = "Blue Harbor", batteryLevel = 0.7f, onWheelEvent = {}) {
                    ArtistAlbumsScreen(PreviewState)
                }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
