package com.eduardoflores.rolabox.device.library.local.impl

import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.impl.tags.ArrayByteSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.ByteSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.ChannelByteSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.parseTags
import com.eduardoflores.rolabox.device.library.testing.audio.AacStream
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixture
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixtures
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Tags
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber") // Byte layouts: the numbers are the format.
class TagParserTest {
    private val latin1 = Charsets.ISO_8859_1

    private fun parse(bytes: ByteArray): FileTags? = parseTags(ArrayByteSource(bytes))

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun synchsafe(value: Int) = bytes(
        value shr 21 and 0x7F,
        value shr 14 and 0x7F,
        value shr 7 and 0x7F,
        value and 0x7F,
    )

    private fun plain(value: Int) = bytes(value ushr 24, value shr 16 and 0xFF, value shr 8 and 0xFF, value and 0xFF)

    private fun id3(major: Int, body: ByteArray, flags: Int = 0) =
        "ID3".toByteArray(latin1) + bytes(major, 0, flags) + synchsafe(body.size) + body

    private fun v22Frame(id: String, payload: ByteArray) =
        id.toByteArray(latin1) + bytes(payload.size shr 16, payload.size shr 8 and 0xFF, payload.size and 0xFF) +
            payload

    private fun v23Frame(id: String, payload: ByteArray, flags: Int = 0) =
        id.toByteArray(latin1) + plain(payload.size) + bytes(0, flags) + payload

    private fun v24Frame(id: String, payload: ByteArray, flags: Int = 0) =
        id.toByteArray(latin1) + synchsafe(payload.size) + bytes(0, flags) + payload

    private fun latin1Text(value: String) = bytes(0) + value.toByteArray(latin1)

    private fun fixture(set: List<AudioFixture>, name: String): ByteArray = set.first { it.path.endsWith(name) }.bytes()

    private val broken get() = AudioFixtures.broken.fixtures

    // ID3v1

    @Test
    fun id3v11_hasItsTrack() {
        val tags = parse(fixture(broken, "05 Static Bloom.mp3"))!!

        assertEquals("Static Bloom", tags.title)
        assertEquals("Velvet Static", tags.artist)
        assertEquals("Old Rips", tags.album)
        assertEquals(2003, tags.year)
        assertEquals(5, tags.trackNumber)
        assertEquals(false, tags.hasEmbeddedPicture)
    }

    @Test
    fun id3v10_hasNoTrack() {
        assertNull(parse(fixture(broken, "06 Night Bus.mp3"))!!.trackNumber)
    }

    @Test
    fun id3v1_cutsATitleAtThirtyCharacters() {
        assertEquals("A Title Much Longer Than Thirt", parse(fixture(broken, "07 Long Title.mp3"))!!.title)
    }

    // ID3v2 versions

    @Test
    fun id3v22_isRead() {
        val tag = id3(
            2,
            v22Frame("TT2", latin1Text("Title")) + v22Frame("TP1", latin1Text("Artist")) +
                v22Frame("TP2", latin1Text("Album Artist")) + v22Frame("TAL", latin1Text("Album")) +
                v22Frame("TRK", latin1Text("3/9")) + v22Frame("TPA", latin1Text("2/2")) +
                v22Frame("TYE", latin1Text("1999")) + v22Frame("TCP", latin1Text("1")) +
                v22Frame("PIC", ByteArray(50)),
        )

        assertEquals(
            FileTags("Title", "Artist", "Album Artist", "Album", 3, 2, 1999, true, null, true),
            parse(tag),
        )
    }

    @Test
    fun id3v23_isRead() {
        val tags = parse(fixture(broken, "01 - ID3v2.3.mp3"))!!

        assertEquals("ID3v2.3", tags.title)
        assertEquals("Tag Lab", tags.artist)
        assertEquals("Tag Lab", tags.albumArtist)
        assertEquals("Versions", tags.album)
        assertEquals(1, tags.trackNumber)
        assertEquals(2024, tags.year)
    }

    @Test
    fun id3v24_isRead() {
        val tags = parse(fixture(broken, "02 - ID3v2.4.mp3"))!!

        assertEquals("ID3v2.4", tags.title)
        assertEquals(2, tags.trackNumber)
        assertEquals(2024, tags.year)
    }

