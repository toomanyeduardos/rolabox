# ADR-042: The feeds of shows opened from search are kept in memory, the last 5 for 5 minutes, as searches are

- **Status:** Accepted
- **Date:** 2026-11-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-031](031-podcasts.md) (in part: "The directory and a show from search are
  read from the network", where `show(show)` reads the network on each call and its answers are
  not kept)

## Context

[ADR-031](031-podcasts.md) reads a show opened from search, and an episode opened from search,
from the show's RSS feed, on the phone. `PodcastDirectory.show(show)` reads the network on each
call, and its answer isn't kept. Rule 4 keeps it out of the database, and the keys of those screens
carry identifiers only ([ADR-012](012-navigation.md), rule 2): the episode's key has its show, its
GUID and its enclosure URL, and its screen reads the rest from the feed.

In field testing on a slow network ([bug card](https://trello.com/c/2wOPpwgP)), a show with a long
feed, such as NPR's *Tiny Desk Concerts*, took many seconds to open from search, with a blank
display. Picking one of its episodes then read the same whole feed again before the Episode screen
showed anything, though the show's screen had just read it. And coming back to the show's screen
after Now Playing read it a third time, while the episode's download shared the same slow network.

Searches already work another way: the answers to the last 20 texts are kept in memory (ADR-031,
The directory), so deleting a letter doesn't ask again.

## Decision

We will keep the feeds the directory reads in memory, as its searches are.

- **What is kept:** a feed that was read, by its URL: its episodes, its owner and where it moved.
  A show found through one of its episodes has no creator, and found by itself has one, but it is
  one feed: both get the same kept feed, each with its own show and creator, as `show(show)` gives
  them. A failed read isn't kept: asking again reads the feed.
- **How many:** the last 5 shows asked for, in `DefaultPodcastDirectory`, with the same
  least-recently-used memory as searches. A feed can hold thousands of episodes with their
  descriptions, so fewer feeds than searches are kept.
- **For how long:** 5 minutes from when the feed was read, measured on the monotonic clock
  (`TimeSource`), and never past the process. After 5 minutes `show(show)` reads the feed again and
  keeps the new one. That covers opening a show, then an episode, then coming back from Now
  Playing, while a new episode shows up within minutes. Nothing is written to the database, to a file or to
  the saved state, so ADR-031's rule 4 and ADR-012's rule 2 hold. After process death a screen
  reads its feed again, as it does today.
- **Nothing else changes.** A show still becomes the library's only when one of its episodes is
  played or it is followed, from the feed the screen shows. The library's follow checks read their
  feeds through the repository, with validators, not through the directory, and aren't kept here.

## Alternatives considered

- **Carry the episode in its key** (title, date, length and description). The Episode screen
  would draw at once, but a key would carry a model, which ADR-012's rule 2 forbids, and the saved
  state would grow with every description. It also doesn't help the show's screen when it comes
  back.
- **Keep the found feed in the database.** It would outlive the process, but it is the cache that
  ADR-031's rule 4 rules out: a show from search would be in the database without the user playing
  or following it.
- **Keep reading the feed each time, and only show that it is loading.** Simpler, and the display
  is no longer blank, but the user still waits for the same feed twice, and the second read
  competes with the episode's download.
- **Stream a feed's episodes to the screen as they are parsed.** It would help the first open, the
  one this ADR doesn't speed up, but it changes the network part's API and the parser for every
  feed. It can come later on its own.

## Consequences

- The Episode screen of an episode picked in a show from search finds its feed at once. It waits
  only for the audio check (ADR-031, Search).
- Coming back to a show from search doesn't read its feed again in the same process.
- A feed kept in memory can be up to 5 minutes older than the publisher's. For browsing, that is
  acceptable.
- Up to 5 parsed feeds stay in memory. A long feed is a few megabytes of text at most.

## Rules

1. `[convention]` `PodcastDirectory.show` answers from the last 5 feeds it read, by feed URL, for 5
   minutes after each was read, and reads the network otherwise. Only a successful read is kept.
2. `[convention]` A feed from search is kept only in the directory's memory: never in the
   database, a file or a navigation key (ADR-031, rule 4; ADR-012, rule 2).

**Conformance.** Reviewed, and covered by `DefaultPodcastDirectoryTest`: a show asked for again
isn't read again, it is read again after 5 minutes on a test clock, a show found by an episode and
by itself share one read, a failed read isn't kept, and the oldest of more than 5 is read again.
