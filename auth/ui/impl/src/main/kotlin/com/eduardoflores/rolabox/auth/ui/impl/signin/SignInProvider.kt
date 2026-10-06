package com.eduardoflores.rolabox.auth.ui.impl.signin

/**
 * The sign-in providers whose UI step this package can run. Adding one is the checklist of ADR-014:
 * a case of `SignInCredential`, its branch in `:auth:data:impl`, a value here with its step and
 * button, and its configuration in `SignInConfig`.
 */
internal enum class SignInProvider { Google, }
