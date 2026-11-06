# ADR-027: Covers missing from the phone are downloaded from the Cover Art Archive, in WorkManager, and kept as the user's data

- **Status:** Accepted
- **Date:** 2026-11-03
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-11-06:** The match drops a store's edition notes from the end of a title ("(Bonus
  Track Version)", " - EP") and folds accents, on both sides ("How a match is judged"). A real
  library of iTunes purchases got no cover for 6 of 14 albums, and 4 of the 6 failed on the title
  alone: MusicBrainz names the album without the store's note. The rest of the match stays strict.
- **Revised 2026-11-06 (second):** An album the strict match misses gets a second, looser pass that
  compares the first part of the title, and picks the closest of several by ranked hints, so a wrong
  cover is no longer always worse than none ("How a match is judged"). The artist can also be one
  of the album's song artists. Of the same library, *Skin and Bones (Live)* and *Experience Hendrix*
  each had two equal release groups on MusicBrainz, and *Live at Radio City* is tagged "Dave
  Matthews" as its album artist and "Dave Matthews & Tim Reynolds" on every song, the artist
  MusicBrainz credits.

## Context

playback/06 showed the artwork that is on the phone ([ADR-026](026-artwork.md)): the picture in the
tags, then an image in the folder, through MediaStore's thumbnail of the song. It also found real
albums that have neither. An album bought on iTunes (Coldplay, *Live 2012*, `.m4a`) shows the
placeholder in Rolabox, while the Music app shows its cover. None of its 15 files has a `covr`
item, and its folder has no image: the Music app keeps the cover in its own library. The files do
carry Apple's ids, `plID` for the album among them. The epic of real music had "fetching artwork
from the internet" as a non-goal, and library/08 (decided 2026-11-03) takes it on.

What the decision has to fit:

- **What is on the phone comes first,** also when it appears after a cover was downloaded.
- **The user's files are never written** ([ADR-024](024-library-index.md), rule 1), so a downloaded
  cover is kept in the app's own storage.
- **Every library table is cache today,** dropped when its schema changes and rebuilt by a rescan
  (ADR-024, rule 10). A downloaded cover can't be rebuilt from the phone, and dropping the record
  would download everything again.
- **The scan doesn't use WorkManager** (ADR-024, rule 9), because the user waits for it. Nobody
  waits for a cover, the network can be missing, and the process can die halfway.
- **Repositories use the local side only, and only a `Syncer` uses both**
  ([ADR-022](022-data-source-sub-parts.md), rule 2). A download isn't sync: nothing goes up, and
  there is no account.
- **Offline mode makes no Firebase request** ([ADR-008](008-one-app-with-offline-mode.md), rule 6).
  This is the first request that isn't Firebase.
- **`ArtworkRepository` loads once** (ADR-026). A cover that arrives while Now Playing shows the
  album has to replace the placeholder there, on the notification and on the lock screen, without
  stopping the music.
- **There is no HTTP client or JSON parser in the stack.** WorkManager is, for sync.

**The sources, as checked on 2026-11-03.**

- **Apple's iTunes Search and Lookup API** answers by `plID`, an exact match for iTunes files. Its
  terms allow its artwork "only to promote store content", next to a "Download on iTunes" badge that
  links to the store, and removed on Apple's request. A cover in a player isn't that. And the lookup
  of *Live 2012* (`id=573891733`) returns no result in the US, GB, MX or DE store.
- **MusicBrainz and the Cover Art Archive.** Free, no key. MusicBrainz asks for about one request a
  second and a `User-Agent` that names the app and a contact. A search of release groups by title
  and artist found *Live 2012* (score 100, one result), and the archive's `front-500` of that release
  group is its cover. Release groups merge an album's editions, so one album is one answer.
- **Last.fm and Discogs** need a key, which an app that ships to phones can't keep secret.

## Decision

We will download the covers that are missing from the phone from the **Cover Art Archive**, matched
through **MusicBrainz** by the album's title and artist, in **WorkManager work** of the library, and
keep a **record per album in a table of the user's data** with the image in the app's own files.

