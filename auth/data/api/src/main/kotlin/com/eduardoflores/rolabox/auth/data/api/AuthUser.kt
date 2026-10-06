package com.eduardoflores.rolabox.auth.data.api

/** [id] is stable across sign-in providers. [photoUrl] is a URL string, since `:api` modules are pure JVM. */
data class AuthUser(val id: String, val displayName: String?, val photoUrl: String?)
