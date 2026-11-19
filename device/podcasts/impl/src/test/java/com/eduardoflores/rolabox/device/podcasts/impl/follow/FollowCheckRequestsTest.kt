package com.eduardoflores.rolabox.device.podcasts.impl.follow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FollowCheckRequestsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = FakeFollowCheckScheduler()
    private var now: Instant = Instant.ofEpochSecond(1_000)
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    @Before
    fun initWorkManager() {
        val configuration = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @Test
    fun theCheck_isRequestedAtProcessStart_andAgainInTheFrontOnlyAfterAnHour() {
        val requests = FollowCheckRequests(scheduler, clock)

        requests.onProcessStart()
        // The first time the app comes to the front is the same open.
        requests.onForeground()
        now = now.plus(FOLLOW_CHECK_AGAIN_AFTER)
        requests.onForeground()
        now = now.plusSeconds(1)
        requests.onForeground()

        assertEquals(2, scheduler.requested.size)
    }

    @Test
    fun theCheck_isEnqueuedOncePerOpen_withANetworkConstraint() {
        val workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerFollowCheckScheduler(context)

        scheduler.checkAll()
        scheduler.checkAll()

        val work = workManager.getWorkInfosForUniqueWork(PODCAST_FOLLOW_CHECK_WORK).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
    }

    @Test
    fun theReadOfOneShow_isWorkOfItsOwn_soARunningCheckDoesntDropIt() {
        val workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerFollowCheckScheduler(context)

        scheduler.checkAll()
        scheduler.checkShow(DirectoryId.itunes(1))

        val show = workManager.getWorkInfosForUniqueWork(followCheckWorkOf(DirectoryId.itunes(1))).get()
        assertEquals("podcast-follow-check-itunes-1", followCheckWorkOf(DirectoryId.itunes(1)))
        assertEquals(1, show.size)
        assertEquals(NetworkType.CONNECTED, show.single().constraints.requiredNetworkType)
    }
}