    @Test
    fun id3v2_winsOverId3v1_whichFillsWhatItLacks() {
        val tags = parse(fixture(broken, "03 - ID3v2.4 and ID3v1.mp3"))!!
        assertEquals("ID3v2.4 and ID3v1", tags.title)
        assertEquals(3, tags.trackNumber)

        val v1 = "TAG".toByteArray(latin1) + "From v1".padEnd(30, '\u0000').toByteArray(latin1) +
            "Artist from v1".padEnd(30, '\u0000').toByteArray(latin1) + ByteArray(65)
        val both = parse(id3(3, v23Frame("TIT2", latin1Text("From v2"))) + ByteArray(100) + v1)!!
        assertEquals("From v2", both.title)
        assertEquals("Artist from v1", both.artist)
    }

    // ID3v2 structure

    @Test
    fun v23TagLevelUnsynchronisation_isUndone() {
        val frame = v23Frame("TIT2", bytes(3) + bytes(0xFF, 0xFE) + "x".toByteArray(latin1))
        val stuffed = ByteArrayOutputStream().apply {
            frame.forEach { byte ->
                write(byte.toInt())
                if (byte == 0xFF.toByte()) write(0)
            }
        }.toByteArray()
        val withTitle = id3(3, stuffed + v23Frame("TPE1", latin1Text("Artist")), flags = 0x80)

        assertEquals("Artist", parse(withTitle)!!.artist)
    }

    @Test
    fun v24FrameUnsynchronisation_isUndone_withItsDataLength() {
        val text = bytes(1, 0xFF, 0x00, 0xFE, 'A'.code, 0)
        val payload = synchsafe(4) + text
        val tag = id3(4, v24Frame("TIT2", payload, flags = 0x03))

        assertEquals("A", parse(tag)!!.title)
    }

    @Test
    fun v23ExtendedHeader_isSkipped() {
        val extended = plain(6) + ByteArray(6)
        val tag = id3(3, extended + v23Frame("TIT2", latin1Text("Title")), flags = 0x40)

        assertEquals("Title", parse(tag)!!.title)
    }

    @Test
    fun v24ExtendedHeader_isSkipped() {
        val extended = synchsafe(6) + bytes(1, 0)
        val tag = id3(4, extended + v24Frame("TIT2", latin1Text("Title")), flags = 0x40)

        assertEquals("Title", parse(tag)!!.title)
    }

    @Test
    fun aCompressedFrame_isSkipped_andTheRestRead() {
        val tag = id3(
            3,
            v23Frame("TIT2", latin1Text("Hidden"), flags = 0x80) + v23Frame("TPE1", latin1Text("Artist")),
        )

        assertNull(parse(tag)!!.title)
        assertEquals("Artist", parse(tag)!!.artist)
    }

    @Test
    fun plainFrameSizesInV24_asITunesWroteThem_areRead() {
        val picture = "APIC".toByteArray(latin1) + plain(300) + bytes(0, 0) + ByteArray(300) { 1 }
        val tag = id3(4, picture + v24Frame("TIT2", latin1Text("After the picture")))

        val tags = parse(tag)!!
        assertEquals(true, tags.hasEmbeddedPicture)
        assertEquals("After the picture", tags.title)
    }

    @Test
    fun aPictures_bytesAreNotRead() {
        val picture = ByteArray(1_000_000)
        val tag = id3(3, v23Frame("APIC", picture) + v23Frame("TIT2", latin1Text("Title")))
        var read = 0L
        val counting = object : ByteSource {
            val source = ArrayByteSource(tag)
            override val size = source.size

            override fun read(position: Long, length: Int) = source.read(position, length).also { read += it.size }
        }

        val tags = parseTags(counting)!!

        assertEquals("Title", tags.title)
        assertEquals(true, tags.hasEmbeddedPicture)
        assertTrue("Read $read bytes", read < 1_000)
    }

    // ADR-024, Reading the tags: an iTunes Store purchase's audio is FairPlay's `drms`, which Android can't play.
    @Test
    fun anMp4WithFairPlayAudio_isProtected_andItsTagsAreStillRead() {
        val protected = FixtureContent.M4a(Tags(title = "Bought"), AacStream(fairPlay = true)).bytes()

        val tags = parse(protected)!!
        assertTrue(tags.isProtected)
        assertEquals("Bought", tags.title)
    }

    @Test
    fun anMp4WithPlainAac_andAnMp3_areNotProtected() {
        assertFalse(parse(FixtureContent.M4a(Tags(title = "Plain"), AacStream()).bytes())!!.isProtected)
        assertFalse(parse(id3(3, v23Frame("TIT2", latin1Text("Title"))))!!.isProtected)
    }

    @Test
    fun theFrameLength_comesFromTLEN() {
        assertEquals(1234L, parse(id3(3, v23Frame("TLEN", latin1Text("1234"))))!!.durationMillis)
    }

