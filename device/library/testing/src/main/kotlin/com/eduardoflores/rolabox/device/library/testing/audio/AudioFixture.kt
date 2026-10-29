package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.File

/**
 * One file of a fixture set: where it goes, relative to the set's folder, and what's in it. [case]
 * says what the file is there to test.
 *
 * The bytes are generated, never read from a file in the repository, so no audio is committed and
 * none can reach the app.
 */
data class AudioFixture(val path: String, val content: FixtureContent, val case: String) {
    val fileName: String get() = path.substringAfterLast('/')

    fun bytes(): ByteArray = content.bytes()
}

sealed interface FixtureContent {
    fun bytes(): ByteArray

    data class Mp3(val tags: Mp3Tags, val stream: Mp3Stream = Mp3Stream()) : FixtureContent {
        override fun bytes(): ByteArray = when (tags) {
            Mp3Tags.None -> stream.bytes()

            is Mp3Tags.Id3v1 -> stream.bytes() + Id3v1Writer.write(tags.tags)

            is Mp3Tags.Id3v2 ->
                Id3v2Writer.write(tags) + stream.bytes() + (tags.id3v1?.let(Id3v1Writer::write) ?: ByteArray(0))
        }
    }

    /** An M4A, with no `udta` box at all when [tags] is null. */
    data class M4a(val tags: Tags?, val stream: AacStream = AacStream()) : FixtureContent {
        override fun bytes(): ByteArray = M4aFile.bytes(stream, tags)
    }

    /** A file that isn't audio, whatever its extension says. */
    data class Text(val text: String) : FixtureContent {
        override fun bytes(): ByteArray = text.toByteArray(Charsets.UTF_8)
    }

    /** The first [fraction] of another file, as an interrupted copy or download leaves it. */
    data class Truncated(val of: FixtureContent, val fraction: Double) : FixtureContent {
        init {
            require(fraction > 0.0 && fraction < 1.0) { "A truncated file keeps part of the file" }
        }

        override fun bytes(): ByteArray {
            val whole = of.bytes()
            return whole.copyOf((whole.size * fraction).toInt())
        }
    }
}

/** A named set of fixtures, written together into one folder. */
data class AudioFixtureSet(val name: String, val fixtures: List<AudioFixture>) {
    init {
        val duplicates = fixtures.groupBy { it.path }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Set $name has two fixtures at $duplicates" }
    }

    /** Writes every fixture under [folder], creating the folders of its path, and returns the files. */
    fun writeTo(folder: File): List<File> = fixtures.map { fixture ->
        File(folder, fixture.path).apply {
            parentFile.mkdirs()
            writeBytes(fixture.bytes())
        }
    }
}
