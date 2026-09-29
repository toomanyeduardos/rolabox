package com.eduardoflores.rolabox.core.auth.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    private fun strength(password: String) = PasswordPolicy.strengthOf(password)

    @Test
    fun isLongEnough_boundary() {
        assertFalse(PasswordPolicy.isLongEnough(""))
        assertFalse(PasswordPolicy.isLongEnough("a".repeat(PasswordPolicy.MIN_LENGTH - 1)))
        assertTrue(PasswordPolicy.isLongEnough("a".repeat(PasswordPolicy.MIN_LENGTH)))
    }

    @Test
    fun isLongEnough_countsSpacesAndDoesNotTrim() {
        assertTrue(PasswordPolicy.isLongEnough("        "))
    }

    @Test
    fun strength_emptyIsEmpty() {
        assertEquals(PasswordStrength.Empty, strength(""))
    }

    @Test
    fun strength_belowMinimumIsWeakHoweverVaried() {
        assertEquals(PasswordStrength.Weak, strength("aB3\$xYz"))
    }

    @Test
    fun strength_shortAndSimpleIsWeak() {
        assertEquals(PasswordStrength.Weak, strength("kdjfhqpw"))
    }

    @Test
    fun strength_longerOrMoreVariedRaisesIt() {
        assertEquals(PasswordStrength.Fair, strength("kdjfhqpwzmxn"))
        assertEquals(PasswordStrength.Strong, strength("kdjfhqPwzm4x"))
        assertEquals(PasswordStrength.VeryStrong, strength("kdjfhqPwzm4x!"))
        assertEquals(PasswordStrength.Strong, strength("kdjfhqpwzmxnvbcy"))
        assertEquals(PasswordStrength.VeryStrong, strength("kdjfhqPwzmxn4vbc"))
    }

    @Test
    fun strength_isNeverLowerForALongerPassword() {
        val base = "kdjfhqPw"
        val strengths = (0..12).map { strength(base + "z4!x".repeat(4).take(it)).level }
        assertEquals(strengths.sorted(), strengths)
    }

    @Test
    fun strength_commonPasswordsAreWeakCaseInsensitively() {
        assertEquals(PasswordStrength.Weak, strength("password123"))
        assertEquals(PasswordStrength.Weak, strength("PassWord123"))
        assertEquals(PasswordStrength.Weak, strength("Qwertyuiop"))
    }

    @Test
    fun strength_repeatedCharacterOrBlockIsWeak() {
        assertEquals(PasswordStrength.Weak, strength("aaaaaaaaaaaaaaaaaaaa"))
        assertEquals(PasswordStrength.Weak, strength("abcabcabcabcabc"))
        assertEquals(PasswordStrength.Weak, strength("1212121212121212"))
    }

    @Test
    fun strength_sequencesAreWeak() {
        assertEquals(PasswordStrength.Weak, strength("abcdefghijklmnop"))
        assertEquals(PasswordStrength.Weak, strength("9876543210"))
    }

    @Test
    fun strength_unicodeLettersCountAsLetters() {
        assertEquals(PasswordStrength.Fair, strength("contraseñaaas"))
    }

    @Test
    fun strength_levelMatchesTheMeterSegments() {
        assertEquals(listOf(0, 1, 2, 3, 4), PasswordStrength.entries.map { it.level })
    }
}