    @Test
    fun year_isTheStartOfADate() {
        assertEquals(2019, parse(id3(4, v24Frame("TDRC", latin1Text("2019-05-01"))))!!.year)
    }

    // Encodings

    @Test
    fun latin1_isRead() {
        val tags = parse(fixture(broken, "01 - Café Brûlée.mp3"))!!

        assertEquals("Café Brûlée", tags.title)
        assertEquals("Søren Ångström", tags.artist)
    }

    @Test
    fun cp1252InALatin1Frame_isReadAsCp1252() {
        val tags = parse(fixture(broken, "02 - Don't Look Back.mp3"))!!

        assertEquals("Don’t Look Back…", tags.title)
        assertEquals("Zoë € Co", tags.artist)
    }

    @Test
    fun utf8InALatin1Frame_isReadAsUtf8() {
        val tag = id3(3, v23Frame("TIT2", bytes(0) + "Café".toByteArray(Charsets.UTF_8)))

        assertEquals("Café", parse(tag)!!.title)
    }

    @Test
    fun utf16WithABom_isRead() {
        val tags = parse(fixture(broken, "03 - Tokyo Nights.mp3"))!!

        assertEquals("東京の夜", tags.title)
        assertEquals("Ниночка", tags.artist)
    }

    @Test
    fun utf16BigEndianWithABom_isRead() {
        val tag = id3(3, v23Frame("TIT2", bytes(1, 0xFE, 0xFF) + "Hi".toByteArray(Charsets.UTF_16BE)))

        assertEquals("Hi", parse(tag)!!.title)
    }

    @Test
    fun utf16WithoutABom_isReadInEitherOrder() {
        val little = id3(3, v23Frame("TIT2", bytes(1) + "Hello".toByteArray(Charsets.UTF_16LE)))
        val big = id3(4, v24Frame("TIT2", bytes(1) + "Hello".toByteArray(Charsets.UTF_16BE)))

        assertEquals("Hello", parse(little)!!.title)
        assertEquals("Hello", parse(big)!!.title)
    }

    @Test
    fun utf16BigEndianDeclared_isRead() {
        val tag = id3(4, v24Frame("TIT2", bytes(2) + "Hello".toByteArray(Charsets.UTF_16BE)))

        assertEquals("Hello", parse(tag)!!.title)
    }

    @Test
    fun utf8_isRead() {
        val tags = parse(fixture(broken, "04 - Greek.mp3"))!!

        assertEquals("Καλημέρα", tags.title)
        assertEquals("Ελένη", tags.artist)
    }

    @Test
    fun strayWhitespaceAndNulls_areTrimmed() {
        val tag = id3(3, v23Frame("TIT2", latin1Text("  Title \u0000junk")))

        assertEquals("Title", parse(tag)!!.title)
    }

    @Test
    fun anEmptyValue_isNoValue() {
        assertNull(parse(id3(3, v23Frame("TIT2", latin1Text("  \u0000"))))!!.title)
    }

    // Numbers

    @Test
    fun aTrackThatIsntANumber_isNone() {
        assertNull(parse(fixture(broken, "Smoke.mp3"))!!.trackNumber)
    }

    @Test
    fun trackZero_isNone() {
        assertNull(parse(fixture(broken, "00 - Ash.mp3"))!!.trackNumber)
    }

    @Test
    fun aTrackPastTheTotal_isStillItsNumber() {
        assertEquals(15, parse(fixture(broken, "15 - Spark.mp3"))!!.trackNumber)
    }

    // MP4

    @Test
    fun mp4_itemsAndDuration_areRead() {
        val tags = parse(fixture(broken, "02 - ${"Low Tides".let { "The $it" }} - High Water.m4a"))!!

        assertEquals("High Water", tags.title)
        assertEquals("The Low Tides", tags.artist)
        assertEquals("Various Artists", tags.albumArtist)
        assertEquals("Summer Mixtape", tags.album)
        assertEquals(2, tags.trackNumber)
        assertEquals(2023, tags.year)
        assertEquals(true, tags.compilation)
        assertEquals(true, tags.hasEmbeddedPicture)
        assertNotNull(tags.durationMillis)
        assertTrue(tags.durationMillis!! > 0)
    }

    @Test
    fun mp4_withNoTags_isEmpty_butHasItsDuration() {
        val tags = parse(fixture(broken, "Recording 3.m4a"))!!

        assertNull(tags.title)
        assertEquals(false, tags.hasEmbeddedPicture)
        assertNotNull(tags.durationMillis)
    }

