package com.eduardoflores.rolabox.device.podcasts.network.impl

import javax.inject.Qualifier

/** The podcasts' HTTP client, shared by the directory and the feeds. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PodcastsClient
