package com.eduardoflores.rolabox.device.podcasts.network.impl

import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The platform's pull parser, on the JVM with Robolectric, against recorded and hand-made feeds (ADR-031, Feeds). */
@RunWith(RobolectricTestRunner::class)
class RssFeedParserTest {
    private fun parse(path: String) = recordedStream(path).use(::parseFeed)

    @Test
    fun aRecordedFeed_givesItsShowAndItsEpisodes_newestFirst() {
        val parsed = parse("feeds/emperors-of-rome.xml")

        with(parsed.feed) {
            assertEquals("Emperors of Rome", title)
            assertEquals("Nightlight Productions", ownerName)
            assertEquals("https://www.mattsmithpodcast.com/emperors/eor-logo.jpg", imageUrl)
            assertEquals(
                listOf("Anthology of Interest IV", "Viriathus", "Paetus, It Does Not Hurt"),
                episodes.map {
                    it.title
                },
            )
        }
        assertNull(parsed.newFeedUrl)
        val first = parsed.feed.episodes.first()
        assertEquals(
            FeedEpisode(
                guid = "2D5ED9F0-BA7A-4424-82AA-2D305404EA75",
                title = "Anthology of Interest IV",
                publishedAt = Instant.parse("2025-12-09T05:27:41Z"),
                length = 3146.seconds,
                description = first.description,
                audioUrl = "https://www.mattsmithpodcast.com/emperors/251209-aoiiv.mp3",
                audioSizeBytes = 50_696_538,
                audioType = "audio/mpeg",
                imageUrl = null,
            ),
            first,
        )
    }

    @Test
    fun aDescription_isPlainText_withItsParagraphs() {
        val description = parse("feeds/emperors-of-rome.xml").feed.episodes.first().description!!

        assertTrue(
            description,
            description.startsWith("Rhiannon Evans, Caillan Davenport, Matt Smith and special guest"),
        )
        assertTrue(description, "\n\nEpisode CCL(250)\n\n" in description)
        assertTrue(description, "\n- The unknown Queens of Rome\n" in description)
        assertFalse(description, '<' in description)
    }

    @Test
    fun theOwner_isItunesOwnersName_whenThereIsNoAuthor_andTheImage_isTheRssImage() {
        val parsed = parse("feeds/edge-cases.xml")

        assertEquals("The Owner", parsed.feed.ownerName)
        assertEquals("https://example.com/channel.jpg", parsed.feed.imageUrl)
        assertEquals("https://example.com/new-feed.xml", parsed.newFeedUrl)
    }

    @Test
    fun itemsWithNoAudio_areSkipped_andTheRestAreNewestFirst_theUndatedLast() {
        val episodes = parse("feeds/edge-cases.xml").feed.episodes

        assertEquals(listOf("Newest", "No GUID", "Oldest", "Undated"), episodes.map { it.title })
    }

    @Test
    fun anItem_isReadAsFeedsWriteIt() {
        val episodes = parse("feeds/edge-cases.xml").feed.episodes
        val newest = episodes[0]
        val noGuid = episodes[1]
        val oldest = episodes[2]
        val undated = episodes[3]

        // A GUID's attributes don't matter, an empty summary gives way to content:encoded, and an item has its image.
        assertEquals("g-new", newest.guid)
        assertEquals(Instant.parse("2024-02-01T08:00:00Z"), newest.publishedAt)
        assertEquals("First paragraph.\n\nSecond, with a link .", newest.description)
        assertEquals("https://example.com/newest.jpg", newest.imageUrl)
        assertNull(newest.audioSizeBytes)
        // No GUID: it is known by its enclosure, audio by its extension, and its date has a wrong weekday and a zone.
        assertNull(noGuid.guid)
        assertEquals("https://example.com/no-guid.m4a?source=feed", noGuid.audioUrl)
        assertNull(noGuid.audioType)
        assertNull(noGuid.audioSizeBytes)
        assertEquals(Instant.parse("2024-01-03T15:00:00Z"), noGuid.publishedAt)
        assertEquals(1.hours + 2.minutes + 3.seconds, noGuid.length)
        assertEquals(59.minutes + 30.seconds, oldest.length)
        assertEquals("Plain & simple", oldest.description)
        assertNull(undated.publishedAt)
        assertNull(undated.length)
        assertNull(undated.description)
    }

    // ADR-043: the channel's itunes:type is the show's kind. A feed with none, or a value the spec hasn't, has none.
    @Test
    fun itunesType_isTheShowsKind() {
        assertEquals(ShowKind.Episodic, parse("feeds/emperors-of-rome.xml").feed.kind)
        assertNull(parse("feeds/edge-cases.xml").feed.kind)
        assertEquals(ShowKind.Serial, kindOf("serial"))
        assertEquals(ShowKind.Serial, kindOf(" Serial "))
        assertEquals(ShowKind.Episodic, kindOf("episodic"))
        assertNull(kindOf("seasonal"))
        assertNull(kindOf(""))
    }

    // Only the channel's tag is the show's: one inside an item isn't.
    @Test
    fun anItemsType_isntTheShowsKind() {
        val feed = "<rss xmlns:itunes=\"$ITUNES\"><channel><title>T</title>" +
            "<item><itunes:type>serial</itunes:type>" +
            "<enclosure url=\"https://e.com/1.mp3\" type=\"audio/mpeg\"/></item></channel></rss>"

        assertNull(parseFeed(ByteArrayInputStream(feed.toByteArray())).feed.kind)
    }

    private fun kindOf(type: String): ShowKind? {
        val feed = "<rss xmlns:itunes=\"$ITUNES\"><channel><title>T</title><itunes:type>$type</itunes:type>" +
            "<item><enclosure url=\"https://e.com/1.mp3\" type=\"audio/mpeg\"/></item></channel></rss>"
        return parseFeed(ByteArrayInputStream(feed.toByteArray())).feed.kind
    }

    @Test
    fun aFeedThatEndsEarly_isUnreadable() {
        assertThrows(UnreadableFeedException::class.java) { parse("feeds/ends-early.xml") }
    }

    @Test
    fun atom_isntRead() {
        assertThrows(UnreadableFeedException::class.java) { parse("feeds/atom.xml") }
    }

    @Test
    fun aBodyThatIsntXml_isUnreadable() {
        listOf("<html><body><p>Not found<br></body></html>", "{\"json\": true}", "").forEach { body ->
            assertThrows(body, UnreadableFeedException::class.java) {
                parseFeed(ByteArrayInputStream(body.toByteArray()))
            }
        }
    }

    @Test
    fun rssWithNoChannel_isUnreadable() {
        assertThrows(UnreadableFeedException::class.java) {
            parseFeed(ByteArrayInputStream("<rss version=\"2.0\"></rss>".toByteArray()))
        }
    }

    private companion object {
        const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    }
}
