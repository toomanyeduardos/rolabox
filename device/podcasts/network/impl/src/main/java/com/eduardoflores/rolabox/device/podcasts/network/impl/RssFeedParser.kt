package com.eduardoflores.rolabox.device.podcasts.network.impl

import android.util.Xml
import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import com.eduardoflores.rolabox.device.podcasts.network.api.Feed
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val CONTENT_NAMESPACE = "http://purl.org/rss/1.0/modules/content/"
private const val RSS = ""

/** The extensions of an enclosure with no audio type that is still audio. */
private val AudioExtensions = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac")

/** A feed that isn't RSS, or ends early: none of it is kept (ADR-031, Feeds). */
internal class UnreadableFeedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A feed as read: [newFeedUrl] is its `itunes:new-feed-url`, when it has one. */
internal data class ParsedFeed(val feed: Feed, val newFeedUrl: String?)

/**
 * Reads an RSS 2.0 feed with the iTunes namespace from [input], streaming, with the platform's pull parser (ADR-031,
 * rule 13). The rest of the feed is ignored, and so are items with no audio enclosure. Throws
 * [UnreadableFeedException] for a body that isn't RSS or ends early, and [IOException] for a connection that fails
 * while it is read.
 */
internal fun parseFeed(input: InputStream): ParsedFeed {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // The encoding is the XML declaration's, or else UTF-8.
        setInput(input, null)
    }
    return try {
        parser.readRss()
    } catch (e: XmlPullParserException) {
        throw UnreadableFeedException("Not well-formed: ${e.message}", e)
    }
}

private fun XmlPullParser.readRss(): ParsedFeed {
    if (nextTag() != XmlPullParser.START_TAG || name != "rss" || namespace != RSS) {
        throw UnreadableFeedException("Not RSS: <$name>")
    }
    var channel: ParsedFeed? = null
    forEachChild {
        if (namespace == RSS && name == "channel" && channel == null) channel = readChannel() else skip()
    }
    return channel ?: throw UnreadableFeedException("RSS with no channel")
}

private class ChannelFields {
    var title: String? = null
    var author: String? = null
    var ownerName: String? = null
    var itunesImage: String? = null
    var rssImage: String? = null
    var newFeedUrl: String? = null
    var kind: String? = null
    val episodes = mutableListOf<FeedEpisode>()
}

private fun XmlPullParser.readChannel(): ParsedFeed {
    val fields = ChannelFields()
    forEachChild {
        when {
            isRss("title") -> fields.title = readText()
            isRss("item") -> readItem()?.let(fields.episodes::add)
            isRss("image") -> fields.rssImage = readImageUrl()
            isItunes("author") -> fields.author = readText()
            isItunes("owner") -> fields.ownerName = readOwnerName()
            isItunes("image") -> fields.itunesImage = attribute("href").also { skip() }
            isItunes("new-feed-url") -> fields.newFeedUrl = readText()
            isItunes("type") -> fields.kind = readText()
            else -> skip()
        }
    }
    val feed = Feed(
        title = fields.title,
        ownerName = fields.author ?: fields.ownerName,
        imageUrl = fields.itunesImage ?: fields.rssImage,
        // Newest first, and the undated last in the feed's order: the sort is stable.
        episodes = fields.episodes.sortedWith(compareBy(nullsLast(reverseOrder())) { it.publishedAt }),
        kind = fields.kind?.let(::showKindOf),
    )
    return ParsedFeed(feed, fields.newFeedUrl)
}

// Apple's spec has two values. Any other, as some hosts write, says nothing: the show is listed newest first.
private fun showKindOf(text: String): ShowKind? = when (text.trim().lowercase(Locale.ROOT)) {
    "serial" -> ShowKind.Serial
    "episodic" -> ShowKind.Episodic
    else -> null
}

private fun XmlPullParser.readOwnerName(): String? {
    var ownerName: String? = null
    forEachChild { if (isItunes("name")) ownerName = readText() else skip() }
    return ownerName
}

private fun XmlPullParser.readImageUrl(): String? {
    var url: String? = null
    forEachChild { if (isRss("url")) url = readText() else skip() }
    return url
}

private class Enclosure(val url: String, val sizeBytes: Long?, val type: String?)

private class ItemFields {
    var guid: String? = null
    var title: String? = null
    var publishedAt: String? = null
    var duration: String? = null
    var description: String? = null
    var summary: String? = null
    var encoded: String? = null
    var image: String? = null
    var enclosure: Enclosure? = null

    /** Reads the child of an item that [parser] is at the start of, or skips it. */
    fun read(parser: XmlPullParser) = if (parser.namespace.isEmpty()) readRss(parser) else readExtension(parser)

    private fun readRss(parser: XmlPullParser) = with(parser) {
        when (name) {
            "guid" -> guid = readText()
            "title" -> title = readText()
            "pubDate" -> publishedAt = readText()
            "description" -> description = readText()
            "enclosure" -> readEnclosure()?.let { if (enclosure == null) enclosure = it }
            else -> skip()
        }
    }

    // The iTunes namespace's, and content:encoded.
    private fun readExtension(parser: XmlPullParser) = with(parser) {
        when {
            isItunes("duration") -> duration = readText()
            isItunes("summary") -> summary = readText()
            isItunes("image") -> image = attribute("href").also { skip() }
            namespace == CONTENT_NAMESPACE && name == "encoded" -> encoded = readText()
            else -> skip()
        }
    }
}

// The item, or `null` when it has no audio enclosure.
private fun XmlPullParser.readItem(): FeedEpisode? {
    val fields = ItemFields()
    forEachChild { fields.read(this) }
    val enclosure = fields.enclosure ?: return null
    return FeedEpisode(
        guid = fields.guid,
        title = fields.title,
        publishedAt = fields.publishedAt?.let(::instantOf),
        length = fields.duration?.let(::durationOf),
        description = listOfNotNull(fields.description, fields.summary, fields.encoded)
            .firstNotNullOfOrNull(::plainTextOf),
        audioUrl = enclosure.url,
        audioSizeBytes = enclosure.sizeBytes,
        audioType = enclosure.type,
        imageUrl = fields.image,
    )
}

// An enclosure that is audio: by its type, or by its URL's extension when the type says nothing.
private fun XmlPullParser.readEnclosure(): Enclosure? {
    val url = attribute("url")
    val type = attribute("type")?.lowercase(Locale.ROOT)
    val size = attribute("length")?.toLongOrNull()?.takeIf { it > 0 }
    skip()
    val isAudio = when {
        type == null || type == "application/octet-stream" -> url?.let(::hasAudioExtension) == true
        else -> type.startsWith("audio/")
    }
    return if (url != null && isAudio) Enclosure(url, size, type) else null
}

private fun hasAudioExtension(url: String): Boolean =
    url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase(Locale.ROOT) in AudioExtensions
