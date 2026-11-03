package com.eduardoflores.rolabox.device.library.network.impl

/** A response MusicBrainz gave, recorded in `src/test/resources/musicbrainz`, with what the match doesn't read cut. */
internal fun recorded(name: String): String =
    checkNotNull(Recorded::class.java.getResource("/musicbrainz/$name")) { "No recorded response $name" }.readText()

private object Recorded
