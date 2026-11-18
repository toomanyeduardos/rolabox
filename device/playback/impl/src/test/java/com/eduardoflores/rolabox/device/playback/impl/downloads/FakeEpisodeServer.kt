package com.eduardoflores.rolabox.device.playback.impl.downloads

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * An HTTP server on the loopback that serves [bytes] as an episode's enclosure, with range requests, as a podcast's
 * host does. It is the JDK's own server: OkHttp's MockWebServer belongs to `:network:impl` modules (ADR-027, rule 7).
 *
 * [missing] answers 404. [offline] refuses every request as a lost connection does, and [stallAfter] sends that many
 * bytes of the next answer and then waits, until [release] or [dropConnections]. The answers after it aren't stalled.
 */
internal class FakeEpisodeServer(private val bytes: ByteArray) : AutoCloseable {
    /** The `Range` header of each request, or `null` for a request of the whole file. */
    val ranges: MutableList<String?> = CopyOnWriteArrayList()

    @Volatile
    var missing = false

    @Volatile
    var offline = false

    @Volatile
    var stallAfter: Int? = null

    /** Stalls every answer after this many bytes while it is set, as a network that barely moves does. */
    @Volatile
    var stallEvery: Int? = null

    // Each release opens the gate of the answers stalled until then, and the next stall waits at a new one.
    @Volatile
    private var gate = Semaphore(0)
    private val open = CopyOnWriteArrayList<HttpExchange>()

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/") { exchange -> serve(exchange) }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}/episode.mp3"

    /** Lets every stalled answer go on. */
    fun release() {
        stallAfter = null
        openGate()
    }

    /** Ends every answer under way, as a network that goes does. */
    fun dropConnections() {
        open.forEach { it.close() }
        open.clear()
        openGate()
    }

    private fun openGate() {
        val stalled = gate
        gate = Semaphore(0)
        stalled.release(Int.MAX_VALUE / 2)
    }

    override fun close() {
        dropConnections()
        server.stop(0)
    }

    private fun serve(exchange: HttpExchange) {
        val range = exchange.requestHeaders.getFirst("Range")
        ranges += range
        when {
            offline -> exchange.close()

            missing -> {
                exchange.sendResponseHeaders(NOT_FOUND, -1)
                exchange.close()
            }

            else -> answer(exchange, range)
        }
    }

    private fun answer(exchange: HttpExchange, range: String?) {
        val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
        val body = bytes.copyOfRange(from, bytes.size)
        exchange.responseHeaders.add("Content-Type", "audio/mpeg")
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        if (range != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $from-${bytes.size - 1}/${bytes.size}")
            exchange.sendResponseHeaders(PARTIAL, body.size.toLong())
        } else {
            exchange.sendResponseHeaders(OK, body.size.toLong())
        }
        open += exchange
        // An answer cut short, by the client or by dropConnections, is what a lost connection is: nothing to report.
        runCatching {
            exchange.responseBody.use { out ->
                val stallAt = synchronized(this) { stallAfter.also { stallAfter = null } } ?: stallEvery
                if (stallAt != null && stallAt < body.size) {
                    val stalled = gate
                    out.write(body, 0, stallAt)
                    out.flush()
                    stalled.acquire()
                    if (exchange in open) out.write(body, stallAt, body.size - stallAt)
                } else {
                    out.write(body)
                }
            }
        }
        open -= exchange
    }

    private companion object {
        const val OK = 200
        const val PARTIAL = 206
        const val NOT_FOUND = 404
    }
}