```
scan ends ─▶ pass: albums with no record ─ thumbnail? ─ yes ─▶ OnPhone
                                             └─ no ──▶ Pending ─▶ work (WorkManager, a network)
                                                                    │ one album at a time, ≤ 1 request/s
                         MusicBrainz search ─▶ match? ─▶ Cover Art Archive front-500
                                                 │                    │
                                     NotFound (attempt +1)    Found ─▶ filesDir/artwork/<file>.jpg

ArtworkRepository: thumbnail of the song ─▶ else the album's Found file ─▶ else none (placeholder)
```

### The order

- **The picture in the tags, then an image in the folder, then the downloaded cover, then the
  placeholder.** The first two are MediaStore's thumbnail of the song, as ADR-026 says.
- **What is on the phone always wins.** Each load asks for the thumbnail first, so a `cover.jpg` that
  appears after a download is shown at once. That load deletes the downloaded file and records the
  album as on the phone.

### The source: MusicBrainz and the Cover Art Archive

- **A search of release groups** on `musicbrainz.org/ws/2/release-group`, by the album's title and
  artist, and then the archive's `coverartarchive.org/release-group/{mbid}/front-500`: a JPEG of
  500 pixels, a few dozen kilobytes.
- **When the match needs them (How a match is judged), a second search** with the first part of the
  title, and **a search of releases** on `musicbrainz.org/ws/2/release` by a release group's MBID
  (`rgid:`), for the track counts of its releases.
- **What is sent is the album's title and artist name,** and MusicBrainz's own ids, and nothing else
  about the user or the phone. None of it is private, so downloading is on by default. The year,
  the track numbers and the song artists that the match reads stay on the phone.
- **The `User-Agent` is `Rolabox/<versionName> ( https://github.com/toomanyeduardos/rolabox )`.**
- **At most one request a second,** to either host, for the whole app. A `503` is MusicBrainz asking
  to slow down, and counts as a server error (Retries).
- **Apple's API isn't used,** for its terms, and `plID` isn't read: nothing would use it.

### How a match is judged

In two passes. The first is strict, since a cover found by it is certain. The second runs only when
the first finds no release group or several, and takes the closest one: a cover of the same artist's
album with the same first words is better than none, and a near miss is the risk accepted for it.

```
pass 1: search title ─▶ exactly one equal? ─ yes ─▶ its cover
                            └─ none, or several ─▶ pass 2: search first part of the title
                                                      ─▶ candidates of both answers, ranked
                                                      ─▶ the first one with a cover, of at most 3
```

**What both passes compare.**

- **Names are compared after the same normalization on both sides:** Unicode NFKC, accents folded
  (NFD with the combining marks dropped, so "JAŸ-Z" is "jay z"), case, whitespace collapsed,
  punctuation dropped, and a leading "The" on an artist.
- **The artist matches** when the release group's artist credit, as one name or its first artist,
  is the album's artist, or the artist of any of the album's songs. *Live at Radio City* is "Dave
  Matthews" as an album and "Dave Matthews & Tim Reynolds" on its songs, which is MusicBrainz's
  credit.
- **A store's edition note is dropped from the end of a title** before it is searched and compared,
  on both sides. A note is the end of the title from its last opening bracket (`(` or `[`), or from
  its last ` - `, when the note's last word is `Version`, `Edition`, `Remaster`, `Remastered`
  (with or without a year after it), `EP` or `Single`. It is dropped again until none is left, and
  never when nothing would be left of the title. So "Collision Course (Bonus Video Version) - EP"
  is "Collision Course", "Invisible (RED) Edit Version" is "Invisible", and "Avenged Sevenfold
  (Bonus Track Version)" is "Avenged Sevenfold". A note that names other music stays: "(Live)",
  "(Acoustic)", "(Soundtrack)" and "- The Best of …" are part of the title, since MusicBrainz often
  keeps a live or acoustic album as a release group of its own. Pass 2 is what looks past those.
- **MusicBrainz's score is at least 95.**
- **A Various Artists album** matches MusicBrainz's Various Artists.

**Pass 1: equal.**

- **The search sends the title without its store's notes,** and the album's artist.
- **The title must be equal,** without its store's notes, on both sides.
- **Exactly one release group passes.** Its cover is the album's, or the album has none: when the
  archive has no front image for it, pass 2 doesn't run.

**Pass 2: the first part of the title, and the closest one.**

