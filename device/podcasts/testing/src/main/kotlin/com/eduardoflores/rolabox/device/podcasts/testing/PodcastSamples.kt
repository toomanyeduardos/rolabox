package com.eduardoflores.rolabox.device.podcasts.testing

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The podcasts of the designs, frames 22 to 32 of `player-designs/device`: The Daily of NY Times, with an episode in
 * every played and download state, and the made-up owners and shows around it. One show has no owner.
 */
object PodcastSamples {
    val nyTimes = Owner(OwnerId("ny times"), "NY Times")
    val fieldNotesMedia = Owner(OwnerId("field notes media"), "Field Notes Media")
    val harborAudio = Owner(OwnerId("harbor audio"), "Harbor Audio")
    val smallRooms = Owner(OwnerId("small rooms"), "Small Rooms")
    val weeklyBench = Owner(OwnerId("weekly bench"), "Weekly Bench")
    val noOwner = Owner(OwnerId(""), null)

    /** A to Z, and the owner with no name last, as the Podcasts list sorts them (frame 22). */
    val owners: List<Owner> = listOf(fieldNotesMedia, harborAudio, nyTimes, smallRooms, weeklyBench, noOwner)

    val theDaily = show(1, "The Daily", nyTimes, followed = true)
    val hardFork = show(2, "Hard Fork", nyTimes)
    val theEzraKleinShow = show(3, "The Ezra Klein Show", nyTimes)
    val dailyDoseOfDesign = show(4, "Daily Dose of Design", fieldNotesMedia, followed = true)
    val theDailyBrief = show(5, "The Daily Brief", harborAudio)
    val roomTone = show(6, "Room Tone", smallRooms, followed = true)
    val benchNotes = show(7, "Bench Notes", weeklyBench)
    val unsignedShow = show(8, "Notes Without a Name", noOwner)

    /** Every show, its owner's A to Z, as the Owner list sorts them (frame 24). */
    val shows: List<Show> = listOf(
        dailyDoseOfDesign,
        theDailyBrief,
        hardFork,
        theDaily,
        theEzraKleinShow,
        roomTone,
        benchNotes,
        unsignedShow,
    )

    /** The Daily's episodes, newest first (frame 25). */
    val theDailyEpisodes: List<Episode> = listOf(
        episode(1, "A Heat Wave Over the North Atlantic", day = 11),
        episode(2, "Inside the Race for a Cheaper Battery", day = 10, download = DownloadState.Downloading(0.38f)),
        episode(3, "The Town That Saved Its Pharmacy", day = 9, download = DownloadState.Downloaded),
        episode(
            4,
            "What Happened at the Port Strike, and What Comes Next",
            day = 8,
            position = 12.minutes,
            download = DownloadState.Downloaded,
            description = "Dockworkers on both coasts walked off the job for nine days. We look at what the strike " +
                "won, what it cost the ports, and why the next contract talks may be harder.",
        ),
        episode(5, "A Quiet Election in a Loud Year", day = 7, played = true, download = DownloadState.Downloaded),
        episode(6, "The Return of the Night Train", day = 6, played = true, download = DownloadState.Downloaded),
        episode(7, "Who Owns the Moon?", day = 5, played = true, download = DownloadState.Downloaded),
    )

    val unplayed: Episode = theDailyEpisodes[0]
    val downloading: Episode = theDailyEpisodes[1]
    val downloaded: Episode = theDailyEpisodes[2]
    val partlyPlayed: Episode = theDailyEpisodes[3]
    val played: Episode = theDailyEpisodes[4]

    private fun show(id: Long, title: String, owner: Owner, followed: Boolean = false) = Show(
        id = ShowId(id),
        directoryId = DirectoryId.itunes(id),
        title = title,
        ownerId = owner.id,
        ownerName = owner.name,
        isFollowed = followed,
    )

    @Suppress("LongParameterList") // One parameter per state an episode can be in.
    private fun episode(
        id: Long,
        title: String,
        day: Int,
        length: Duration = 32.minutes,
        position: Duration = Duration.ZERO,
        played: Boolean = false,
        download: DownloadState = DownloadState.NotDownloaded,
        description: String? = null,
    ) = Episode(
        id = EpisodeId(id),
        showId = theDaily.id,
        title = title,
        publishedAt = LocalDate.of(SAMPLE_YEAR, SAMPLE_MONTH, day).atStartOfDay().toInstant(ZoneOffset.UTC),
        length = length,
        description = description,
        audioUrl = "https://example.com/the-daily/$id.mp3",
        audioSizeBytes = null,
        playedState = when {
            played -> PlayedState.Played
            position.isPositive() -> PlayedState.PartlyPlayed
            else -> PlayedState.Unplayed
        },
        position = if (played) length else position,
        download = download,
    )

    private const val SAMPLE_YEAR = 2026
    private const val SAMPLE_MONTH = 11
}
