package com.eduardoflores.rolabox.device.podcasts.impl

import java.text.Normalizer
import java.util.Locale

private val MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
private const val SORT_PREFIX = "the "

/**
 * What a show's title or an owner's name sorts by: ignoring case and accents, so "Écoute" sorts with the E's, and
 * without a leading "The", as the library's lists sort. The database orders by it, as it is given.
 */
internal fun sortKeyOf(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(MARKS, "")
    .trim()
    .replace(WHITESPACE, " ")
    .lowercase(Locale.ROOT)
    .removePrefix(SORT_PREFIX)