- **It runs when pass 1 finds no release group, or several.**
- **The first part of a title** is what comes before its first `(`, `[`, `:` or ` - `, after its
  store's notes are dropped, and the whole title when nothing would be left before it. "Skin and
  Bones (Live)" is "Skin and Bones", and "Experience Hendrix - The Best of Jimi Hendrix" and
  MusicBrainz's "Experience Hendrix: The Best of Jimi Hendrix" are both "Experience Hendrix".
- **The search sends the first part,** and the album's artist. A first part that is the whole title,
  as pass 1 sent it, isn't sent again.
- **The candidates** are the release groups of either answer whose first part is equal to the
  album's, by an artist that matches, with a score of at least 95.
- **They are ranked by these hints, in order,** each one deciding only between the candidates the
  ones before it left equal:
  1. **The full title closest to the album's:** the fewest words, after normalization and without
     store's notes, that are in one title and not in the other.
  2. **Live, when the album says so:** when the part of the album's title after its first part has
     "live", "concert" or "unplugged", a release group whose secondary types have Live comes first.
  3. **Enough tracks:** a release group with a release of at least as many tracks as the album's
     highest track number comes first. The track counts come from a search of the release group's
     releases, asked only for the candidates still equal at this hint, and an album with no track
     numbers skips it.
  4. **The same year:** a release group first released in the album's year comes first. The year is
     a weak hint: a compilation's songs often carry the year each was recorded, so an album with no
     single year, or a year no candidate has, skips it.
  5. **An album:** a primary type of Album comes before EP, Single and the rest.
  6. **The earliest:** the earliest first release date, then MusicBrainz's order.
- **The first ranked candidate whose archive has a front image is the album's cover.** At most 3 are
  asked. When none has one, it is an answer of no cover (Retries).
- **An album with no title, or by the unknown artist, is never searched.**
- **The match is the source's to judge,** in `:device:library:network:impl`, since it reads the
  source's answer. The `:network:api` takes an album's names, the artists of its songs, its highest
  track number and its year, and returns a cover or none.
- **An album takes at most 2 searches, 3 searches of releases and 3 requests to the archive,** all
  at one request a second.

### What is recorded: a table of the user's data

- **One row per album, in the library's Room database,** keyed by the album's grouping key (its title
  key and its album artist's key, ADR-024's Grouping), not by `AlbumId`: the albums table is cache,
  and its rows are rebuilt.
- **The states:**

  | State | Holds |
  | --- | --- |
  | `Pending` | Queued, never answered |
  | `Found` | The file name of the cached image, the release group's MBID, and when it was found |
  | `NotFound` | The number of attempts, and when the last one was |
  | `OnPhone` | The phone has a cover, so nothing is downloaded |

- **It is the first table of the user's data** (ADR-024, Changing the schema). It survives rescans
  and changes to the cache tables, and each change to it has a migration tested against the exported
  schema.
- **A row stays when its album leaves the library,** with its file, so an album on a card that is
  unmounted, or tagged back the way it was, doesn't download again. Removing them is for later.

### When: a pass after each scan

- **When a scan ends,** at the start of the process ([ADR-025](025-process-start-tasks.md)) and after
  the observer's rescans, the library's `:impl` takes the albums with no row. For each, it asks the
  local side for the thumbnail of the album's song (ADR-026), one song per album. An album with a
  cover gets `OnPhone`, and one without gets `Pending`.
- **Then it enqueues the work** if any album is `Pending`, or `NotFound` and due again.
- **The pass runs only while downloading is allowed** (Offline mode and the setting), since its
  thumbnails are of use only to the download.
- **A record is corrected when a load finds it wrong.** A load that finds a thumbnail makes the row
  `OnPhone` (and deletes a `Found` file). A load of an `OnPhone` album that finds none, for the song
  and for the song that stands for the album, makes it `Pending`: a song with no picture of its own
  can be on an album whose other song has one. A `Found` row whose file is missing becomes `Pending`.

### The work: WorkManager

- **One unique work, `artwork-download`,** that keeps a running one, with a network constraint of any
  kind. It takes the due albums one at a time, `Pending` first, and stops when none is left.
