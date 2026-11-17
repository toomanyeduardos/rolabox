package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.runtime.Composable

// A followed show in the library (frame 25): each dot, the arrow and a percentage.
private val Episodes = listOf(
    DeviceListRow("Follow", value = "On"),
    DeviceListRow(
        "A Heat Wave Over the North Atlantic",
        dot = DeviceListDot.Unplayed,
        download = DeviceListDownload.NotDownloaded,
    ),
    DeviceListRow(
        "Inside the Race for a Cheaper Battery",
        dot = DeviceListDot.Unplayed,
        download = DeviceListDownload.Downloading(percent = 38),
    ),
    DeviceListRow("The Town That Saved Its Pharmacy", dot = DeviceListDot.Unplayed),
    DeviceListRow("What Happened at the Port Strike, and What Comes Next", dot = DeviceListDot.PartlyPlayed),
    DeviceListRow("A Quiet Election in a Loud Year"),
    DeviceListRow("The Return of the Night Train"),
    DeviceListRow("Who Owns the Moon?"),
)

// Search's results (frame 31): section labels, and the shows' creators as secondary values.
private val SearchResults = listOf(
    DeviceListSection("Shows"),
    DeviceListRow("The Daily", secondaryValue = "NY Times"),
    DeviceListRow("The Daily Brief", secondaryValue = "Harbor Audio"),
    DeviceListRow("Daily Dose of Design", secondaryValue = "Field Notes Media"),
    DeviceListSection("Episodes"),
    DeviceListRow("Why the Daily Commute Changed", opensSubmenu = true),
    DeviceListRow("A Daily Habit, Ten Years Later", opensSubmenu = true),
)

// A show opened from search (frame 32): every episode with the arrow, and no dots.
private val SearchShow = listOf(
    DeviceListRow("Follow", value = "Off"),
    DeviceListRow("A Heat Wave Over the North Atlantic", download = DeviceListDownload.NotDownloaded),
    DeviceListRow("Inside the Race for a Cheaper Battery", download = DeviceListDownload.NotDownloaded),
    DeviceListRow("The Town That Saved Its Pharmacy", download = DeviceListDownload.NotDownloaded),
)

/** The highlight on the half dot, whose long title starts at its first word. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodesPreview() = DeviceListPreview("The Daily", Episodes, highlightedIndex = 4)

/** The highlight on a full dot and a percentage. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodeDownloadingPreview() = DeviceListPreview("The Daily", Episodes, highlightedIndex = 2)

/** The highlight on a full dot and the arrow. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodeNotDownloadedPreview() = DeviceListPreview("The Daily", Episodes, highlightedIndex = 1)

/** The highlight on an arrow, with no dot. */
@PreviewLightDark
@Composable
private fun DeviceListSearchShowPreview() = DeviceListPreview("The Daily", SearchShow, highlightedIndex = 1)

/**
 * Section labels, and the highlight on a row with a secondary value, which gives way at 1.5x. The
 * highlight is on the first row of its section, so the section's label is in view above it.
 */
@PreviewLightDark
@Composable
private fun DeviceListSectionsPreview() = DeviceListPreview("Search", SearchResults, highlightedIndex = 1)

/** A message under the rows (frame 23). */
@PreviewLightDark
@Composable
private fun DeviceListMessagePreview() = DeviceListPreview(
    "Podcasts",
    listOf(DeviceListRow("Search", opensSubmenu = true)),
    highlightedIndex = 0,
    message = DeviceListMessage("No podcasts yet", "Select Search to find a show."),
)
