package com.eduardoflores.rolabox.common.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview in light, in dark, and in light at the font scale every screen has to work at
 * (ADR-017). They are the three renders the screenshot tests record (ADR-016).
 */
@Preview(name = "Light", uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF1A1C1E)
@Preview(name = "Large font", uiMode = UI_MODE_NIGHT_NO, showBackground = true, fontScale = 1.5f)
annotation class PreviewLightDark
