package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.util.Locale

/** The directory's store when the phone's locale names no country. */
private const val FALLBACK_COUNTRY = "US"

/** A two-letter country code: ISO 3166's. */
private const val COUNTRY_CODE_LENGTH = 2

/** The country whose directory is searched: the phone's (ADR-031, The directory). */
internal fun interface DeviceCountry {
    fun code(): String

    companion object {
        /** The region of the phone's locale, or US when it has none, or one that isn't a country (`419`). */
        val FromLocale = DeviceCountry {
            Locale.getDefault().country.takeIf { it.length == COUNTRY_CODE_LENGTH && it.all(Char::isLetter) }
                ?: FALLBACK_COUNTRY
        }
    }
}