- **The album shown goes first.** Observing a song's artwork (`observeArtwork`), as Now Playing and
  playback do for the item playing, makes its album the one wanted next, when the phone has no cover
  for it and it is queued or has no record yet: one with none is queued then. The work is enqueued if
  downloading is allowed. The request in flight isn't cancelled: it ends and is kept, and the album
  wanted is the next one asked for, a few seconds later at one request a second. Only one album is
  wanted at a time, the last one shown, kept in memory. An album not found waits out its day or week
  like any other (rule 5), and an album already found or on the phone isn't asked for.
- **Why WorkManager, unlike the scan (ADR-024, rule 9):** nobody waits for it, it needs the network,
  it should survive the process and the network going away, and WorkManager gives it a constraint and
  a backoff. A process killed halfway leaves the album it was on as it was, and the work resumes.
- **It lives in `:device:library:impl`,** and gets its dependencies through a Hilt entry point, as
  `SyncWorker` does.
- **ADR-022's rule 2 is revised** to allow a part's work that fetches what its network side has and
  its local side keeps. The repositories still read the local side only: `ArtworkRepository` never
  calls the network. It makes an album wanted, and enqueues the work, which does.

### Retries

- **Only "the source answered and has no cover" is an attempt:** no candidate in either pass, or no
  front image for the one pass 1 found or for the ones pass 2 asked. The row becomes `NotFound`
  with one more attempt.
- **An attempt is tried again after at least a day, then after at least a week, and not after the
  third.** A due retry is picked up by the pass after the next scan, so the phone isn't woken for it.
- **No network, a timeout and a server error (`5xx`, `503` included) aren't attempts.** The work
  returns `Result.retry()`, with WorkManager's exponential backoff, and the album stays as it was.
- **A `Found` album is never fetched again.** A cover doesn't change.

### The cache

- **`filesDir/artwork/`, one JPEG per album, as the archive served it.** Not `cacheDir`, which the
  system may clear while the table says `Found`.
- **Written to a temporary file and renamed,** before the row says `Found`.
- **No limit on its size.** A cover is a few dozen kilobytes, so 1,000 albums are well under 100 MB.
- **The local side owns it:** `:device:library:local:impl` writes the file, reads it into an
  `ArtworkImage` at the size of ADR-026's thumbnails, and deletes it. The bytes cross the
  `:network:api` and the `:local:api` as a `ByteArray`.

### Offline mode and the setting

- **No artwork request is made in offline mode.** ADR-008's rule 6 is revised to say so.
- **The setting is on by default,** kept on this phone and not synced, as the library's flag for the
  permission request is: in `:common:storage`, through the library's local side. The settings list
  (`:device:settings:impl`) shows it as Online Artwork, On or Off, after Theme, through
  `ArtworkRepository`. Center changes it in place, as it does Clicker.
- **The work checks both before each request,** not only when it is enqueued, and stops when either
  says no. Turning the setting off, or choosing offline mode, cancels the work. Covers already
  downloaded are still shown.
- **The library's `:impl` reads offline mode** from `UserDataRepository`, in `:common:userdata:api`.

### What `ArtworkRepository` becomes

- **`ArtworkRef` stays a song.** The `:impl` maps the song to its album's key.
- **It gains `observeArtwork(ref)`,** a `Flow<Either<LibraryError, ArtworkImage?>>` that emits the
  image the load would return, and again when the album's record changes: when a cover is
  downloaded, or the phone's replaces it. Observing it makes the album wanted next (The work).
  `loadArtwork(ref)` stays, for Media3's one-shot `BitmapLoader`.
- **It gains the setting:** `observeDownloadEnabled()` and `setDownloadEnabled(enabled)`.
- **The LRU's entry is for one version of the file and one version of the record,** so a download
  isn't hidden by an image of the placeholder's time.

### Who shows it

- **Now Playing observes the artwork of the item playing.** When a cover arrives for the album it
  shows, the placeholder is replaced, while the song plays.
- **The notification and the lock screen follow.** `:device:playback:impl` observes the artwork of the
  current item too, and when it changes, it updates the item's `MediaMetadata` with an artwork URI
  that carries the record's version, so Media3 loads it again through the same `BitmapLoader`. Only
  the metadata changes, so playback isn't interrupted.

### The layout

```
:device:library:api            ArtworkRepository gains observeArtwork and the setting
:device:library:impl           the pass after the scan, the work, the order of the sources
:device:library:network:api    the cover source, as an interface, its CoverQuery, and CoverSourceError
:device:library:network:impl   MusicBrainz and the Cover Art Archive over OkHttp, the match
:device:library:local:api      the record of each album, the cached file, the setting
:device:library:local:impl     the table, its migrations, the files in filesDir/artwork
```

