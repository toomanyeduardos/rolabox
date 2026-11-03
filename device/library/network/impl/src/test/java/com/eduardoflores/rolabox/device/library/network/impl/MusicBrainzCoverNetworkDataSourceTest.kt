package com.eduardoflores.rolabox.device.library.network.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import com.eduardoflores.rolabox.device.library.network.api.CoverSourceError
import dagger.Lazy
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

private const val LIVE_2012 = "2f215175-266c-4945-b622-8c4f8cb0d4fb"
private val Jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
private val Live2012 = CoverQuery("Live 2012", CoverArtist.Named("Coldplay"))

/** The source against recorded answers, served by an interceptor: no request leaves the test. */
class MusicBrainzCoverNetworkDataSourceTest {
    private val requests = mutableListOf<HttpUrl>()
    private var answer: (HttpUrl) -> Answer = { url ->
        when {
            url.host == "musicbrainz.org" -> Answer(200, recorded("live-2012.json").toByteArray(), "application/json")
            url.encodedPath == "/release-group/$LIVE_2012/front-500" -> Answer(200, Jpeg, "image/jpeg")
            else -> Answer(404, ByteArray(0), "text/plain")
        }
    }

    private class Answer(val code: Int, val body: ByteArray, val type: String)

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val url = chain.request().url
                requests += url
                val served = answer(url)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(served.code)
                    .message("recorded")
                    .header("Content-Type", served.type)
                    .body(served.body.toResponseBody(served.type.toMediaType()))
                    .build()
            },
        )
        .build()

    private fun TestScope.source() = MusicBrainzCoverNetworkDataSource(
        Lazy { client },
        RequestSpacing(Duration.ZERO, TestTimeSource()),
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun aMatch_downloadsTheFrontImageOfItsReleaseGroup() = runTest {
        assertEquals(Cover(LIVE_2012, Jpeg).right(), source().findCover(Live2012))
        assertEquals("musicbrainz.org", requests[0].host)
        assertEquals("releasegroup:\"Live 2012\" AND artist:\"Coldplay\"", requests[0].queryParameter("query"))
        assertEquals("json", requests[0].queryParameter("fmt"))
        assertEquals("https://coverartarchive.org/release-group/$LIVE_2012/front-500", requests[1].toString())
    }

    @Test
    fun noMatch_isNoCover_withoutAskingTheArchive() = runTest {
        assertEquals(null.right(), source().findCover(CoverQuery("Parachutes", CoverArtist.Named("Coldplay"))))
        assertEquals(1, requests.size)
    }

    @Test
    fun aMatchWithNoFrontImage_isNoCover() = runTest {
        val search = answer
        answer =
            { url -> if (url.host == "coverartarchive.org") Answer(404, ByteArray(0), "text/plain") else search(url) }
        assertEquals(null.right(), source().findCover(Live2012))
    }

    @Test
    fun noNetwork_isUnreachable() = runTest {
        answer = { throw IOException("offline") }
        assertEquals(CoverSourceError.Unreachable.left(), source().findCover(Live2012))
    }

    @Test
    fun aTimeout_isUnreachable() = runTest {
        answer = { throw SocketTimeoutException("slow") }
        assertEquals(CoverSourceError.Unreachable.left(), source().findCover(Live2012))
    }

    @Test
    fun aRequestToSlowDown_isAServerError() = runTest {
        answer = { Answer(503, ByteArray(0), "text/plain") }
        assertEquals(CoverSourceError.Server(503).left(), source().findCover(Live2012))
    }

    @Test
    fun anAnswerThatIsntASearch_isMalformed() = runTest {
        answer = { Answer(200, "<html>maintenance</html>".toByteArray(), "text/html") }
        assertEquals(CoverSourceError.Malformed.left(), source().findCover(Live2012))
    }

    @Test
    fun anImageThatIsntOne_isMalformed() = runTest {
        val search = answer
        answer = { url -> if (url.host == "coverartarchive.org") Answer(200, Jpeg, "text/html") else search(url) }
        assertEquals(CoverSourceError.Malformed.left(), source().findCover(Live2012))
    }

    @Test
    fun theRequestsWaitTheirTurn() = runTest {
        val spacing = RequestSpacing(1.seconds, testScheduler.timeSource)
        val started = mutableListOf<Long>()
        repeat(3) { spacing.next { started += testScheduler.currentTime } }
        advanceTimeBy(5.seconds)
        spacing.next { started += testScheduler.currentTime }
        // A second apart, and none waits when the last was long enough ago.
        assertEquals(listOf(0L, 1000L, 2000L, 7000L), started)
    }
}
