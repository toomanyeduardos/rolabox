package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** What a list is of, for its title. A [name] the library doesn't know is `null`, and the title says so. */
internal sealed interface ListTitle {
    val name: String?

    data class Artist(override val name: String?) : ListTitle

    data class Album(override val name: String?) : ListTitle
}

/** The words of the title. `null`, for a title not read yet or that can't be, leaves the header empty. */
@Composable
internal fun ListTitle?.label(): String? = when (this) {
    null -> null
    is ListTitle.Artist -> name ?: stringResource(R.string.music_unknown_artist)
    is ListTitle.Album -> name ?: stringResource(R.string.music_unknown_album)
}
