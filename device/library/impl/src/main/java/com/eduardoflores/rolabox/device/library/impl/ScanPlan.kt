package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.AudioFileChanges
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState

/** What a scan changes in the index: the songs to add or update, and those whose files are gone. */
internal data class ScanPlan(val writes: List<SongWrite>, val removals: List<SongId>)

/**
 * Compares what MediaStore found with the index. A file finds its song by its MediaStore id, then by its path and
 * name, then by its name and size, each among the songs not taken yet, so a song keeps its id when the system
 * rebuilds its database or the file is moved (ADR-024, Identity). A song is written only if something changed.
 */
internal fun planScan(files: List<AudioFile>, indexed: List<IndexedSong>): ScanPlan {
    val (matches, untaken) = matchFiles(files, indexed)
    val writes = groupSongs(files.map { it.toDraft(matches[it]) }).changedFrom(indexed)
    return ScanPlan(writes, untaken.map { it.id })
}

/**
 * [planScan] from only what changed. A file that changed can take its own song or the song of a file that is gone, as
 * a moved file does, but never the song of a file still there: that one is a copy. The rules look at a whole album,
 * so the songs that didn't change are grouped again with those that did.
 */
internal fun planChanges(changes: AudioFileChanges, indexed: List<IndexedSong>): ScanPlan {
    val changedIds = changes.changed.mapTo(HashSet()) { it.file.mediaStoreId }
    val (open, kept) = indexed.partition {
        it.file.mediaStoreId in changedIds ||
            it.file.mediaStoreId !in changes.presentIds
    }
    val (matches, untaken) = matchFiles(changes.changed, open)
    val drafts = changes.changed.map { it.toDraft(matches[it]) } + kept.map { it.toDraft() }
    return ScanPlan(groupSongs(drafts).changedFrom(indexed), untaken.map { it.id })
}

/** How much of MediaStore a scan has to read, after comparing it with what the last scan saw. */
internal enum class ScanExtent { Nothing, Changes, Everything }

/**
 * A generation means something only within one version of a volume's database, and doesn't exist before API 30. So
 * anything but the same volumes at the same versions, with generations that didn't go back, reads everything.
 */
internal fun scanExtent(saved: List<VolumeScanState>, current: List<VolumeScanState>): ScanExtent {
    val before = saved.associateBy { it.volume }
    val comparable = saved.isNotEmpty() && saved.size == current.size && current.all { now ->
        val then = before[now.volume]
        val generationThen = then?.generation
        val generationNow = now.generation
        then?.version == now.version && generationThen != null && generationNow != null &&
            generationNow >= generationThen
    }
    return when {
        !comparable -> ScanExtent.Everything
        saved.toSet() == current.toSet() -> ScanExtent.Nothing
        else -> ScanExtent.Changes
    }
}

/** Each file's song, by its MediaStore id, then by its path and name, then by its name and size, and the songs left. */
private fun matchFiles(
    files: List<AudioFile>,
    candidates: List<IndexedSong>,
): Pair<Map<AudioFile, IndexedSong>, List<IndexedSong>> {
    val matches = HashMap<AudioFile, IndexedSong>(files.size)
    var untaken = candidates
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
    return matches to untaken
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
