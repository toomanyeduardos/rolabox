package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.auth.data.testing.FakePasswordPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateAccountValidationTest {
    private val policy = FakePasswordPolicy()

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

    // The policy is a fake (ADR-021): its rules are tested in :auth:data:impl. These cover which error
    // each of its answers becomes.
    @Test
    fun password_notLongEnoughIsTooShort_withThePolicysMinimum() {
        policy.longEnough = false
        policy.minLength = 10

        assertEquals(PasswordError.TooShort(minLength = 10), validatePassword("1234567", policy))
    }

    @Test
    fun password_tooShortComesBeforeMissingCharacters() {
        policy.longEnough = false
        policy.requiredCharacters = false

        assertEquals(PasswordError.TooShort(policy.minLength), validatePassword("", policy))
    }

    @Test
    fun password_longEnoughButMissingACharacterKindIsRejected() {
        policy.requiredCharacters = false

        assertEquals(PasswordError.MissingCharacters, validatePassword("kdjfhqPw42", policy))
    }

    @Test
    fun password_thatPassesThePolicyIsValid() {
        assertNull(validatePassword("Password1!", policy))
    }
}
