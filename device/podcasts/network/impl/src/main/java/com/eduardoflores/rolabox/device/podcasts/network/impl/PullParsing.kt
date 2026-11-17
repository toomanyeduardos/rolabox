package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/** The iTunes namespace, as feeds write it, which is sometimes `https` or in capitals. */
private const val ITUNES_NAMESPACE = "www.itunes.com/dtds/podcast-1.0.dtd"

/** Whether the parser is at RSS's own [tag], which has no namespace. */
internal fun XmlPullParser.isRss(tag: String) = namespace.isEmpty() && name == tag

/** Whether the parser is at the iTunes namespace's [tag]. */
internal fun XmlPullParser.isItunes(tag: String) =
    name == tag && namespace.lowercase(Locale.ROOT).substringAfter("://") == ITUNES_NAMESPACE

/** The attribute, trimmed, or `null` when it is missing or blank. */
internal fun XmlPullParser.attribute(name: String): String? =
    getAttributeValue(null, name)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Calls [read] at the start tag of each child of the current element, which must leave the parser at that child's end
 * tag, and returns at the current element's end tag. A document that ends first ends early.
 */
internal inline fun XmlPullParser.forEachChild(read: XmlPullParser.() -> Unit) {
    val depth = depth
    while (true) {
        when (next()) {
            XmlPullParser.START_TAG -> read()
            XmlPullParser.END_TAG -> if (this.depth == depth) return
            XmlPullParser.END_DOCUMENT -> throw UnreadableFeedException("The feed ends early, in <$name>")
        }
    }
}

/** The text of the current element, CDATA included and child elements' left out, trimmed, or `null` when blank. */
internal fun XmlPullParser.readText(): String? {
    val depth = depth
    val text = StringBuilder()
    while (true) {
        when (next()) {
            // next() reports CDATA and entities as text.
            XmlPullParser.TEXT -> text.append(this.text)

            XmlPullParser.START_TAG -> skip()

            XmlPullParser.END_TAG -> if (this.depth == depth) break

            XmlPullParser.END_DOCUMENT -> throw UnreadableFeedException("The feed ends early, in <$name>")
        }
    }
    return text.toString().trim().takeIf { it.isNotEmpty() }
}

/** Moves past the current element, and all it holds. */
internal fun XmlPullParser.skip() {
    forEachChild { skip() }
}
