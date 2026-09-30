package com.eduardoflores.rolabox.feature.account

/** How long the resend key stays disabled after a link was sent. */
internal const val RESEND_COOLDOWN_SECONDS = 60

/** How long it stays disabled when the backend says there were too many requests. */
internal const val TOO_MANY_REQUESTS_COOLDOWN_SECONDS = 300

private const val SECONDS_PER_MINUTE = 60

/** "0:42", "4:59": what the resend key shows while it counts down. */
internal fun formatCountdown(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    return "${clamped / SECONDS_PER_MINUTE}:${(clamped % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}
