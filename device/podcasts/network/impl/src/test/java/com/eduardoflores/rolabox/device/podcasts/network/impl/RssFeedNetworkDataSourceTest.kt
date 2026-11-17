package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.left
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import dagger.Lazy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ETAG = "\"abc123\""
private const val LAST_MODIFIED = "Tue, 27 Oct 2026 12:27:32 GMT"

/** Feeds served from a local server, which redirects, stalls and answers 304 as a real one does. */
@RunWith(RobolectricTestRunner::class)
class RssFeedNetworkDataSourceTest {
    private val server = MockWebServer()
    private val feed = recorded("feeds/emperors-of-rome.xml")

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.close()

    private fun TestScope.source() =
        RssFeedNetworkDataSource(Lazy { testClient() }, StandardTestDispatcher(testScheduler))

    private fun url(path: String = "/feed.xml") = server.url(path).toString()

    @Test
    fun aFeed_isRead_withTheValidatorsOfItsAnswer() = runTest {
        server.enqueue(answer(200, feed, "ETag" to ETAG, "Last-Modified" to LAST_MODIFIED))

        val read = source().fetch(url()).getOrNull() as FeedAnswer.Read

        assertEquals(3, read.feed.episodes.size)
        assertEquals(FeedValidators(ETAG, LAST_MODIFIED), read.validators)
        assertNull(read.movedTo)
        // Nothing to compare with was sent.
        val request = server.takeRequest()
        assertNull(request.headers["If-None-Match"])
        assertNull(request.headers["If-Modified-Since"])
    }

    @Test
    fun theValidators_areSentBack_andA304_isNothingNew() = runTest {
        server.enqueue(answer(304))

        val answer = source().fetch(url(), FeedValidators(ETAG, LAST_MODIFIED))

        assertEquals(FeedAnswer.NotModified(movedTo = null), answer.getOrNull())
        val request = server.takeRequest()
        assertEquals(ETAG, request.headers["If-None-Match"])
        assertEquals(LAST_MODIFIED, request.headers["If-Modified-Since"])
    }

    @Test
    fun aPermanentRedirect_movesTheFeed() = runTest {
        server.enqueue(answer(301, "", "Location" to "/moved.xml"))
        server.enqueue(answer(308, "", "Location" to "/moved-again.xml"))
        server.enqueue(answer(200, feed))

        val read = source().fetch(url()).getOrNull() as FeedAnswer.Read

        assertEquals(url("/moved-again.xml"), read.movedTo)
        assertEquals(3, read.feed.episodes.size)
    }

    @Test
    fun aTemporaryRedirect_isFollowed_withoutMovingTheFeed() = runTest {
        server.enqueue(answer(302, "", "Location" to "/today.xml"))
        server.enqueue(answer(200, feed))

        val read = source().fetch(url()).getOrNull() as FeedAnswer.Read

        assertNull(read.movedTo)
    }

    @Test
    fun aTemporaryRedirectAfterAPermanentOne_movesTheFeedOnlyAsFarAsThePermanentOne() = runTest {
        server.enqueue(answer(301, "", "Location" to "/moved.xml"))
        server.enqueue(answer(307, "", "Location" to "/tracking.xml"))
        server.enqueue(answer(304))

        val answer = source().fetch(url(), FeedValidators(ETAG, null))

        assertEquals(FeedAnswer.NotModified(movedTo = url("/moved.xml")), answer.getOrNull())
    }

    @Test
    fun theFeedsOwnNewUrl_movesIt_overARedirect() = runTest {
        server.enqueue(answer(301, "", "Location" to "/moved.xml"))
        server.enqueue(answer(200, recorded("feeds/edge-cases.xml")))

        val read = source().fetch(url()).getOrNull() as FeedAnswer.Read

        assertEquals("https://example.com/new-feed.xml", read.movedTo)
    }

    @Test
    fun aFeedThatIsMalformedOrEndsEarly_isUnreadable_andNothingOfItIsKept() = runTest {
        server.enqueue(answer(200, recorded("feeds/ends-early.xml")))
        server.enqueue(answer(200, "<html><body>Moved to our new site!</body></html>"))
        server.enqueue(answer(200, recorded("feeds/atom.xml")))

        repeat(3) { assertEquals(FeedNetworkError.Unreadable.left(), source().fetch(url())) }
    }

    @Test
    fun anHttpError_isUnreadable() = runTest {
        server.enqueue(answer(404))
        server.enqueue(answer(500))

        repeat(2) { assertEquals(FeedNetworkError.Unreadable.left(), source().fetch(url())) }
    }

    @Test
    fun aUrlThatIsntOne_isUnreadable() = runTest {
        assertEquals(FeedNetworkError.Unreadable.left(), source().fetch("not a url"))
    }

    @Test
    fun aTimeout_isUnreachable() = runTest {
        server.enqueue(tooSlow())

        assertEquals(FeedNetworkError.Unreachable.left(), source().fetch(url()))
    }
}
