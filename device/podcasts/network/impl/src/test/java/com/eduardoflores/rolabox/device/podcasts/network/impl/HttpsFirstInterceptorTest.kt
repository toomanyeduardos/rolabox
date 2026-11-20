package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * ADR-034, rule 4. The hosts are an interceptor after the one under test, which answers each URL as a host would,
 * with no network.
 */
class HttpsFirstInterceptorTest {
    private val asked = mutableListOf<String>()

    private fun client(host: (Interceptor.Chain) -> Response) = OkHttpClient.Builder()
        .addInterceptor(HttpsFirstInterceptor())
        .addInterceptor { chain ->
            asked += chain.request().url.toString()
            host(chain)
        }
        .build()

    private fun OkHttpClient.get(url: String): Response = newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun anHttpUrl_isAskedForAsHttps_andItsAnswerKept() {
        val response = client { answer(it, 200) }.get("http://feeds.example.com/show.xml?id=1")

        assertEquals(200, response.code)
        assertEquals("https://feeds.example.com/show.xml?id=1", response.request.url.toString())
        assertEquals(listOf("https://feeds.example.com/show.xml?id=1"), asked)
    }

    @Test
    fun a304OverHttps_isKept() {
        client { answer(it, 304) }.get("http://feeds.example.com/show.xml").close()

        assertEquals(listOf("https://feeds.example.com/show.xml"), asked)
    }

    @Test
    fun aHandshakeThatFails_fallsBackToTheUrlAsGiven() {
        val response = client { chain ->
            if (chain.request().url.isHttps) throw SSLHandshakeException("no certificate for this host")
            answer(chain, 200)
        }.get("http://feeds.example.com/show.xml")

        assertEquals(200, response.code)
        assertEquals("http://feeds.example.com/show.xml", response.request.url.toString())
        assertEquals(listOf("https://feeds.example.com/show.xml", "http://feeds.example.com/show.xml"), asked)
    }

    @Test
    fun anErrorOverHttps_fallsBackToTheUrlAsGiven() {
        val response = client { chain -> answer(chain, if (chain.request().url.isHttps) 404 else 200) }
            .get("http://feeds.example.com/show.xml")

        assertEquals(200, response.code)
        assertEquals(listOf("https://feeds.example.com/show.xml", "http://feeds.example.com/show.xml"), asked)
    }

    @Test
    fun anHttpsUrl_andAPortOfItsOwn_areAskedForAsGiven() {
        val client = client { answer(it, 200) }

        client.get("https://itunes.apple.com/search").close()
        client.get("http://feeds.example.com:8080/show.xml").close()

        assertEquals(listOf("https://itunes.apple.com/search", "http://feeds.example.com:8080/show.xml"), asked)
    }

    @Test
    fun aCancelledCall_isNotRetried() {
        val client = client { chain ->
            chain.call().cancel()
            throw IOException("Canceled")
        }

        assertThrows(IOException::class.java) { client.get("http://feeds.example.com/show.xml") }
        assertEquals(listOf("https://feeds.example.com/show.xml"), asked)
    }

    private fun answer(chain: Interceptor.Chain, code: Int) = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .build()
}
