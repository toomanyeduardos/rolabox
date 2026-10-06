package com.eduardoflores.rolabox.device.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The settings list, a full screen on the app stack (ADR-018, rule 11). */
@Serializable
data object SettingsKey : NavKey