    @Test
    fun mp4_aTrackWrittenAsZero_isNone() {
        assertNull(parse(fixture(broken, "03 - Kindling.m4a"))!!.trackNumber)
    }

    // Broken and fake files

    @Test
    fun aTextFile_hasNoTags_andFailsNothing() {
        assertEquals(FileTags(hasEmbeddedPicture = false), parse(fixture(broken, "not really audio.mp3")))
    }

    @Test
    fun aTruncatedMp3_keepsItsWholeTag() {
        assertEquals("Interrupted Download", parse(fixture(broken, "Interrupted Download.mp3"))!!.title)
    }

    @Test
    fun aTruncatedM4a_cutInsideItsIndex_keepsWhatIsLeft_andFailsNothing() {
        assertNotNull(parse(fixture(broken, "Interrupted Download.m4a")))
    }

    @Test
    fun anM4aCutBeforeItsIndex_isMalformed() {
        val ftyp = plain(16) + "ftypM4A ".toByteArray(latin1) + ByteArray(4)
        val mdatCutShort = plain(1_000_000) + "mdat".toByteArray(latin1) + ByteArray(100)

        assertNull(parse(ftyp + mdatCutShort))
    }

    @Test
    fun anId3HeaderOfNonsense_isMalformed() {
        assertNull(parse("ID3".toByteArray(latin1) + ByteArray(5) { 0xFF.toByte() } + ByteArray(20)))
    }

    @Test
    fun aTagCutShort_keepsTheFramesThatFit() {
        val whole = id3(3, v23Frame("TIT2", latin1Text("Title")) + v23Frame("TPE1", latin1Text("Artist")))

        val tags = parse(whole.copyOf(whole.size - 3))!!

        assertEquals("Title", tags.title)
    }

    @Test
    fun aFrameSizePastTheEnd_failsNothing() {
        val tag = id3(3, "TIT2".toByteArray(latin1) + plain(Int.MAX_VALUE) + bytes(0, 0) + latin1Text("x"))

        assertNull(parse(tag)!!.artist)
    }

    @Test
    fun formatsItDoesntRead_sayNothing_evenAboutAPicture() {
        assertEquals(FileTags(), parse("fLaC".toByteArray(latin1) + ByteArray(40)))
        assertEquals(FileTags(), parse("OggS".toByteArray(latin1) + ByteArray(40)))
        assertEquals(FileTags(), parse("RIFF".toByteArray(latin1) + ByteArray(40)))
    }

    @Test
    fun anEmptyFile_failsNothing() {
        assertFalse(parse(ByteArray(0))!!.hasEmbeddedPicture!!)
    }

    @Test
    fun everyFixture_parsesWithoutThrowing() {
        (AudioFixtures.clean.fixtures + broken + AudioFixtures.formats.fixtures).forEach { parse(it.bytes()) }
    }

    @Test
    fun aRealFile_readsThroughItsChannel() {
        val file = File.createTempFile("tags", ".mp3")
        try {
            file.writeBytes(fixture(broken, "01 - ID3v2.3.mp3"))
            val tags = RandomAccessFile(file, "r").use { parseTags(ChannelByteSource(it.channel)) }
            assertEquals("ID3v2.3", tags!!.title)
        } finally {
            file.delete()
        }
    }

    // Genres (ADR-024, Genre)

    private fun genresOf(name: String) = parse(fixture(broken, name))!!.genres

    @Test
    fun anId3v1Genre_isItsNameInTheList() {
        assertEquals(listOf("Rock"), genresOf("05 Static Bloom.mp3"))
    }

    @Test
    fun anId3v1GenreOf255_isNoGenre() {
        assertEquals(emptyList<String>(), parse(id3v1WithGenre(255))!!.genres)
    }

    @Test
    fun anId3v2ReferenceInParentheses_isTheNameInId3v1sList() {
        assertEquals(listOf("Rock"), genresOf("01 - Parenthesised.mp3"))
    }

    @Test
    fun anId3v2BareNumber_isTheNameInId3v1sList() {
        assertEquals(listOf("Rock"), genresOf("02 - Number.mp3"))
    }

    @Test
    fun id3v24sNullSeparatedValues_areSeveralGenres() {
        assertEquals(listOf("Rock", "Pop", "Jazz"), genresOf("03 - Several.mp3"))
    }

    @Test
    fun freeText_isOneGenre() {
        assertEquals(listOf("Dream Pop"), genresOf("04 - Text.mp3"))
    }

