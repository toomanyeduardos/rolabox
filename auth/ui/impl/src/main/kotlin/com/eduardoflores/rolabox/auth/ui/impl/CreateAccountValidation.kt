package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.auth.data.api.PasswordPolicy

internal enum class NameError { Required }

internal enum class EmailError {
    Required,
    Invalid,

    /** Reported by the backend, so it can't be found by looking at the text. */
    AlreadyInUse,
}

internal sealed interface PasswordError {
    /** Shorter than [minLength], the minimum of the password policy. */
    data class TooShort(val minLength: Int) : PasswordError

    /** Long enough, but missing a lowercase letter, an uppercase letter, a digit or a special character. */
    data object MissingCharacters : PasswordError

    /** Reported by the backend's password policy, which can be stricter than ours. */
    data object Rejected : PasswordError
}

// One '@' with something on both sides, and a dot in the domain. The backend has the last word: this
// only catches typos before a round trip.
private val EmailPattern = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

internal fun validateName(name: String): NameError? = if (name.isBlank()) NameError.Required else null

internal fun validateEmail(email: String): EmailError? = when {
    email.isBlank() -> EmailError.Required
    !EmailPattern.matches(email) -> EmailError.Invalid
    else -> null
}

internal fun validatePassword(password: String, policy: PasswordPolicy): PasswordError? = when {
    !policy.isLongEnough(password) -> PasswordError.TooShort(policy.minLength)
    !policy.hasRequiredCharacters(password) -> PasswordError.MissingCharacters
    else -> null
}
