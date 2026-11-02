package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.SongWrite

/** What a scan changes in the index: the songs to add or update, and those whose files are gone. */
internal data class ScanPlan(val writes: List<SongWrite>, val removals: List<SongId>)

/**
 * Compares what MediaStore found with the index. A file finds its song by its MediaStore id, then by its path and
 * name, then by its name and size, each among the songs not taken yet, so a song keeps its id when the system
 * rebuilds its database or the file is moved (ADR-024, Identity). A song is written only if something changed.
 */
internal fun planScan(files: List<AudioFile>, indexed: List<IndexedSong>): ScanPlan {
    val matches = HashMap<AudioFile, IndexedSong>(files.size)
    var untaken = indexed
    fun <K> matchBy(fileKey: (AudioFile) -> K, songKey: (IndexedSong) -> K) {
        val songs = untaken.groupByTo(HashMap(), songKey)
        files.filter { it !in matches }.forEach { file ->
            songs[fileKey(file)]?.removeFirstOrNull()?.let { matches[file] = it }
        }
        untaken = songs.values.flatten()
    }
    matchBy({ it.file.mediaStoreId }, { it.file.mediaStoreId })
    matchBy({ it.file.relativePath to it.file.fileName }, { it.file.relativePath to it.file.fileName })
    matchBy({ it.file.fileName to it.file.size }, { it.file.fileName to it.file.size })

    val writes = groupSongs(files.map { it.toDraft(matches[it]) }).changedFrom(indexed)
    return ScanPlan(writes, untaken.map { it.id })
}

/**
 * What the index has to change after reading [read] of [indexed]'s songs. The rules look at a whole album, so every
 * song is grouped again, and one read can move songs that weren't.
 */
internal fun regroupAfterReading(indexed: List<IndexedSong>, read: List<SongDraft>): List<SongWrite> {
    val readById = read.associateBy { it.id }
    return groupSongs(indexed.map { readById[it.id] ?: it.toDraft() }).changedFrom(indexed)
}

private fun IndexedSong.toDraft() = SongDraft(id, file, metadata, hasEmbeddedPicture, tagsReadAt)

/** A song is written only if something changed. */
private fun List<SongWrite>.changedFrom(indexed: List<IndexedSong>): List<SongWrite> {
    val byId = indexed.associateBy { it.id }
    return filterNot { write -> byId[write.id]?.let(write::isSameAs) == true }
}

private fun SongWrite.isSameAs(song: IndexedSong) =
    file == song.file && metadata == song.metadata && artist.key == song.artistKey &&
        album.key == song.albumKey && tagsReadAt == song.tagsReadAt && isDuplicate == song.isDuplicate
