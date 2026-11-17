package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient

/**
 * An answer recorded in `src/test/resources`: `itunes/` holds the directory's, as it gave them on 2026-11-17, and
 * `feeds/` a recorded feed cut to three items, with hand-made feeds for what it doesn't do.
 */
internal fun recorded(path: String): String = recordedStream(path).use { it.readBytes().decodeToString() }

internal fun recordedStream(path: String): InputStream =
    checkNotNull(Recorded::class.java.getResourceAsStream("/$path")) { "No recorded answer $path" }

private object Recorded

/** A client like the app's, that gives up on a silent server sooner, so a timeout is quick to test. */
internal fun testClient(): OkHttpClient = OkHttpClient.Builder()
    .readTimeout(READ_TIMEOUT.toJavaDuration())
    .build()

private val READ_TIMEOUT = 500.milliseconds

internal fun answer(code: Int, body: String = "", vararg headers: Pair<String, String>): MockResponse =
    MockResponse.Builder().code(code).body(body).apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .build()

/** An answer whose headers come after the client has stopped waiting. */
internal fun tooSlow(): MockResponse =
    MockResponse.Builder().code(200).headersDelay(TOO_SLOW_MILLIS, TimeUnit.MILLISECONDS).build()

private const val TOO_SLOW_MILLIS = 2_000L