    @Test
    fun id3v2_winsOverId3v1sGenre() {
        val tags = parse(id3(3, v23Frame("TCON", latin1Text("Shoegaze"))) + id3v1WithGenre(17))!!

        assertEquals(listOf("Shoegaze"), tags.genres)
    }

    @Test
    fun id3v1sGenre_fillsAnId3v2TagWithout() {
        val tags = parse(id3(3, v23Frame("TIT2", latin1Text("Title"))) + id3v1WithGenre(8))!!

        assertEquals(listOf("Jazz"), tags.genres)
    }

    @Test
    fun anId3v22Genre_isRead() {
        assertEquals(listOf("Rock"), parse(id3(2, v22Frame("TCO", latin1Text("(17)"))))!!.genres)
    }

    @Test
    fun anM4aGnre_isId3v1sListCountedFromOne() {
        assertEquals(listOf("Pop"), genresOf("05 - Numbered M4A.m4a"))
    }

    @Test
    fun anM4aGenAtom_isItsText() {
        val bytes = AudioFixture("x.m4a", FixtureContent.M4a(Tags(title = "x", genre = "Dream Pop")), "").bytes()

        assertEquals(listOf("Dream Pop"), parse(bytes)!!.genres)
    }

    @Test
    fun anM4aGenAtomWithSeveralDataBoxes_isSeveralGenres() {
        val ilst = atom("ilst", atom("\u00A9gen", dataBox("Rock") + dataBox("Jazz")))
        val meta = atom("meta", bytes(0, 0, 0, 0) + ilst)
        val file = atom("ftyp", "M4A ".toByteArray(latin1)) + atom("moov", atom("udta", meta))

        assertEquals(listOf("Rock", "Jazz"), parse(file)!!.genres)
    }

    @Test
    fun id3v23ReferencesWithARefinement_areEachOnce() {
        assertEquals(listOf("Rock", "Pop"), parse(id3(3, v23Frame("TCON", latin1Text("(17)(13)Rock"))))!!.genres)
    }

    @Test
    fun remixAndCover_areTheirNames() {
        assertEquals(listOf("Remix", "Cover"), parse(id3(3, v23Frame("TCON", latin1Text("(RX)(CR)"))))!!.genres)
    }

    @Test
    fun aDoubleParenthesis_escapesANameStartingWithOne() {
        assertEquals(listOf("(Live) Sets"), parse(id3(3, v23Frame("TCON", latin1Text("((Live) Sets"))))!!.genres)
    }

    @Test
    fun aParenthesisThatIsntAReference_isPartOfTheName() {
        assertEquals(listOf("(Live) Rock"), parse(id3(3, v23Frame("TCON", latin1Text("(Live) Rock"))))!!.genres)
    }

    @Test
    fun aNumberPastTheList_isNoGenre() {
        assertEquals(emptyList<String>(), parse(id3(3, v23Frame("TCON", latin1Text("(255)"))))!!.genres)
        assertEquals(emptyList<String>(), parse(id3(4, v24Frame("TCON", latin1Text("300"))))!!.genres)
    }

    @Test
    fun punctuationInFreeText_doesntSplitIt() {
        assertEquals(listOf("Rock; Pop"), parse(id3(3, v23Frame("TCON", latin1Text("Rock; Pop"))))!!.genres)
        assertEquals(listOf("R&B/Soul"), parse(id3(3, v23Frame("TCON", latin1Text("R&B/Soul"))))!!.genres)
    }

    @Test
    fun junkGenres_areKeptAsWritten() {
        assertEquals(listOf("Blues"), parse(id3v1WithGenre(0))!!.genres)
        assertEquals(listOf("Other"), parse(id3(3, v23Frame("TCON", latin1Text("Other"))))!!.genres)
    }

    @Test
    fun theSameGenreTwice_isOnce() {
        val tags = parse(id3(4, v24Frame("TCON", latin1Text("Rock\u0000rock\u0000(17)"))))!!

        assertEquals(listOf("Rock"), tags.genres)
    }

    private fun id3v1WithGenre(number: Int) = ByteArray(128).also {
        "TAG".toByteArray(latin1).copyInto(it)
        it[127] = number.toByte()
    }

    private fun atom(type: String, payload: ByteArray) = plain(8 + payload.size) + type.toByteArray(latin1) + payload

    // A data box of UTF-8 text: its type, then a locale of zero.
    private fun dataBox(text: String) = atom("data", plain(1) + plain(0) + text.toByteArray(Charsets.UTF_8))
}
