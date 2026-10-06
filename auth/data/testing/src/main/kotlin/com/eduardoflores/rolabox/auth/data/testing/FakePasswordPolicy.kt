package com.eduardoflores.rolabox.auth.data.testing

import com.eduardoflores.rolabox.auth.data.api.PasswordPolicy
import com.eduardoflores.rolabox.auth.data.api.PasswordStrength
import javax.inject.Inject
import javax.inject.Singleton

/** Answers what the test set, whatever the password. The real rules are tested in `:auth:data:impl`. */
@Singleton
class FakePasswordPolicy @Inject constructor() : PasswordPolicy {
    override var minLength = 8

    /** What [isLongEnough] answers. */
    var longEnough = true

    /** What [hasRequiredCharacters] answers. */
    var requiredCharacters = true

    /** What [strengthOf] answers. */
    var strength = PasswordStrength.Strong

    override fun isLongEnough(password: String): Boolean = longEnough

    override fun hasRequiredCharacters(password: String): Boolean = requiredCharacters

    override fun strengthOf(password: String): PasswordStrength = strength
}
