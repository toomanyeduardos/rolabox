package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Runs the call, and cancels it when the coroutine is cancelled, so a search the user typed past stops its request
 * (ADR-031, The directory). A connection that failed, or a timeout, is an [IOException]. The caller closes the
 * response.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, unused, _ -> unused.close() }
            }
        },
    )
}
