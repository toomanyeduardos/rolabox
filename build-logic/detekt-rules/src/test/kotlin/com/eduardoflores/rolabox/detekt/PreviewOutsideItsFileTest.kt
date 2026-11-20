package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewOutsideItsFileTest {
    private val rule = PreviewOutsideItsFile(Config.empty)

    private fun lint(code: String, fileName: String = "ArtistsScreen.kt") =
        rule.lint(compileContentForTest(code.trimIndent(), fileName))

    @Test
    fun `flags a file of previews by its name`() {
        val findings = lint(
            """
            package com.example

            @PreviewLightDark
            @Composable
            private fun ArtistsPreview() {
                MusicList(Artists, highlightedIndex = 0)
            }
            """,
            fileName = "MusicPreviews.kt",
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.startsWith("ADR-016 rule 10:"))
    }

    @Test
    fun `flags each preview in a file that creates no composable`() {
        val findings = lint(
            """
            package com.example

            private val Artists = listOf("Adeline Park")

            @Composable
            private fun DevicePreview(content: @Composable () -> Unit) = RolaboxTheme { content() }

            @Composable
            private fun artistRows() = Artists.map { DeviceListRow(it) }

            @PreviewLightDark
            @Composable
            private fun ArtistsPreview() = DevicePreview { MusicList(artistRows(), highlightedIndex = 0) }

            @Preview
            @Composable
            private fun SongsPreview() = DevicePreview { MusicList(Songs, highlightedIndex = 0) }
            """,
            fileName = "Samples.kt",
        )

        assertEquals(listOf(13, 17), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-016 rule 10:") })
    }

    @Test
    fun `passes previews next to the route of the screen they show`() {
        val findings = lint(
            """
            package com.example

            @Composable
            internal fun ArtistsRoute(viewModel: ArtistsViewModel) {
                WheelList(state = viewModel.rows)
            }

            @PreviewLightDark
            @Composable
            private fun ArtistsPreview() {
                RolaboxTheme {
                    DeviceScreen(title = "Artists", batteryLevel = 0.7f, onWheelEvent = {}) {
                        MusicList(Artists, highlightedIndex = 0)
                    }
                }
            }
            """,
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes previews next to a private screen that only the file's entry calls`() {
        val findings = lint(
            """
            package com.example

            internal fun EntryProviderScope<NavKey>.mainMenuEntry() = entry<MainMenuKey> { MainMenu() }

            @Composable
            private fun MainMenu() = MainMenuList(rows = MenuRow.entries, highlightedIndex = 0)

            @PreviewLightDark
            @Composable
            private fun MainMenuPreview() = RolaboxTheme { MainMenu() }
            """,
            fileName = "MainMenu.kt",
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a public component or modifier with its previews, and a file with no previews`() {
        val component = lint(
            """
            package com.example

            @Composable
            fun DeviceListRow(label: String) { Text(label) }

            @Preview
            @Composable
            private fun DeviceListRowPreview() { DeviceListRow("Coastline") }
            """,
            fileName = "DeviceListRow.kt",
        )
        val noPreviews = lint(
            """
            package com.example

            @Composable
            private fun Helper() = Unit
            """,
        )

        val modifier = lint(
            """
            package com.example

            @Composable
            fun Modifier.brushedMetal(): Modifier = this

            @PreviewLightDark
            @Composable
            private fun BrushedMetalPreview() { Box(Modifier.brushedMetal()) }
            """,
            fileName = "BrushedMetal.kt",
        )

        assertEquals(emptyList<Any>(), component + noPreviews + modifier)
    }

    @Test
    fun `passes the declaration of a preview annotation`() {
        val findings = lint(
            """
            package com.example

            @Preview(name = "Light")
            @Preview(name = "Dark", uiMode = 32)
            annotation class PreviewLightDark
            """,
            fileName = "PreviewLightDark.kt",
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
