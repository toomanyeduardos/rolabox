package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

/** A rule with "or" in the middle, between the primary action and the alternatives. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
        Text(stringResource(R.string.ds_or), style = RolaboxType.styles.caption)
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
    }
}

@PreviewLightDark
@Composable
private fun OrDividerPreview() {
    RolaboxTheme { OrDivider() }
}
