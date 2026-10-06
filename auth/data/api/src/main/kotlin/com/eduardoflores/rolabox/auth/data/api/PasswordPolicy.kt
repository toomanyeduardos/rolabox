package com.eduardoflores.rolabox.auth.data.api

/** How hard a password is to guess, as the strength meter shows it. [level] is the number of segments lit. */
enum class PasswordStrength {
    Empty,
    Weak,
    Fair,
    Good,
    Strong,
    ;

    val level: Int get() = ordinal
}

/**
 * The rules for a new password: at least [minLength] characters, with a lowercase letter, an
 * uppercase letter, a digit and a special character. They mirror the password policy enforced in
 * Firebase, which has the last word. Passing them doesn't make a password strong: the strength meter
 * still tells the user about a predictable one.
 */
interface PasswordPolicy {
    val minLength: Int

    fun isLongEnough(password: String): Boolean

    fun hasRequiredCharacters(password: String): Boolean

    fun strengthOf(password: String): PasswordStrength
}
