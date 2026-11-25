# ADR-043: A show's episodes are in the order its feed asks for, the user can set it per show, synced in a document of its own, and the list opens on the next episode

- **Status:** Accepted
- **Date:** 2026-11-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-031](031-podcasts.md) (in part: "newest first" in "The database" (What a show lists) and in
  "Search" (A show opened from search), and the database's table, which gains the feed's kind in Shows and a table
  of the user's choices)

## Context

[ADR-031](031-podcasts.md) lists a show's episodes newest first, always: the feed parser sorts them that way, and
so does the database's query of a show's episodes. That is right for a news show, such as *The Daily*, where only
the latest episode matters. It is wrong for a story told in order, such as a limited series or an audio drama,
which is meant to be heard from episode 1. In field testing ([card](https://trello.com/c/Rg6YDTA7)), such shows
opened on their last episode.

Apple's podcast spec has a channel tag for this: `<itunes:type>`, `episodic` or `serial`. Apple Podcasts lists a
`serial` show oldest first, so the publishers of story shows set it. The tag is optional, and some feeds get it
wrong: a hosting tool may write `episodic` for every show.

Order alone doesn't solve the problem. On the device the list is moved one row per turn of the wheel
([ADR-018](018-device-navigation.md)). A serial show listed oldest first, with 40 episodes already heard, would put
the next episode 40 turns down. And a show's list opens on its first row today.

The user's choice should follow them to their other phones, as follows do (ADR-031, Syncing follows). But phones
on 1.0.2 already write the follow document, whole, with `set` and only its three fields: a field added to it would
be erased by any of them that touches that show's follow.

## Decision

We will list a show's episodes in the order its feed asks for, let the user set the order per show, sync that
choice in a document of its own, and open a show listed oldest first on the episode after the last one played.

### The order

- **Two orders:** `Newest` (newest first, as today) and `Oldest` (oldest first). In both, episodes with no date are
  last, in the feed's order.
- **The order in effect** is, in this priority: the user's choice for the show, if one is set; else `Oldest` for a
  feed whose `<itunes:type>` is `serial`; else `Newest`. A feed with no tag, `episodic` or any other value is
  `Newest`. Nothing is guessed from titles or episode numbers: a wrong guess is worse than today's default.
- **The feed's kind is kept with the show.** The parser reads `itunes:type` from the channel, and `Feed` carries
  it as `ShowKind` (`Episodic`, `Serial`), `null` when the feed says none or a value Apple's spec hasn't. It is
  written to the show's row whenever the feed is read whole: by a follow check, and by Follow On from search. Playing
  an episode from search writes it when that adds the show, and keeps a show already there as it is (ADR-031).
- **The order is applied where the episodes are read,** in the query of a show's episodes, not by the parser,
  which still lists a feed newest first, the undated last, as ADR-031 says. `Show` in `:device:podcasts:api`
  carries the order in effect, `episodeOrder`, and the one its feed asks for, `feedEpisodeOrder`, so a screen
  doesn't decide either.
- **A show from search** (frame 32) is in the order its feed asks for, from the feed in memory
  ([ADR-042](042-found-feeds-kept-in-memory.md)): `ShowFeed` carries the feed's kind, and its screen lists the
  feed's episodes in that order. It has no Order row: the choice belongs to a show of the library. A show from
  search that is in the library with Follow Off uses the library's order in effect, with the user's choice.

### The user's choice

- **An Order row on every show screen of the library,** under Follow, followed or not: `Order` with the value
  `Newest` or `Oldest`, the order in effect. Center flips it in place, as on Follow.
- **The choice is stored only when the user flips it.** Opening a show stores nothing, so a show whose feed adds
  `itunes:type` later follows it.
- **Flipping back to the feed's order clears the choice.** If the value the user flips to is the one the feed asks
  for, the choice is cleared, and the show follows its feed again. There is no third value, such as Auto: on a
  wheel, it would be one more press on every flip.
- **Rows are in the order in effect,** so the list re-sorts at once, and the highlight stays on the row it was on
  (the Order row).

### Syncing the choice

- **One document per show with a choice,** `users/{uid}/podcastEpisodeOrders/{showKey}`, where `showKey` is the
  show's directory id, as for follows, with only `value` and `updatedAt`
  ([ADR-010](010-firestore-security-rules.md), rule 4). `value` is `Newest`, `Oldest` or `Feed`. A document exists
  only for a show whose Order row the user has flipped.
- **Last-write-wins per show,** on `updatedAt` ([ADR-011](011-preferences-sync.md), rule 2), the document whole.
  A cleared choice is written as `Feed`, not deleted, so it wins over an older choice from another phone, as an
  unfollow does for follows. Choices are never deleted by sync.
- **The follow document doesn't change.** Its fields, its rules, `SyncedFollows` and `FollowsSyncer` stay as
  ADR-031 says, so a phone on 1.0.2 writes follows as it does today, and never reads or writes a choice.
- **A choice works for any show of the library,** followed or only played, with no follow needed. It doesn't add a
  show to another phone's library: a show joins it only by being followed (ADR-031). A choice for a show this phone
  doesn't have waits, and is applied once the show joins the library.
- **Locally,** the user's choices are a table of their own, `EpisodeOrders`, with the directory id, the value and
  `updatedAt`. It is apart from Shows, as Follows is, since a choice can arrive by sync before its show is in the
  library. The Shows table gains the feed's kind. Each change has a migration tested against the exported schema
  ([ADR-024](024-library-index.md), rule 10). Android backup keeps the table with the rest of the podcasts'
  database ([ADR-036](036-android-backup.md)).
- **The `Syncer` of choices** is `EpisodeOrdersSyncer`, in `:device:podcasts:impl`, added to the set of
  `:common:sync:impl` ([ADR-022](022-data-source-sub-parts.md), rule 5). It runs only when the follows' does
  ([ADR-011](011-preferences-sync.md), rule 8). Choices made without an account are kept, and merged on sign-in,
  show by show.
- **The model both sides speak** is `SyncedEpisodeOrders`, a map from directory id to a `SyncedValue` of the
  choice, in `:device:podcasts:api` ([ADR-022](022-data-source-sub-parts.md), rule 7). The Firestore mapping and
  transaction are in `:device:podcasts:network:impl`, with Firestore from `:common:firestore`
  ([ADR-033](033-shared-firestore-module.md)).
- **The security rules** allow that path for its owner only, with `hasOnly` and `hasAll` on `value` and
  `updatedAt`, a `showKey` of the form `itunes-` and digits, `value` one of the three, and `updatedAt` checked as
  ADR-011's rule 6 says. An update may not move `updatedAt` back. Each case has its emulator test
  ([ADR-010](010-firestore-security-rules.md), rule 3). The rules are deployed before the version that writes the
  path: until then its writes are refused, and only the choices fail to sync, logged
  ([ADR-029](029-sync-failures-are-logged.md)).
- **Deleting an account** deletes the choices too: `EpisodeOrdersSyncer` deletes its documents in the cloud, as
  every `Syncer` does ([ADR-037](037-deleting-an-account.md)).

### Where the list opens

- **A show listed `Oldest` opens on the episode after the last played one:** the first episode, in list order,
  after the last episode that is played. That is the one the user is partly through, or the next one to start. If
  no episode is played, it opens on the first episode. If the last episode is played, it opens on the last
  episode.
- **A show listed `Newest` opens as today,** on its first row.
- **This is only where the highlight starts,** the first time the entry is shown. The highlight is still the
  entry's saved state ([ADR-018](018-device-navigation.md), rule 7): coming back to the show, also after process
  death, restores the row the user left. The ViewModel gives the starting episode, from the played state it
  already reads, and the screen its row. The host's `ListHighlight` moves to it once, with the first rows that are
  known, and saves that it did with the highlight, so a list that comes back, or is listed again after Order is
  flipped, isn't moved.
- **Nothing is hidden.** The played episodes stay above, with no dot, so an earlier one can be played again.

## Alternatives considered

- **Only a toggle, newest first by default.** Simplest, but every serial show would be wrong until the user found
  the toggle, while most publishers of story shows already say it in the feed.
- **Only the feed's tag, with no toggle.** Nothing to design or sync, but a feed that has no tag, or the wrong
  one, could never be fixed, and a user caught up on a serial show couldn't put the newest on top.
- **Guess serial shows from episode numbers or titles** ("Part 1", `itunes:episode`). It would catch untagged
  shows, but many episodic shows number their episodes too, and a wrong guess would hide the newest episode of a
  news show at the bottom.
- **Hide played episodes, or a filter "Unplayed only".** It solves the 40 turns, but an episode heard before could
  only be found again by turning the filter off, and the list's length would change as episodes are played.
- **An "Up next" row at the top of the list.** One press to the next episode, in either order. But it is one more
  row on a small display, it repeats a row of the list, and the starting highlight gets the same result with none.
- **A third value, Auto, to go back to the feed.** Explicit, but one more press on every flip, and a word that
  needs explaining. Clearing the choice when it matches the feed gets back to the feed with no new value.
- **Keep the choice on the phone only.** No new collection, rules or `Syncer`, and Android backup already keeps the
  podcasts' database ([ADR-036](036-android-backup.md)). But a choice made on one phone would be missing on the
  user's other phones, while their follows sync.
- **A field of the follow document,** `order: { value, updatedAt }`. One collection for everything about a show,
  but three problems. Phones on 1.0.2 write the follow whole, with `set`, so any of them would erase the choice,
  and the rules couldn't stop it, since the field must stay optional for them. A follow is one synced value today,
  and its rules check `updatedAt` on the whole document, so the follow and the choice would have to be merged
  field by field, a new sync of the data whose mistakes would turn Follow On or Off on the user's other phones. And
  a show that was only played has no follow, so one would be created with `followed: false` to hold the choice,
  giving the record that means "unfollowed" a second meaning.

## Consequences

- A serial show opens oldest first, on the next episode, with no setting, when its feed says it is serial.
- A user can fix a feed's missing or wrong tag, per show, once, on every phone with this version.
- A phone on 1.0.2 lists every show newest first, as before, and leaves the choices alone.
- Every show screen of the library has one more row before its episodes, so an episode is one row lower.
- Follows don't change, so a mistake in syncing the choices can lose a choice, but never changes a follow.
- A second synced collection of the podcasts part, with its rules, their tests, a `Syncer` and a table. Its
  documents are the shows the user flipped, tens at most.
- The Shows table gains a column, and the database a table, with migrations.
- The privacy policy and the account deletion page name the choices among the synced data
  ([ADR-038](038-public-web-pages.md)).
- The store screenshots of a show's screen show the Order row the next time they are rendered
  ([ADR-039](039-store-and-readme-screenshots.md)): they are rendered on demand, apart from the goldens.
- A show listed newest first still opens on its first row, as before. Only oldest first opens elsewhere.

## Rules

1. `[convention]` A show's episodes are listed in its order in effect: the user's choice, else `Oldest` for a
   feed whose `itunes:type` is `serial`, else `Newest`. Episodes with no date are last. The order is applied where
   the episodes are read, and `Show.episodeOrder` carries it.
2. `[convention]` The user's choice is stored only when the user flips the Order row, and is cleared, as `Feed`,
   when it flips to the feed's order.
3. `[convention]` The choices sync one document per show, `users/{uid}/podcastEpisodeOrders/{showKey}`,
   last-write-wins on `updatedAt`, and are never deleted by sync. The follow document is not changed for them.
4. `[enforced]` The security rules of `users/{uid}/podcastEpisodeOrders/{showKey}` allow its owner only, with
   `hasOnly` and `hasAll` on `value` and `updatedAt`, `value` one of `Newest`, `Oldest` and `Feed`, don't let an
   update move `updatedAt` back, and have emulator tests for each case.
5. `[convention]` A show listed `Oldest` opens on the episode after the last played one, the first time its entry
   is shown. The highlight is otherwise the entry's saved state (ADR-018, rule 7).

**Conformance.** Rule 4 is `firebase/test/firestore.rules.test.mjs`, which the `firestore-rules` job in CI runs
against the emulator. The rest are reviewed, and covered by tests: rule 1 by the parser's tests in
`:device:podcasts:network:impl` (a feed `serial`, `episodic`, with no tag and with another value) and the contract
tests of the episodes' query in `:device:podcasts:local:impl`; rule 2 by the tests of `PodcastsRepository` in
`:device:podcasts:impl`; rule 3 by `EpisodeOrdersSyncerTest` (a newer choice on either side, a cleared choice,
choices made without an account, and deleting them) and the contract tests of the choices in
`:device:podcasts:local:impl`, with `FollowsSyncerTest` unchanged; rule 5 by `EpisodeOrderingTest` (nothing
played, 40 of 60 played, one partly played, all played, and `Newest`), `ShowViewModelTest` and `ListHighlightTest`
(the highlight starts once, and keeps that it did across process death).
