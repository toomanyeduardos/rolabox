package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkError
import dagger.Lazy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The audio check against a local server (podcasts/12), which answers as a publisher's does: the range, a refusal for
 * a paid episode, a redirect through a tracker, and nothing at all.
 */
class OkHttpAudioNetworkDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.close()

    private fun TestScope.source() =
        OkHttpAudioNetworkDataSource(Lazy { testClient() }, StandardTestDispatcher(testScheduler))

    private fun url(path: String = "/episode.mp3") = server.url(path).toString()

    @Test
    fun theRange_isAskedFor_andA206_isThere() = runTest {
        server.enqueue(answer(206, "ID3", "Content-Range" to "bytes 0-1023/27000000"))

        assertEquals(Unit.right(), source().check(url()))
        assertEquals("bytes=0-1023", server.takeRequest().headers["Range"])
    }

    @Test
    fun aServerThatIgnoresTheRange_isThere() = runTest {
        server.enqueue(answer(200, "ID3"))

        assertEquals(Unit.right(), source().check(url()))
    }

    @Test
    fun a401_403Or410_isRefused() = runTest {
        listOf(401, 403, 410).forEach { server.enqueue(answer(it)) }

        repeat(3) { assertEquals(AudioNetworkError.Refused.left(), source().check(url())) }
    }

    // Not a refusal: the player says what it finds, as it did before the check.
    @Test
    fun anotherHttpError_isNotARefusal() = runTest {
        server.enqueue(answer(404))
        server.enqueue(answer(500))

        repeat(2) { assertEquals(Unit.right(), source().check(url())) }
    }

    @Test
    fun aRedirect_isFollowed_withTheRange() = runTest {
        server.enqueue(answer(302, "", "Location" to "/cdn/episode.mp3"))
        server.enqueue(answer(403))

        assertEquals(AudioNetworkError.Refused.left(), source().check(url()))
        server.takeRequest()
        assertEquals("bytes=0-1023", server.takeRequest().headers["Range"])
    }

    @Test
    fun aTimeout_isUnreachable() = runTest {
        server.enqueue(tooSlow())

        assertEquals(AudioNetworkError.Unreachable.left(), source().check(url()))
    }

    @Test
    fun aServerThatIsntThere_isUnreachable() = runTest {
        val gone = url()
        server.close()

        assertEquals(AudioNetworkError.Unreachable.left(), source().check(gone))
    }

    @Test
    fun aUrlThatIsntOne_isNotARefusal() = runTest {
        assertEquals(Unit.right(), source().check("not a url"))
    }
}
