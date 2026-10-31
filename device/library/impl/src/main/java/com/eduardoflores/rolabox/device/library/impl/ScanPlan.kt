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

    val writes = files.mapNotNull { file ->
        val song = matches[file]
        file.toWrite(song?.id).takeUnless { song != null && it.isSameAs(song) }
    }
    return ScanPlan(writes, untaken.map { it.id })
}

private fun SongWrite.isSameAs(song: IndexedSong) =
    file == song.file && metadata == song.metadata && artist.key == song.artistKey && album.key == song.albumKey
