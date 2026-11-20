package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextUrlOutsidePodcastsTest {
    private val rule = CleartextUrlOutsidePodcasts(Config.empty)

    @Test
    fun `flags plain, templated, raw and upper-case http URLs`() {
        val findings = rule.lint(
            """
            class Covers(private val id: String) {
                val search = "http://musicbrainz.org/ws/2/release-group"
                val cover = "http://coverartarchive.org/release-group/${'$'}id/front"
                val raw = ${"\"\"\""}http://example.com/a${"\"\"\""}
                val shouted = "HTTP://EXAMPLE.COM"
            }
            """.trimIndent(),
        )

        assertEquals(listOf(2, 3, 4, 5), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-034 rule 3:") })
    }

    @Test
    fun `passes https URLs, schemes alone, and http inside other text`() {
        val findings = rule.lint(
            """
            class Covers(private val id: String) {
                val search = "https://musicbrainz.org/ws/2/release-group"
                val scheme = "http"
                val prefix = "http:"
                val templated = "${'$'}id http://example.com"
                val sentence = "See http://example.com"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
