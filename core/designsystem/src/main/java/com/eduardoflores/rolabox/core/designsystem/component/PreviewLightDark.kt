package com.eduardoflores.rolabox.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

/** Renders a preview in both light and dark themes. */
@Preview(name = "Light", uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF1A1C1E)
annotation class PreviewLightDark
