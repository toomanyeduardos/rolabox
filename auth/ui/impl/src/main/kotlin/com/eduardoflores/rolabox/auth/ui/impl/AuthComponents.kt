package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.component.LcdStatusBar
import com.eduardoflores.rolabox.common.designsystem.component.WordmarkHeader
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

/** The frame the account screens share: back key and wordmark, LCD, heading, then the screen's content. */
@Composable
internal fun AuthScaffold(
    lcdLeft: String,
    title: String,
    subtitle: String,
    footer: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    lcdRight: String = "",
    lcdError: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val styles = RolaboxType.styles
    Column(
        modifier = modifier
            .fillMaxSize()
            .brushedMetal()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WordmarkHeader(onBack = onBack)
        LcdStatusBar(left = lcdLeft, right = lcdRight, error = lcdError)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = styles.screenTitle)
            Text(text = subtitle, style = styles.screenSubtitle)
        }
        content()
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            // The links have 48dp touch targets, so they already sit about 12dp apart.
            content = footer,
        )
    }
}
