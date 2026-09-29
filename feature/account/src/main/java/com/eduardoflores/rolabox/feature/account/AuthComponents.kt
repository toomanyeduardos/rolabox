package com.eduardoflores.rolabox.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eduardoflores.rolabox.core.designsystem.R as DesignSystemR
import com.eduardoflores.rolabox.core.designsystem.component.BackKey
import com.eduardoflores.rolabox.core.designsystem.component.LcdStrip
import com.eduardoflores.rolabox.core.designsystem.component.MetalKeyButton
import com.eduardoflores.rolabox.core.designsystem.component.Wordmark
import com.eduardoflores.rolabox.core.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal

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
    val colors = RolaboxMetal.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .brushedMetal(colors.bodyTop, colors.bodyBottom)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack !=
                null
            ) {
                BackKey(onClick = onBack, contentDescription = stringResource(DesignSystemR.string.ds_back))
            }
            Wordmark()
        }
        LcdStrip(left = lcdLeft, right = lcdRight, error = lcdError)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = colors.ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.03).em,
                lineHeight = 32.sp,
            )
            Text(text = subtitle, color = colors.muted, fontSize = 14.5.sp, lineHeight = 21.sp)
        }
        content()
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = footer,
        )
    }
}

@Composable
internal fun OrDivider(modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
        Text(stringResource(DesignSystemR.string.ds_or), color = colors.muted, fontSize = 12.sp)
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
    }
}

/** [icon] is where Google's official "G" goes. Google's branding rules don't allow redrawing it. */
@Composable
internal fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit = {}) {
    MetalKeyButton(onClick = onClick, modifier = modifier) {
        icon()
        Text(
            text = stringResource(R.string.account_google),
            color = RolaboxMetal.colors.ink,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun FooterLink(prompt: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Row(
        modifier.clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(prompt, color = colors.muted, fontSize = 14.sp)
        Text(action, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** A small accent-colored action, for the answers under an error. */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = RolaboxMetal.colors.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        ).padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
