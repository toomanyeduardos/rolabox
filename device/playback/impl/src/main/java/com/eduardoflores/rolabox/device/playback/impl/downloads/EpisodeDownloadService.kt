package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.eduardoflores.rolabox.common.designsystem.R as DesignR
import com.eduardoflores.rolabox.device.playback.impl.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The foreground service of type `dataSync` that holds the process while an episode downloads (ADR-031, Downloads),
 * with the manager of [MediaDownloads]. Its notification shows the progress where notifications are allowed, and
 * `POST_NOTIFICATIONS` is never asked for (ADR-023, rule 8). It has no `Scheduler`: starting a foreground service from
 * the background is restricted from Android 12, so a download stopped by a kill goes on at the next start of the app.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
internal class EpisodeDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.playback_downloads_channel,
        0,
    ) {
    @Inject
    lateinit var downloads: MediaDownloads

    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = downloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        notifications.buildProgressNotification(
            this,
            DesignR.drawable.ds_ic_notification,
            null,
            null,
            downloads,
            notMetRequirements,
        )
}

/**
 * Starts [EpisodeDownloadService] in the foreground. It runs only when a download was just added by playing it, so the
 * app is in front, or when the app comes to the front with a download unfinished. A start the system refuses leaves the
 * download for the next start of the app.
 */
@OptIn(UnstableApi::class)
internal class StartEpisodeDownloadService @Inject constructor(@ApplicationContext private val context: Context) :
    DownloadServiceStarter {
    override fun start(manager: DownloadManager) {
        try {
            DownloadService.startForeground(context, EpisodeDownloadService::class.java)
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is one: the app went to the background meanwhile.
            Log.w(TAG, "The download service couldn't start now", refused)
        }
    }
}

// Apart from the media notification's, Media3's DEFAULT_NOTIFICATION_ID, 1001.
private const val NOTIFICATION_ID = 1002
private const val CHANNEL_ID = "episode_downloads"
private const val TAG = "EpisodeDownloads"
