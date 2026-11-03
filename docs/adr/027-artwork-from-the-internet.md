# ADR-027: Covers missing from the phone are downloaded from the Cover Art Archive, in WorkManager, and kept as the user's data

- **Status:** Accepted
- **Date:** 2026-11-03
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

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
- **What is sent is the album's title and artist name,** and nothing else about the user or the
  phone. Neither is private, so downloading is on by default.
- **The `User-Agent` is `Rolabox/<versionName> ( https://github.com/toomanyeduardos/rolabox )`.**
- **At most one request a second,** to either host, for the whole app. A `503` is MusicBrainz asking
  to slow down, and counts as a server error (Retries).
- **Apple's API isn't used,** for its terms, and `plID` isn't read: nothing would use it.

### How a match is judged

A wrong cover is worse than none, so the match is strict:

- **The title and the artist must be equal** to what is in the index, after the same normalization on
  both sides: Unicode NFKC, case, whitespace collapsed, punctuation dropped, and a leading "The" on
  the artist. The artist is the release group's artist credit as one name, or its first artist.
- **MusicBrainz's score is at least 95.**
- **Exactly one release group passes.** Several that pass are an ambiguous answer, and are no cover.
- **A Various Artists album** matches MusicBrainz's Various Artists.
- **An album with no title, or by the unknown artist, is never searched.**
- **The match is the source's to judge,** in `:device:library:network:impl`, since it reads the
  source's answer. The `:network:api` takes an album's names and returns a cover or none.

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
  `OnPhone` (and deletes a `Found` file). A load of an `OnPhone` album that finds none makes it
  `Pending`. A `Found` row whose file is missing becomes `Pending`.

### The work: WorkManager

- **One unique work, `artwork-download`,** that keeps a running one, with a network constraint of any
  kind. It takes the due albums one at a time, `Pending` first, and stops when none is left.
- **Why WorkManager, unlike the scan (ADR-024, rule 9):** nobody waits for it, it needs the network,
  it should survive the process and the network going away, and WorkManager gives it a constraint and
  a backoff. A process killed halfway leaves the album it was on as it was, and the work resumes.
- **It lives in `:device:library:impl`,** and gets its dependencies through a Hilt entry point, as
  `SyncWorker` does.
- **ADR-022's rule 2 is revised** to allow a part's work that fetches what its network side has and
  its local side keeps. The repositories still read the local side only: `ArtworkRepository` never
  calls the network.

### Retries

- **Only "the source answered and has no cover" is an attempt:** no match, an ambiguous answer, or a
  release group with no front image. The row becomes `NotFound` with one more attempt.
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
- **The setting "Download missing artwork" is on by default,** kept on this phone and not synced, as
  the library's flag for the permission request is: in `:common:storage`, through the library's local
  side. A row in the settings list (`:device:settings:impl`) shows it, through `ArtworkRepository`.
- **The work checks both before each request,** not only when it is enqueued, and stops when either
  says no. Turning the setting off, or choosing offline mode, cancels the work. Covers already
  downloaded are still shown.
- **The library's `:impl` reads offline mode** from `UserDataRepository`, in `:common:userdata:api`.

### What `ArtworkRepository` becomes

- **`ArtworkRef` stays a song.** The `:impl` maps the song to its album's key.
- **It gains `observeArtwork(ref)`,** a `Flow<Either<LibraryError, ArtworkImage?>>` that emits the
  image the load would return, and again when the album's record changes: when a cover is
  downloaded, or the phone's replaces it. `loadArtwork(ref)` stays, for Media3's one-shot
  `BitmapLoader`.
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
:device:library:network:api    the cover source, as an interface, and CoverSourceError
:device:library:network:impl   MusicBrainz and the Cover Art Archive over OkHttp, the match
:device:library:local:api      the record of each album, the cached file, the setting
:device:library:local:impl     the table, its migrations, the files in filesDir/artwork
```

- **The library gains its network sub-part** (ADR-022, rule 1). ADR-024 said it had none.
- **OkHttp and kotlinx-serialization-json** are new in the stack, as dependencies of `:network:impl`
  modules only. OkHttp follows the archive's redirect, and is tested against recorded responses with
  its `MockWebServer`. The `OkHttpClient` is injected lazily, so nothing opens a connection in
  offline mode (ADR-008, rule 8).
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
- **A looser match:** dropping "(Deluxe Edition)" or "Remastered", or taking the best score. More
  albums would get a cover, and some would get the wrong one, which the user can't fix while
  replacing a cover is out of scope. The strict match can be loosened when real misses show what to
  allow.
- **Download when Now Playing opens an album, with no queue.** No pass, no table of attempts, and only
  albums that are played. But the first play would show the placeholder, the notification would too,
  a failure would be tried again on every play, and an album played offline would never get one.
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
- Some albums with a cover online won't get it: a title that differs from MusicBrainz's ("Deluxe
  Edition"), an artist credited differently, or two release groups of one name.
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
   the rules under "How a match is judged". Only an album's title and artist are sent, with the
   `User-Agent` above, at most one request a second.
3. `[convention]` No artwork request is made in offline mode or while the setting is off. The work
   checks both before each request. Tests cover both.
4. `[convention]` Downloads run only in the library's `artwork-download` WorkManager work, one album at a
   time, with a network constraint. `ArtworkRepository` never calls the network.
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
tests of the pass and the work against a fake cover source; the match by the tests of
`:device:library:network:impl` against recorded responses, with no network; the table by Room's
migration tests; rule 8 by `NowPlayingViewModelTest` and the tests of playback's artwork watcher.
Rule 2's request rate and the device behavior are checked by hand with *Live 2012*.
