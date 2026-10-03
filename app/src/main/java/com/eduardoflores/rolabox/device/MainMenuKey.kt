package com.eduardoflores.rolabox.device

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The main menu, where the screen stack starts (ADR-018). Its rows are the list of features, so it lives in `:app`. */
@Serializable
internal data object MainMenuKey : NavKey
