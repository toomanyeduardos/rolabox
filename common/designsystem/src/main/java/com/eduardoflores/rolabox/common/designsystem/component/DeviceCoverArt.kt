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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

private val CoverShape = RoundedCornerShape(4.dp)
private val PreviewSize = 108.dp
private val PreviewPadding = 16.dp
private const val MARK_FRACTION = 0.56f

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
