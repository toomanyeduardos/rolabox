package com.eduardoflores.rolabox.device.library.api

/**
 * Where a cover is: the song whose picture it is, the one in its tags or the one in its folder (ADR-026). It holds no
 * image, and whether there is one is known only once [ArtworkRepository.loadArtwork] has looked.
 */
data class ArtworkRef(val song: SongId)

/**
 * A cover, as [width] by [height] ARGB pixels, row by row, since an `:api` has no image type. Each consumer turns it
 * into its own: an `ImageBitmap` on a screen, a `Bitmap` for the notification.
 */
class ArtworkImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "$width x $height, ${pixels.size} pixels" }
    }

    override fun equals(other: Any?): Boolean = other is ArtworkImage &&
        width == other.width &&
        height == other.height &&
        pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    override fun toString(): String = "ArtworkImage($width x $height)"
}
