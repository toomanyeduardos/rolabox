package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchInputInDeviceScreenTest {
    private val rule = TouchInputInDeviceScreen(Config.empty)

    @Test
    fun `flags touch modifiers in a file that imports the device host`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun ArtistsScreen(onArtistClick: (String) -> Unit) {
                HandleWheelEvents { }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(Modifier.clickable { onArtistClick("a") }) { }
                    Box(Modifier.pointerInput(Unit) { detectTapGestures { } })
                }
            }
            """.trimIndent(),
        )

        assertEquals(3, findings.size)
        assertEquals(listOf(6, 7, 8), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-018 rule 3:") })
    }

    @Test
    fun `flags touch modifiers in a file that imports a device component`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.common.designsystem.component.DeviceList

            @Composable
            fun Songs() {
                DeviceList(rows, 0, Modifier.draggable(state, Orientation.Vertical))
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `passes a device screen with no touch handling`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.host.HandleWheelEvents

            @Composable
            fun ArtistsScreen(onArtistClick: (String) -> Unit) {
                HandleWheelEvents { }
                Column(Modifier.fillMaxSize().padding(8.dp)) { Text("Artists") }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a full screen, which is operated by touch`() {
        val findings = rule.lint(
            """
            import androidx.compose.foundation.verticalScroll
            import com.eduardoflores.rolabox.common.designsystem.component.SecondaryButton

            @Composable
            fun SettingsScreen() {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(Modifier.clickable { }) { }
                }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
