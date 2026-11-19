package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A directory that tests fill in, for the screens: set the [results] of each text and the [feeds] of each show, or
 * make every call fail with [searchError] and [showError]. A text with no results set answers
 * [DirectoryResults.None], and a show with no feed set [PodcastError.FeedUnreadable]. To see a search waiting, set
 * [pending]: searches then wait for it to complete. The phone is [connected] until a test says it isn't.
 */
@Singleton
class FakePodcastDirectory @Inject constructor() : PodcastDirectory {
    var results: Map<String, DirectoryResults> = emptyMap()
    var feeds: Map<DirectoryShow, ShowFeed> = emptyMap()
    var searchError: PodcastError? = null
    var showError: PodcastError? = null
    var pending: CompletableDeferred<Unit>? = null
    val connected = MutableStateFlow(true)

    /** The texts searched, in order, and the searches that finished: a cancelled search isn't in [answered]. */
    val searched = mutableListOf<String>()
    val answered = mutableListOf<String>()

    /** The shows opened, in order. */
    val opened = mutableListOf<DirectoryShow>()

    override suspend fun search(text: String): Either<PodcastError, DirectoryResults> {
        searched += text
        pending?.await()
        answered += text
        return searchError?.left() ?: (results[text] ?: DirectoryResults.None).right()
    }

    override fun observeConnected(): Flow<Boolean> = connected

    override suspend fun show(show: DirectoryShow): Either<PodcastError, ShowFeed> {
        opened += show
        return showError?.left() ?: feeds[show]?.right() ?: PodcastError.FeedUnreadable.left()
    }
}
