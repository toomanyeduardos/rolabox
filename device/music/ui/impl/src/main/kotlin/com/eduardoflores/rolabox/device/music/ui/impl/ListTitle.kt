package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** What a list is of, for its title. A name the library doesn't know is `null`, and the title says so. */
internal sealed interface ListTitle {
    data class Artist(val name: String?, val isVarious: Boolean = false) : ListTitle

    data class Album(val name: String?) : ListTitle

    /** A genre, whose name is `null` for Unknown Genre. */
    data class Genre(val name: String?) : ListTitle

    /** Every song of the library, whose title is the Music menu's row. */
    data object AllSongs : ListTitle

    /** Every artist of the library, whose title is the Music menu's row. */
    data object AllArtists : ListTitle
}

/** The words of the title. `null`, for a title not read yet or that can't be, leaves the header empty. */
@Composable
internal fun ListTitle?.label(): String? = when (this) {
    null -> null

    is ListTitle.Artist -> name ?: stringResource(
        if (isVarious) R.string.music_various_artists else R.string.music_unknown_artist,
    )

    is ListTitle.Album -> name ?: stringResource(R.string.music_unknown_album)

    is ListTitle.Genre -> name ?: stringResource(R.string.music_unknown_genre)

    ListTitle.AllSongs -> stringResource(R.string.music_songs_title)

    ListTitle.AllArtists -> stringResource(R.string.music_artists_title)
}
