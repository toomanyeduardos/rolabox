package com.eduardoflores.rolabox.feature.account

import org.junit.Assert.assertEquals
import org.junit.Test

class ResetPasswordCooldownTest {
    @Test
    fun formatCountdown_showsMinutesAndPaddedSeconds() {
        assertEquals("1:00", formatCountdown(RESEND_COOLDOWN_SECONDS))
        assertEquals("0:42", formatCountdown(42))
        assertEquals("0:05", formatCountdown(5))
        assertEquals("4:59", formatCountdown(TOO_MANY_REQUESTS_COOLDOWN_SECONDS - 1))
        assertEquals("5:00", formatCountdown(TOO_MANY_REQUESTS_COOLDOWN_SECONDS))
    }

    @Test
    fun formatCountdown_neverGoesNegative() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:00", formatCountdown(-3))
    }
}
