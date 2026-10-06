package com.eduardoflores.rolabox.device.ui.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The device, a destination on the app stack (ADR-018). What its display shows is the screen stack inside it. */
@Serializable
data object DeviceKey : NavKey

/** The main menu, where the screen stack inside the display starts (ADR-018). */
@Serializable
data object MainMenuKey : NavKey
