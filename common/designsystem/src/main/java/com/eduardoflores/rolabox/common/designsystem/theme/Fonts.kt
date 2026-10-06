package com.eduardoflores.rolabox.common.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eduardoflores.rolabox.common.designsystem.R

/** The grotesk the designs call for, since Helvetica Neue isn't on Android. SIL OFL, see `third_party`. */
internal val HankenGrotesk = FontFamily(
    Font(R.font.ds_hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.ds_hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.ds_hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.ds_hanken_grotesk_bold, FontWeight.Bold),
)

/**
 * Google's font, for the label of the Sign in with Google button and nothing else. Google's branding
 * rules set it (Google Sans Medium), so it never follows a skin (ADR-000 rule 5). SIL OFL, see
 * `third_party`.
 */
internal val GoogleSans = FontFamily(
    Font(R.font.ds_google_sans_medium, FontWeight.Medium),
)
