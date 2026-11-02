@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

import com.eduardoflores.rolabox.device.library.local.api.FileTags

private const val ATOM_HEADER = 8
private const val LARGE_ATOM_HEADER = 16
private const val MAX_ITEM = 64 * 1024
private const val FULL_BOX_HEADER = 4L
private const val MVHD_LENGTH = 32
private const val MVHD_V0_LENGTH = 20
private const val DATA_HEADER = 16
private const val DATA_UTF_16 = 2
private const val MILLIS_PER_SECOND = 1000

/** [next] is where the next atom starts, which can be past [end] when a file is cut short. */
private class Atom(val type: String, val start: Long, val end: Long, val next: Long)

/** Whether these bytes, the start of a file, are an MP4's. */
internal fun ByteArray.isMp4() = size >= ATOM_HEADER && ascii(4, 4) == "ftyp"

/**
 * The `ilst` items of an MP4, and the duration from `mvhd`. Only the atoms on the way there are read, so `mdat` and a
 * cover's bytes are skipped. Null when there's no `moov`, which a file cut short before it has lost.
 */
internal fun readMp4(source: ByteSource): FileTags? {
    val moov = source.atoms(0, source.size).firstOrNull { it.type == "moov" } ?: return null
    val children = source.atoms(moov.start, moov.end).toList()
    val duration = children.firstOrNull { it.type == "mvhd" }?.let { source.durationMillis(it) }
    val ilst = source.findIlst(children)
    val items = ilst?.let { source.atoms(it.start, it.end).toList() }.orEmpty()
    val values = items.associateBy { it.type }
    fun text(type: String) = values[type]?.let { source.itemData(it) }?.text()
    fun number(type: String) = values[type]?.let { source.itemData(it) }?.number()
    return FileTags(
        title = text("©nam"),
        artist = text("©ART"),
        albumArtist = text("aART"),
        album = text("©alb"),
        trackNumber = number("trkn"),
        discNumber = number("disk"),
        year = text("©day")?.tagYear(),
        compilation = values["cpil"]?.let { source.itemData(it) }?.flag(),
        durationMillis = duration,
        hasEmbeddedPicture = "covr" in values,
    )
}

private fun ByteSource.atoms(start: Long, end: Long): Sequence<Atom> = sequence {
    var position = start
    while (position + ATOM_HEADER <= end) {
        val atom = atomAt(position, end) ?: break
        yield(atom)
        position = atom.next
    }
}

private fun ByteSource.atomAt(position: Long, end: Long): Atom? {
    val header = read(position, LARGE_ATOM_HEADER)
    if (header.size < ATOM_HEADER) return null
    val declared = header.bigEndian(0, 4)
    val large = declared == 1L && header.size == LARGE_ATOM_HEADER
    val headerSize = if (large) LARGE_ATOM_HEADER else ATOM_HEADER
    val size = when {
        large -> header.bigEndian(8, 8)
        declared == 0L -> end - position
        else -> declared
    }
    // A file cut short has atoms that say they end past it.
    return Atom(header.ascii(4, 4), position + headerSize, minOf(position + size, end), position + size)
        .takeIf { size >= headerSize }
}

// iTunes keeps the tags in moov/udta/meta/ilst, and some writers in moov/meta/ilst.
private fun ByteSource.findIlst(moovChildren: List<Atom>): Atom? {
    val udta = moovChildren.firstOrNull { it.type == "udta" }
    val meta = udta?.let { atoms(it.start, it.end).firstOrNull { atom -> atom.type == "meta" } }
        ?: moovChildren.firstOrNull { it.type == "meta" }
        ?: return null
    return atoms(metaChildrenStart(meta), meta.end).firstOrNull { it.type == "ilst" }
}

// `meta` is a full box in ISO files, with four bytes of version and flags, and a plain one in QuickTime's.
private fun ByteSource.metaChildrenStart(meta: Atom): Long {
    val head = read(meta.start, ATOM_HEADER)
    val plain = head.size == ATOM_HEADER && head.ascii(4, 4) == "hdlr"
    return if (plain) meta.start else meta.start + FULL_BOX_HEADER
}

private fun ByteSource.durationMillis(mvhd: Atom): Long? {
    val box = read(mvhd.start, MVHD_LENGTH)
    val version1 = box.firstOrNull() == 1.toByte()
    if (box.size < if (version1) MVHD_LENGTH else MVHD_V0_LENGTH) return null
    val timescale = box.bigEndian(if (version1) 20 else 12, 4)
    val duration = if (version1) box.bigEndian(24, 8) else box.bigEndian(16, 4)
    return if (timescale > 0 && duration > 0) duration * MILLIS_PER_SECOND / timescale else null
}

private class ItemData(val type: Int, val payload: ByteArray) {
    fun text(): String? = when (type) {
        DATA_UTF_16 -> payload.decodeUtf16Be()
        else -> payload.toString(Charsets.UTF_8)
    }.cleanTagText()

    // trkn and disk: two reserved bytes, the number and the total, as 16-bit integers.
    fun number(): Int? = if (payload.size >= 4) payload.bigEndian(2, 2).toInt().takeIf { it > 0 } else null

    fun flag(): Boolean? = payload.lastOrNull()?.let { it != 0.toByte() }
}

// The data box's header: its size and type, then the type of its payload, and a locale.
private fun ByteSource.itemData(item: Atom): ItemData? {
    val length = item.end - item.start
    val bytes = if (length in DATA_HEADER..MAX_ITEM) read(item.start, length.toInt()) else ByteArray(0)
    val isData = bytes.size >= DATA_HEADER && bytes.ascii(4, 4) == "data"
    val end = if (isData) minOf(bytes.bigEndian(0, 4), bytes.size.toLong()).toInt() else 0
    return if (end < DATA_HEADER) null else ItemData(bytes.bigEndian(8, 4).toInt(), bytes.copyOfRange(DATA_HEADER, end))
}