- **The library gains its network sub-part** (ADR-022, rule 1). ADR-024 said it had none.
- **`CoverQuery` holds what the match reads of an album:** its title, its artist, the artists of its
  songs, its highest track number and its year, the last two `null` when the index has none. The
  library's `:impl` builds it from the index.
- **OkHttp and kotlinx-serialization-json** are new in the stack, as dependencies of `:network:impl`
  modules only. OkHttp follows the archive's redirect. The tests serve recorded answers of MusicBrainz
  from an OkHttp interceptor, so no request leaves them. JSON is read as a tree, with no compiler
  plugin. The `OkHttpClient` is injected lazily, so nothing opens a connection in offline mode
  (ADR-008, rule 8).
- **`CoverSourceError`** is the network side's sealed error ([ADR-007](007-error-handling.md)):
  `Unreachable` (no network, a timeout), `Server(code)`, and `Malformed` for an answer that can't be
  read. The network's `:impl` maps `IOException`, `SocketTimeoutException` and the HTTP codes to it.
  "No cover" isn't an error: it is a `Right` with no cover. The work folds the `Either` into its
  `Result`.

## Alternatives considered

- **Apple's Lookup API by `plID`.** An exact match for iTunes files, with no fuzzy step. But its terms
  allow the artwork only to promote the store, next to a badge, and the album that started this
  ticket isn't in the store any more.
- **Last.fm or Discogs.** Large catalogs, and Last.fm matches loosely. But both need a key that an app
  can't keep secret, and both have terms of their own to keep. They can be a second source later,
  behind the same `:network:api`.
- **Only the strict match, as first decided.** No wrong cover, ever. But on a real library of iTunes
  purchases it left 6 of 14 albums with none, each with its cover on MusicBrainz: store notes in the
  title, two equal release groups, an artist credited on the songs and not on the album. The user
  can't fix a missing cover any more than a wrong one, and a missing one is far more common.
- **Taking the best score, with no pass 1.** One search, and every album gets the closest answer. But
  MusicBrainz's score is about the words searched, and two release groups of one name both score
  100: it would pick by the source's order where the album's own tags can tell them apart. Pass 1
  keeps the certain answers certain, and pass 2's hints are the album's.
- **Pass 2 ignoring only a list of words: "live", "acoustic", "bonus …".** It couldn't pick a wrong
  album by a title's first words alone. But the variations are open-ended ("- The Best of …",
  ": Volume 2", a city's name), and the list would grow with every library. The first part of the
  title, the same artist, and the ranking are the guard instead.
- **The year as the main hint.** The index has it, with no request. But it is a year, not a date, so
  two editions of one year are equal (*Skin and Bones*'s album and EP are both 2006), and a
  compilation's songs often carry the years they were recorded (*Experience Hendrix*'s say 1966 to
  1971). The highest track number tells an album from its EP or its deluxe edition better, for one
  more request in a tie.
- **Trying the next candidate when pass 1's has no front image.** But pass 1's answer is the album,
  so another release group's cover would be another album's.
- **Download when Now Playing opens an album, with no queue.** No pass, no table of attempts, and only
  albums that are played. But the first play would show the placeholder, the notification would too,
  a failure would be tried again on every play, and an album played offline would never get one.
- **Downloads in the library's order only, with no album first.** Nothing in memory, and the order is
  the index's. But with a few dozen albums ahead of it, the album playing waits a minute or more for a
  cover that was found on the first device test.
- **Cancel the work when another album is shown, and start again with it.** The album shown would be
  asked for at once. But the request in flight would be lost, and since nothing is kept until an
  answer is, all it saves is the few seconds of one album.
- **Download in the application scope, like the scan.** No WorkManager. But it dies with the process,
  has no network constraint, and its backoff would be Rolabox's to write.
- **Write the cover into the files.** Every player would see it. But ADR-024's rule 1 never writes the
  user's files, and a tag write that goes wrong damages music the user owns.
- **Keep the record in a cache table, keyed by `AlbumId`.** No migration to write. But a change to any
  cache table would download every cover again, and `AlbumId` is a row of a table that is rebuilt.
