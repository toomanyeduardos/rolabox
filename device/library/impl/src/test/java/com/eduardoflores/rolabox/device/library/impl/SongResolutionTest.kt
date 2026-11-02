package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongResolutionTest {
    private val untagged = audioFile(
        id = 1,
        path = "Music/Velvet Static/Old Rips/",
        name = "03 - Neon Rain.mp3",
        title = null,
        artist = null,
        album = null,
    )

    @Test
    fun mediaStore_comesBeforeTheFolderAndTheName() {
        val metadata = untagged.copy(title = "Tagged", artist = "Tagged Artist", album = "Tagged Album").toMetadata()

        assertEquals(Sourced("Tagged", FieldSource.MediaStore), metadata.title)
        assertEquals(Sourced("Tagged Artist", FieldSource.MediaStore), metadata.artist)
        assertEquals(Sourced("Tagged Album", FieldSource.MediaStore), metadata.album)
    }

    @Test
    fun theFolder_comesBeforeTheName() {
        val file = untagged.copy(file = untagged.file.copy(fileName = "Someone Else - Neon Rain.mp3"))

        assertEquals(Sourced("Velvet Static", FieldSource.Folder), file.toMetadata().artist)
        assertEquals(Sourced("Old Rips", FieldSource.Folder), file.toMetadata().album)
    }

    @Test
    fun theName_givesTheTitleAndTrack_whenNothingStrongerDoes() {
        val metadata = untagged.toMetadata()

        assertEquals(Sourced("Neon Rain", FieldSource.FileName), metadata.title)
        assertEquals(Sourced(3, FieldSource.FileName), metadata.trackNumber)
    }

    @Test
    fun theName_givesTheArtist_whenTheFolderSaysNothing() {
        val file = audioFile(1, path = "Download/", name = "Velvet Static - Neon Rain.mp3", title = null, artist = null)

        assertEquals(Sourced("Velvet Static", FieldSource.FileName), file.toMetadata().artist)
        assertEquals(Sourced("Neon Rain", FieldSource.FileName), file.toMetadata().title)
    }

    @Test
    fun nothing_isUnknown_withNoValue() {
        val file = audioFile(1, path = "Download/", name = "track1.mp3", title = null, artist = null, album = null)

        val metadata = file.toMetadata()

        assertEquals(Sourced<String>(null, FieldSource.Unknown), metadata.artist)
        assertEquals(Sourced<String>(null, FieldSource.Unknown), metadata.album)
        assertEquals(Sourced<Int>(null, FieldSource.Unknown), metadata.trackNumber)
        assertEquals(Sourced("track1", FieldSource.FileName), metadata.title)
    }

    @Test
    fun mediaStoresTitle_isNothing_whenItIsOnlyTheFileName() {
        val name = "Velvet Static - Neon Rain"
        val file = audioFile(1, path = "Download/", name = "$name.mp3", title = name)

        assertEquals(Sourced("Neon Rain", FieldSource.FileName), file.toMetadata().title)
    }

    @Test
    fun mediaStoresAlbum_isNothing_whenItIsOnlyTheFolder() {
        val file = audioFile(1, path = "Music/Dump/", name = "track1.mp3", album = "Dump")

        assertEquals(Sourced<String>(null, FieldSource.Unknown), file.toMetadata().album)
    }

    @Test
    fun aTag_replacesEveryOtherSource() {
        val indexed = indexed(untagged)

        val metadata = indexed.metadata.withTags(FileTags(title = "T", artist = "A", album = "L", trackNumber = 9))

        assertEquals(Sourced("T", FieldSource.Tags), metadata.title)
        assertEquals(Sourced("A", FieldSource.Tags), metadata.artist)
        assertEquals(Sourced("L", FieldSource.Tags), metadata.album)
        assertEquals(Sourced(9, FieldSource.Tags), metadata.trackNumber)
    }

    @Test
    fun aMissingTag_leavesWhatIsThere() {
        val indexed = indexed(untagged)

        val metadata = indexed.metadata.withTags(FileTags())

        assertEquals(indexed.metadata, metadata)
    }

    @Test
    fun duration_keepsMediaStores_overTheParsers() {
        val metadata = indexed(untagged).metadata.withTags(FileTags(durationMillis = 5))

        assertEquals(Sourced(1000L, FieldSource.MediaStore), metadata.durationMillis)
    }

    @Test
    fun duration_takesTheParsers_whenMediaStoreHasNone() {
        val metadata = indexed(untagged.copy(durationMillis = null)).metadata.withTags(FileTags(durationMillis = 5))

        assertEquals(Sourced(5L, FieldSource.Tags), metadata.durationMillis)
    }

    @Test
    fun anUnchangedFile_keepsItsTags_overMediaStore() {
        val read = indexed(untagged).let { song ->
            val write = song.toDraftAfterReading(FileTags(title = "From tags"))
            song.copy(metadata = write.metadata, tagsReadAt = write.tagsReadAt)
        }

        val write = untagged.copy(title = "From MediaStore").toDraft(read)

        assertEquals(Sourced("From tags", FieldSource.Tags), write.metadata.title)
        assertEquals(read.tagsReadAt, write.tagsReadAt)
    }

    @Test
    fun aSongRead_stillTakesAStrongerSource_whenOneTurnsUp() {
        val read = indexed(untagged).let { it.copy(tagsReadAt = it.file.dateModified) }

        val write = untagged.copy(artist = "Now known").toDraft(read)

        assertEquals(Sourced("Now known", FieldSource.MediaStore), write.metadata.artist)
    }

    @Test
    fun aChangedFile_losesWhatItsOldTagsSaid_andIsReadAgain() {
        val read = indexed(untagged).let { song ->
            val write = song.toDraftAfterReading(FileTags(title = "Old tag", hasEmbeddedPicture = true))
            song.copy(metadata = write.metadata, hasEmbeddedPicture = true, tagsReadAt = write.tagsReadAt)
        }

        val write = untagged.copy(file = untagged.file.copy(dateModified = 999)).toDraft(read)

        assertEquals(Sourced("Neon Rain", FieldSource.FileName), write.metadata.title)
        assertNull(write.tagsReadAt)
        assertNull(write.hasEmbeddedPicture)
    }

    @Test
    fun songsInDifferentFolders_withTheSameTags_areOneAlbum() {
        val tags = FileTags(artist = "ABC", album = "123")
        val first = indexed(audioFile(1, path = "Music/One/", name = "a.mp3", artist = null, album = null))
        val second = indexed(audioFile(2, path = "Download/Two/Three/", name = "b.mp3", artist = null, album = null))

        val writes = groupSongs(listOf(first.toDraftAfterReading(tags), second.toDraftAfterReading(tags)))

        assertEquals(writes[0].album.key, writes[1].album.key)
    }

    private fun indexed(file: AudioFile) = IndexedSong(
        id = SongId(file.file.mediaStoreId),
        file = file.file,
        metadata = file.toMetadata(),
        artistKey = "",
        albumKey = "",
    )
}
