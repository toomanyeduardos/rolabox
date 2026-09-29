package com.eduardoflores.rolabox.feature.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateAccountValidationTest {
    @Test
    fun name_blankIsRequired() {
        assertEquals(NameError.Required, validateName(""))
        assertEquals(NameError.Required, validateName("   "))
    }

    @Test
    fun name_anythingElseIsValid() {
        assertNull(validateName("Eduardo"))
        assertNull(validateName("Eduardo Flores"))
        assertNull(validateName("Zoë O'Brien-Núñez"))
    }

    @Test
    fun email_blankIsRequired() {
        assertEquals(EmailError.Required, validateEmail(""))
        assertEquals(EmailError.Required, validateEmail("  "))
    }

    @Test
    fun email_malformedIsInvalid() {
        listOf(
            "toomanyeduardos",
            "toomanyeduardos@",
            "@gmail.com",
            "toomanyeduardos@gmail",
            "toomanyeduardos@gmail.",
            "toomanyeduardos@.com",
            "toomanyeduardos@@gmail.com",
            "toomanyeduardos gmail@gmail.com",
            "toomanyeduardos@gmail..com",
        ).forEach { assertEquals("$it should be invalid", EmailError.Invalid, validateEmail(it)) }
    }

    @Test
    fun email_wellFormedIsValid() {
        listOf(
            "toomanyeduardos@gmail.com",
            "toomanyeduardos+rolabox@mail.co.uk",
            "e@b.io",
            "EDUARDO@MAIL.COM",
        ).forEach { assertNull("$it should be valid", validateEmail(it)) }
    }

    @Test
    fun password_shorterThanMinimumIsTooShort() {
        assertEquals(PasswordError.TooShort, validatePassword(""))
        assertEquals(PasswordError.TooShort, validatePassword("1234567"))
    }

    @Test
    fun password_atLeastMinimumIsValidEvenIfWeak() {
        assertNull(validatePassword("12345678"))
        assertNull(validatePassword("kdjfhqPwzm4x!"))
    }
}
