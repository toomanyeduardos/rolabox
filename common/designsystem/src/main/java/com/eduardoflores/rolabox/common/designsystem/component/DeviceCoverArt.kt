package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

private val CoverShape = RoundedCornerShape(4.dp)
private val PreviewSize = 108.dp
private val PreviewPadding = 16.dp
private const val MARK_FRACTION = 0.56f

/**
 * The cover art of a song, cropped to the square of its frame: a rounded square with a thin border. With no [image],
 * because the song has none or it is still loading, it is [DeviceCoverArtPlaceholder] (ADR-026). Its size comes from
 * [modifier]. It is decoration, since the song's names are on the screen, so a screen reader skips it.
 */
@Composable
fun DeviceCoverArt(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image == null) {
        DeviceCoverArtPlaceholder(modifier)
    } else {
        Image(
            image,
            contentDescription = null,
            modifier = modifier
                .clip(CoverShape)
                .border(1.dp, RolaboxMetal.colors.displayRule, CoverShape),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * The cover art of a song that has none, which is a valid case: the app's icon, the vinyl on a tile of
 * aluminum, in the frame real artwork gets: a rounded square with a thin border. Its size comes from
 * [modifier]. It is decoration, so a screen reader skips it.
 */
@Composable
fun DeviceCoverArtPlaceholder(modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Box(
        modifier
            .clip(CoverShape)
            .background(Brush.linearGradient(listOf(colors.displayArt, colors.displayArtEnd)))
            .border(1.dp, colors.displayRule, CoverShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ds_ic_vinyl),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(MARK_FRACTION),
        )
    }
}

@PreviewLightDark
@Composable
private fun DeviceCoverArtPlaceholderPreview() {
    RolaboxTheme {
        Box(Modifier.padding(PreviewPadding)) { DeviceCoverArtPlaceholder(Modifier.size(PreviewSize)) }
    }
}

@PreviewLightDark
@Composable
private fun DeviceCoverArtPreview() {
    RolaboxTheme {
        Box(Modifier.padding(PreviewPadding)) { DeviceCoverArt(sampleCoverArt(), Modifier.size(PreviewSize)) }
    }
}

/**
 * A cover for previews and screenshot tests, drawn here so no image is committed: a sunset of the accent's oranges
 * over a dark sea, wider than it is tall, so the crop to a square shows.
 */
fun sampleCoverArt(): ImageBitmap {
    val bitmap = ImageBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT)
    val size = Size(SAMPLE_WIDTH.toFloat(), SAMPLE_HEIGHT.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(Brush.verticalGradient(listOf(SampleSky, SampleHorizon)))
        drawCircle(
            SampleSun,
            radius = size.height / SUN_FRACTION,
            center = Offset(size.width / 2, size.height * HORIZON),
        )
        drawRect(SampleSea, topLeft = Offset(0f, size.height * HORIZON), size = size.copy(height = size.height))
    }
    return bitmap
}

private const val SAMPLE_WIDTH = 240
private const val SAMPLE_HEIGHT = 180
private const val SUN_FRACTION = 4f
private const val HORIZON = 0.62f
private val SampleSky = Color(0xFF3B1F4A)
private val SampleHorizon = Color(0xFFE8A33D)
private val SampleSun = Color(0xFFFFD27A)
private val SampleSea = Color(0xFF1D2B3A)
