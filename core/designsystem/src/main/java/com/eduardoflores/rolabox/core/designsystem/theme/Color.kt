package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Raw palette for the default skin. Screens should read roles from MaterialTheme.colorScheme,
// never these values directly, so a skin can swap the palette without touching call sites.
internal val Ink10 = Color(0xFF1A1C1E)
internal val Ink20 = Color(0xFF2F3033)
internal val Ink30 = Color(0xFF45474A)
internal val Ink80 = Color(0xFFC6C6CA)
internal val Ink90 = Color(0xFFE2E2E6)
internal val Ink95 = Color(0xFFF1F0F4)
internal val Ink99 = Color(0xFFFCFCFF)

internal val Slate30 = Color(0xFF44474E)
internal val Slate50 = Color(0xFF74777F)
internal val Slate60 = Color(0xFF8E9099)
internal val Slate80 = Color(0xFFC4C6D0)
internal val Slate90 = Color(0xFFE0E2EC)

internal val Blue10 = Color(0xFF001B3D)
internal val Blue20 = Color(0xFF003062)
internal val Blue30 = Color(0xFF00468A)
internal val Blue40 = Color(0xFF1F5FA9)
internal val Blue80 = Color(0xFFA8C8FF)
internal val Blue90 = Color(0xFFD6E3FF)

internal val Steel10 = Color(0xFF111C2B)
internal val Steel20 = Color(0xFF263141)
internal val Steel30 = Color(0xFF3C4758)
internal val Steel40 = Color(0xFF545F71)
internal val Steel80 = Color(0xFFBCC7DC)
internal val Steel90 = Color(0xFFD8E3F8)

internal val Orange10 = Color(0xFF2F1500)
internal val Orange20 = Color(0xFF4F2500)
internal val Orange30 = Color(0xFF703700)
internal val Orange40 = Color(0xFF924C00)
internal val Orange80 = Color(0xFFFFB77C)
internal val Orange90 = Color(0xFFFFDCC2)

internal val Red10 = Color(0xFF410002)
internal val Red20 = Color(0xFF690005)
internal val Red30 = Color(0xFF93000A)
internal val Red40 = Color(0xFFBA1A1A)
internal val Red80 = Color(0xFFFFB4AB)
internal val Red90 = Color(0xFFFFDAD6)

internal val DefaultLightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Steel40,
    onSecondary = Color.White,
    secondaryContainer = Steel90,
    onSecondaryContainer = Steel10,
    tertiary = Orange40,
    onTertiary = Color.White,
    tertiaryContainer = Orange90,
    onTertiaryContainer = Orange10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Ink99,
    onBackground = Ink10,
    surface = Ink99,
    onSurface = Ink10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Slate30,
    inverseSurface = Ink20,
    inverseOnSurface = Ink95,
    inversePrimary = Blue80,
    outline = Slate50,
    outlineVariant = Slate80,
)

internal val DefaultDarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Steel80,
    onSecondary = Steel20,
    secondaryContainer = Steel30,
    onSecondaryContainer = Steel90,
    tertiary = Orange80,
    onTertiary = Orange20,
    tertiaryContainer = Orange30,
    onTertiaryContainer = Orange90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Ink10,
    onBackground = Ink90,
    surface = Ink10,
    onSurface = Ink90,
    surfaceVariant = Slate30,
    onSurfaceVariant = Slate80,
    inverseSurface = Ink90,
    inverseOnSurface = Ink20,
    inversePrimary = Blue40,
    outline = Slate60,
    outlineVariant = Ink30,
)
