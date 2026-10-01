package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class FixedHeightAroundTextTest {
    private val rule = FixedHeightAroundText(Config.empty)

    @Test
    fun `flags a fixed height on a container of text`() {
        val findings = rule.lint(
            """
            fun Key(text: String) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(text)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
        assertEquals(5, findings.single().entity.location.source.line)
    }

    @Test
    fun `flags a fixed size on a text composable and on text nested deeper`() {
        val findings = rule.lint(
            """
            fun Badge() {
                Text("!", modifier = Modifier.size(16.dp))
                Row(Modifier.requiredHeight(46.dp)) {
                    Column { Text("left") }
                }
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `passes a spacer, a rule and an icon`() {
        val findings = rule.lint(
            """
            fun Divider() {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(color))
                Box(Modifier.size(48.dp)) { Icon(icon, null, Modifier.size(20.dp)) }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a container that grows with its text`() {
        val findings = rule.lint(
            """
            fun Key(text: String) {
                Box(Modifier.heightIn(min = 52.dp).drawBehind { drawRect(color, size = area.size(2)) }) { Text(text) }
                Row(Modifier.height(IntrinsicSize.Min)) { Text(text) }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a suppressed exception`() {
        val findings = rule.lint(
            """
            @Suppress("FixedHeightAroundText") // ADR-017 rule 3: the size is scaled by the font scale.
            fun Badge(size: Dp) {
                Box(Modifier.size(size)) { Text("!") }
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `knows the design system's text components from its configuration`() {
        val code = """
            fun Header() {
                Row(Modifier.height(48.dp)) { Wordmark() }
            }
        """.trimIndent()
        val configured = FixedHeightAroundText(TestConfig("textComposables" to listOf("Text", "Wordmark")))

        assertEquals(0, rule.lint(code).size)
        assertEquals(1, configured.lint(code).size)
    }
}
