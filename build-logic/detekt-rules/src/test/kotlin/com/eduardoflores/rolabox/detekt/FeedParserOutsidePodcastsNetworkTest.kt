package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserOutsidePodcastsNetworkTest {
    private val rule = FeedParserOutsidePodcastsNetwork(Config.empty)

    @Test
    fun `flags an import of the pull parser, of its factory, and a qualified use`() {
        val findings = rule.lint(
            """
            import android.util.Xml
            import org.xmlpull.v1.XmlPullParser
            import org.xmlpull.v1.*

            class FeedReader {
                val parser = android.util.Xml.newPullParser()
                val start = org.xmlpull.v1.XmlPullParser.START_TAG
            }
            """.trimIndent(),
        )

        assertEquals(listOf(1, 2, 3, 6, 7), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-031 rule 13:") })
    }

    @Test
    fun `passes code that reads podcasts through the api, and other android util classes`() {
        val findings = rule.lint(
            """
            import android.util.Log
            import android.util.Xml2
            import com.eduardoflores.rolabox.device.podcasts.api.PodcastRepository

            class Shows(private val podcasts: PodcastRepository) {
                fun shows() = podcasts.observeShows()
                val parserLike = "org.xmlpull.v1.XmlPullParser"
                val logged = Log.d("Shows", "android.util.Xml")
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
