package com.eduardoflores.rolabox.feature.account

import com.eduardoflores.rolabox.core.auth.api.PasswordPolicy

internal enum class NameError { Required }

internal enum class EmailError {
    Required,
    Invalid,

    /** Reported by the backend, so it can't be found by looking at the text. */
    AlreadyInUse,
}

internal enum class PasswordError {
    TooShort,

    /** Reported by the backend's password policy, which can be stricter than ours. */
    Rejected,
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

internal fun validatePassword(password: String): PasswordError? =
    if (PasswordPolicy.isLongEnough(password)) null else PasswordError.TooShort