- **Cache in `cacheDir`.** The system can reclaim it when space is low. But it can also clear it while
  the record says `Found`, and every cover would be downloaded again.
- **`HttpURLConnection` and a JSON parser of our own.** No dependency. But redirects, timeouts and
  tests against recorded responses would be Rolabox's to write, for the first of what will be several
  network sides.

## Consequences

- *Live 2012*, and albums like it, show their cover on Now Playing, the notification and the lock
  screen, once the phone has been online.
- An album's title and artist go to MusicBrainz and the Cover Art Archive. The setting turns it off.
- Some albums with a cover online won't get it: a first part of the title that differs from
  MusicBrainz's, or an artist credited differently on MusicBrainz and in every tag of the album.
- **Pass 2 can show a near miss:** the cover of another album by the same artist whose title starts
  the same way, such as *Greatest Hits: Volume 2* for a *Greatest Hits* that MusicBrainz doesn't
  have, or the EP of an album with no track numbers. Replacing a cover is still out of scope, so
  the user can't correct it yet.
- An album needs up to 8 requests instead of 2, at one a second, only when pass 1 misses. A first
  download of a large library takes longer.
- An album already `NotFound` before the 2026-11-06 revisions is searched again with the new match
  only when its retry is due (rule 5).
- The library has its first table of the user's data. From now on, every change to its schema says
  whether it touches it, and the migrations of that table are tested.
- The library has a network side, and OkHttp and kotlinx-serialization-json are in the stack.
- Covers of albums no longer in the library stay in `filesDir`, until a later ticket removes them.
- A downloaded cover is 500 pixels, a little softer than the phone's 720-pixel thumbnails on Now
  Playing.
- The pass after a scan decodes one thumbnail per new album. A first scan of a large library does it
  for every album, once.

## Rules

1. `[convention]` Artwork comes from the picture in the tags, then an image in the folder (MediaStore's
   thumbnail of the song), then a downloaded cover, then the placeholder. Each load asks for the
   thumbnail first, and a cover on the phone replaces a downloaded one.
2. `[convention]` Covers are downloaded only from the Cover Art Archive, matched through MusicBrainz by
   the rules under "How a match is judged": pass 1 equal, then pass 2 only when it misses. Only an
   album's title and artist, and MusicBrainz's own ids, are sent, with the `User-Agent` above, at most
   one request a second.
3. `[convention]` No artwork request is made in offline mode or while the setting is off. The work
   checks both before each request. Tests cover both.
4. `[convention]` Downloads run only in the library's `artwork-download` WorkManager work, one album at a
   time, with a network constraint: the album shown first, then the queued ones, then the ones due again.
   A request in flight is never cancelled to change the order. `ArtworkRepository` never calls the
   network.
5. `[convention]` Only an answer of no cover is an attempt. An album is tried again after at least a day,
   then a week, and not after three attempts. A `Found` album is never fetched again.
6. `[convention]` The record of each album is a table of the user's data, keyed by the album's grouping
   key, with migrations tested against the exported schema. Covers are cached in `filesDir/artwork/`,
   and never written to the user's files.
7. `[enforced]` OkHttp (`com.squareup.okhttp3`) and kotlinx-serialization-json
   (`org.jetbrains.kotlinx:kotlinx-serialization-json`) are dependencies of `:network:impl` modules
   only.
8. `[convention]` Now Playing, the notification and the lock screen observe the artwork through
   `ArtworkRepository`, and replace the placeholder when a cover arrives, without interrupting playback.

**Conformance.** Rule 7 is checked by `ModuleRules.kt` when the build is configured, on those
artifacts, with a violating and a passing case in `ModuleRulesTest`. The rest are reviewed, and
covered by tests: the order and the corrections of a record by the tests of `ArtworkRepository` in
`:device:library:impl`; the states, the retries and giving up, offline mode and the setting, by the
tests of the pass and the work against a fake cover source; the match, both passes and each hint of
the ranking, by the tests of `:device:library:network:impl` against recorded responses, with no
network (*Skin and Bones*, *Experience Hendrix* and *Live at Radio City* among them); the table by Room's
migration tests; rule 8 by `NowPlayingViewModelTest` and the tests of playback's artwork watcher.
Rule 2's request rate and the device behavior are checked by hand with *Live 2012*.
