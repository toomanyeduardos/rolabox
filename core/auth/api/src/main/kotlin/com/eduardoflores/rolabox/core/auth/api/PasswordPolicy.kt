package com.eduardoflores.rolabox.core.auth.api

/** How hard a password is to guess, as the strength meter shows it. [level] is the number of segments lit. */
enum class PasswordStrength {
    Empty,
    Weak,
    Fair,
    Strong,
    VeryStrong,
    ;

    val level: Int get() = ordinal
}

/**
 * The rules for a new password. Only the length is enforced: a weak password that is long enough
 * is allowed, and the strength meter tells the user about it.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    private const val LONG_LENGTH = 12
    private const val VERY_LONG_LENGTH = 16
    private const val MIN_SEQUENCE_RUN = 4
    private const val VARIETY_FOR_STRONG = 3
    private const val VARIETY_FOR_VERY_STRONG = 4

    // The most common passwords that are long enough to pass the length rule.
    private val commonPasswords = setOf(
        "password", "password1", "password12", "password123", "passw0rd", "p@ssw0rd", "12345678", "123456789",
        "1234567890", "qwertyui", "qwerty123", "qwertyuiop", "iloveyou", "letmein1", "welcome1", "welcome123",
        "admin123", "abc12345", "monkey123", "football1", "baseball1", "dragon123", "sunshine1", "princess1",
        "trustno1", "changeme", "11111111", "00000000", "rolabox1", "rolabox123",
    )

    fun isLongEnough(password: String): Boolean = password.length >= MIN_LENGTH

    fun strengthOf(password: String): PasswordStrength = when {
        password.isEmpty() -> PasswordStrength.Empty

        !isLongEnough(password) || isPredictable(password) -> PasswordStrength.Weak

        else -> when (points(password)) {
            0 -> PasswordStrength.Weak
            1 -> PasswordStrength.Fair
            2 -> PasswordStrength.Strong
            else -> PasswordStrength.VeryStrong
        }
    }

    // 0 to 4: up to two for length, up to two for a mix of lowercase, uppercase, digits and symbols.
    private fun points(password: String): Int {
        val lengthPoints = (if (password.length >= LONG_LENGTH) 1 else 0) +
            (if (password.length >= VERY_LONG_LENGTH) 1 else 0)
        val variety = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() },
        ).count { it }
        val varietyPoints = (if (variety >= VARIETY_FOR_STRONG) 1 else 0) +
            (if (variety >= VARIETY_FOR_VERY_STRONG) 1 else 0)
        return lengthPoints + varietyPoints
    }

    private fun isPredictable(password: String): Boolean = password.lowercase() in commonPasswords ||
        password.toSet().size == 1 ||
        isRepeatedBlock(password) ||
        isSequence(password)

    // "abcabcabc", "12341234".
    private fun isRepeatedBlock(password: String): Boolean = (1..password.length / 2).any { size ->
        password.length % size == 0 && password == password.take(size).repeat(password.length / size)
    }

    // The whole password steps up or down by one: "12345678", "abcdefgh", "87654321".
    private fun isSequence(password: String): Boolean {
        if (password.length < MIN_SEQUENCE_RUN) return false
        val steps = password.lowercase().zipWithNext { a, b -> b - a }
        return steps.all { it == 1 } || steps.all { it == -1 }
    }
}
