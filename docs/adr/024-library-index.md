# ADR-024: The library index: MediaStore finds the files, and Rolabox's own database is what the device reads

- **Status:** Accepted
- **Date:** 2026-10-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-10-30:** library/03 saw detekt report `fallbackToDestructiveMigration`, so rule 11 is
  `[enforced]`. Until library/06, a song's artist is its own, and missing values are `null` through
  the part's `:api`. The start of the process reaches the library through ADR-025. The scan state is
  kept per volume, since MediaStore's generations are.
- **Revised 2026-10-31:** `LibraryRepository` also keeps whether the device has asked for the permission
  (What `LibraryRepository` becomes). Building library/04 showed that Android's rationale check is false
  both before the first ask and after a permanent denial, so the device can't tell them apart without
  it. Who asks is unchanged. The `LibraryStatus` cases keep their names.

## Context

The device browses `SampleLibrary`, a hard-coded list in `:device:library:impl` that is served in
memory ([ADR-020](020-modules-by-product-area.md), revision of 2026-10-06). ADR-002 has always said
that the library is indexed from MediaStore into a Room database, and left where that database
lives to its own ADR. [Real music](https://trello.com/c/5rwZH0WU) needs the index, and this is that
ADR.

What the decision has to fit:

- **The music is the user's own files, anywhere on the phone.** Rolabox doesn't put them there and
  never moves them. They are often tagged badly or not at all: tags missing, wrong or in an odd
  encoding, files with no folder structure. The device never shows a blank row.
- **The planning session of 2026-10-27 set the direction.** Files are found through MediaStore, with
  the audio permission. Rolabox's own index is the source of truth the device reads
  ([ADR-002](002-offline-first-data-flow.md)), and MediaStore and the files are where its data
  comes from. Files are never copied into the app's storage, so they stay when the app is
  reinstalled, and the index is rebuilt by a rescan. Each field of a song's metadata comes from the
  first source that has it, in a fixed order, and the index records which one.
- **[ADR-023](023-playback-engine.md) already took two decisions from this one.** Every `Song`
  has the `content://` URI of its audio, which playback hands to Media3 without querying anything
  itself (ADR-023, rule 5). And the storage permission is the library's alone.
- **ADR-022 says how a part's data sources are split.** A part's local side is
  `:<part>:local:{api,impl}`, and its `:impl` combines the sides
  ([ADR-022](022-data-source-sub-parts.md)). Where Room's database, entities and DAOs live was
  left open there and in ADR-020.
- **Libraries are large.** 10,000 songs is an ordinary library, and the first scan of one has to
  finish while the user waits, with the device usable as early as possible.
- **Room isn't in the stack.** Storage today is DataStore, behind `PreferencesStore` in
  `:common:storage`, which holds key–value settings.
- **minSdk is 29.** The audio permission is `READ_MEDIA_AUDIO` from API 33 and
  `READ_EXTERNAL_STORAGE` before it. On API 29, MediaStore has no generation numbers.

**Target state.** This ADR is ahead of the code. Pass 1 is built (library/03). The tickets that build
the rest are listed under "What each ticket builds".

## Decision

We will find the music through **MediaStore**, keep it in a **Room database that belongs to the
library**, in `:device:library:local:impl`, and read the tags ourselves with **our own parser for
ID3 and MP4**. The device reads only the index, through `LibraryRepository`.

```
MediaStore ─query─┐                                  ┌─ observe (Flow) ─▶ LibraryRepository ─▶ screens
                  ├─▶ scanner (:impl) ─write─▶ index ┤
files ──tags──────┘     pass 1, pass 2      (Room)   └─ Song's content:// URI ─▶ playback (ADR-023)
```

### Where the files come from

- **MediaStore's audio collection, the items marked as music.** One query finds every file the
  system's scanner indexed, with its id, `content://` URI, relative path, file name, size, date
  modified, duration and the system's reading of its tags. Ringtones, alarms and notifications are
  not music, and are left out.
- **The permission is `READ_MEDIA_AUDIO` from API 33, and `READ_EXTERNAL_STORAGE` with
  `maxSdkVersion="32"` before it.** Both are declared in `:device:library:local:impl`'s own
  `AndroidManifest.xml`, which the build merges into the app's, as ADR-023 does for playback's
  service. `:app` declares nothing for the library.
- **Asking for the permission is the UI's** ([ADR-009](009-ui-bound-sdks.md)): it needs an
  Activity. The library only checks whether it has been granted, and reports it (below).
- **Files are never copied, moved or written.** The index holds their metadata and their URI, and
  the audio is read only when it plays, or for its tags while scanning.
- **A picked folder (the Storage Access Framework) can come later as "Add a folder",** for files
  MediaStore misses. Its files are `content://` URIs too, so playback doesn't change, and the index
  gets a second kind of source. Nothing here is built for it.

### The layout

```
:device:library:api          LibraryRepository, LibraryStatus, Artist, Album, Song (with its URI),
                             LibraryError
:device:library:impl         the repository over the index, the scanner and its triggers, the
                             fallback order of each field, the grouping rules
:device:library:testing      FakeLibraryRepository, and fakes of the three local sources
:device:library:local:api    the three local sources, as interfaces: the index, the audio files
                             (MediaStore), and the tags of one file
:device:library:local:impl   the Room database, its entities and DAOs, the MediaStore queries and
                             observer, the ID3 and MP4 parser, and the manifest with the permission
```

- **The library has a local sub-part and no network one** (ADR-022, rule 2). Favorites add a
  network sub-part when they are synced. That is their own ticket.
- **`:local` knows how everything is stored on the phone:** the database's tables, MediaStore's
  columns, and the byte layout of ID3 and MP4. Room, MediaStore, `ContentResolver` and their
  exceptions stay in `:local:impl` and are mapped to `LibraryError` at its boundary
  ([ADR-007](007-error-handling.md), ADR-022 rule 7).
- **The part's `:impl` knows what a library is:** which source wins for each field, how songs are
  grouped into albums and artists, and when to scan. It reaches the files, the tags and the index
  only through `:local:api`, so all of it is tested on the JVM against the fakes.
- **`:local:api` has plain models of its own,** for what the part's `:api` doesn't show: an audio
  file as MediaStore lists it, the tags read from a file, and a song as indexed, with the source of
  each field. They are Kotlin data classes with no Android type, like every `:api`. ADR-022 rule 7
  is revised to allow this.

### The index is a Room database that the library owns

- **Room.** ADR-002 named it, and nothing found here argues against it. It gives observable queries
  as `Flow`, checks the SQL at compile time with KSP, exports each version of the schema, and has
  tested migrations. Its DAOs run on the JVM with Robolectric, which ADR-023 already adds to the
  stack.
- **One database per part, in that part's `:local:impl`.** The library's is the first. A part that
  later keeps relational data of its own, such as podcasts with their episodes' positions, gets a
  database of its own in its own `:local:impl`. No database is shared, so no module knows another
  part's tables, and ADR-022's ownership holds.
- **What is stored:**

  | Table | Holds | Kind |
  | --- | --- | --- |
  | Songs | One row per file: the index's own id, the MediaStore id, the URI, the relative path, the file name, the size, the date modified; the title, artist, album artist, album, track and disc numbers, year, compilation flag and duration, each with its source; whether the file has an embedded picture; and the date modified at which its tags were last read | Cache |
  | Albums | One row per album, found by its grouping key: title, artist, year | Cache |
  | Artists | One row per artist, found by its grouping key: name | Cache |
  | Scan state | MediaStore's version and generation at the last scan, for each volume | Cache |

  Genre is not stored yet. music/02 adds it as a change to cache tables.
- **Artwork is a reference, not an image.** The index records whether a file has an embedded
  picture, once its tags have been read. How it is loaded and shown is playback/06's. A cover image
  next to the files (`cover.jpg`) needs the images permission, and is out of this phase.

### Metadata: one fixed order per field

Each field of a song takes its value from the first of these that has one:

1. **The tags, read by Rolabox** (below).
2. **MediaStore**, the system's reading of the same tags.
3. **The folder.** The parent folder is the album, and the one above it the artist, when the path
   looks like `…/Artist/Album/file`.
4. **The file name.** The title is the name without its extension, and a leading number is the
   track number (`03 - Title.mp3`).
5. **Unknown.** The index stores no value, with "unknown" as its source.

- **The index stores where each value came from,** as a source per field. A value from a weaker
  source is replaced when a stronger one turns up, and never the other way round, so a second pass
  only improves a song.
- **Duration is not a tag but a property of the audio,** so for it MediaStore comes first, and the
  parser's reading of the stream's header is used only when MediaStore has none.
- **"Unknown" is never stored as text.** The words the user sees ("Unknown album") are the screens',
  in strings that can be translated. The library sorts and groups a missing value as a value of its
  own.
- **Which source a field can come from, and how the folder and the name are read,** are library/05's
  details. **How songs become albums and artists** (album artist, compilations, discs, songs with no
  album) are library/06's grouping rules. Until library/06, songs are grouped by album title and
  album artist, or artist when there is none. A song keeps its own artist, and a name is never split,
  so "U2 & Coldplay" is an artist of its own. An artist's albums include those of others they have a
  song on, and an album artist's songs include those of their albums.

### Reading the tags: our own parser

- **Rolabox reads ID3 and MP4 itself,** the tags of the two formats this phase tests (MP3 and
  M4A/AAC). For ID3 that is v1 and v1.1 at the end of the file, and v2.2 to v2.4 at its start, with
  every text encoding the standard has, unsynchronisation, and extended headers. For MP4 it is the
  `ilst` atom's items, and the duration from `mvhd`.
- **It reads only the bytes it needs:** the ID3v2 header and its frames, the last 128 bytes, or the
  MP4 atoms with `mdat` skipped. A picture's bytes are skipped and only its presence is recorded.
  The file is opened through `ContentResolver` from its `content://` URI, so no path or `File` is
  needed under scoped storage.
- **Other formats (FLAC, OGG/Opus, WAV) take their values from MediaStore,** and then the folder and
  the name. A reader for Vorbis comments can be added to the parser later without changing anything
  around it.
- **The parser is plain Kotlin over bytes,** in `:local:impl`, tested on the JVM with tags built in
  the tests and with the generated tones of library/02.
- **Text is decoded as the tag declares.** A Latin-1 frame that really holds another code page shows
  wrong characters, as it does in most players. Whether to detect that is library/05's.

### Identity: the index's own ids

- **`SongId`, `AlbumId` and `ArtistId` are the index's own row ids.** No MediaStore id crosses the
  `:local:api`'s models into the part's `:api`.
- **A rescan matches a file to its song** by the MediaStore id first, then by the relative path and
  file name, then by the file name and size. So a song keeps its id when the system rebuilds its
  media database or the file is moved, and what is keyed on that id survives: the queue playback/05
  restores, and later the favorites.
- **An album or an artist is found by its grouping key,** so it keeps its id across rescans while
  its key stays the same.
- **The `Song`'s URI is the one the last scan read** from MediaStore. If it has changed since, the
  song fails to play as a file that is gone (playback/04), and the next scan corrects it.
- **ADR-002's portable identity key is not computed yet.** It is derived from the tags, so it is a
  cache column, added with the first synced data that needs it.

### When and how the library is scanned

A scan has **two passes**, so the device is usable within seconds of the first one:

1. **Pass 1, from MediaStore.** One query, compared with the index by MediaStore id, size and date
   modified. New files are added, changed ones updated, and files that are gone removed. Every field
   takes the best value that doesn't need the file opened: MediaStore's, then the folder's and the
   name's (from library/05), or none. Albums and artists are regrouped.
2. **Pass 2, the tags.** Every song whose tags haven't been read at its current date modified is
   read, its fields are resolved again in the fixed order, and the regrouping follows. While it
   runs, rows can change and move from one album to another.

- **Each batch is written in one transaction,** a few hundred songs at a time, so the screens'
  flows emit per batch, not per file, and a process that is killed leaves a consistent index.
  Pass 2 picks up where it stopped, since the songs it hadn't read are still marked unread.
- **What starts a scan:**
  - The permission found granted when it wasn't: the first full scan.
  - The start of the app's process ([ADR-025](025-process-start-tasks.md)). From API 30, MediaStore's version and generation say whether
    anything changed, and only changed items are read. On API 29, pass 1 compares every row, which
    is one query.
  - A `ContentObserver` on MediaStore's audio collection while the process lives, debounced, so a
    copy of many files is one scan.
- **One scan at a time.** A trigger during a scan marks the library dirty, and one more scan runs
  when this one ends.
- **The scanner is a `@Singleton` in `:device:library:impl`,** in the application scope
  ([ADR-006](006-async-api-shape.md), rule 6), on the IO dispatcher of `:common:util`.
- **No WorkManager.** A scan runs while the app is open, and the user is waiting for what it finds.
  WorkManager adds deferral and constraints to work that has to start now, and a periodic scan while
  the app is closed would wake the phone to find changes that the next start finds anyway.

### What `LibraryRepository` becomes

- **Its methods stay as they are,** and are now queries on the index. The sort orders become the
  queries' `ORDER BY`.
- **`Song` gains its URI,** as ADR-023 says and playback/02 adds: the `content://` URI from the
  index, as a string, since the `:api` is a JVM module with no `android.net.Uri`.
- **It gains `observeStatus()`,** which the device's permission and empty rows read (library/04):

  ```kotlin
  sealed interface LibraryStatus {
      data object PermissionMissing : LibraryStatus
      data object Scanning : LibraryStatus                    // the index is empty, and pass 1 runs
      data object Empty : LibraryStatus                       // a scan finished and found no music
      data class Ready(val readingTags: Progress?) : LibraryStatus  // pass 2's progress, or null
  }
  ```

  It is a `Flow` with the same `Either` as the other reads. library/04 may rename the cases, but
  not change what they mean.
- **It gains a call that checks the permission again,** for the device to make after its request
  returns and when it comes back to the foreground while the permission is missing. When the
  permission is granted, the call starts a scan.
- **It keeps whether the device has asked for the permission,** which the audio files' local source
  stores in `:common:storage`, on this phone and unsynced. Asking stays the UI's. The flag is what tells
  "not asked yet" from "Android won't ask again", which Android's own check doesn't.
- **`SampleLibrary` and `InMemoryLibraryRepository` are deleted** when library/03 binds the
  index-backed repository, with playback/02's stand-in library of tones. Tests and previews already
  use `FakeLibraryRepository`, which follows each change of the `:api`.

### Changing the schema

- **Every table is either cache or the user's data.** A cache table is rebuilt by a rescan. A
  table of the user's data (favorites later, for example) is not, since nothing on the phone could
  rebuild it.
- **A change to cache tables drops and recreates them in its migration,** and the next start
  rescans. Every table is cache today.
- **A change to a table of the user's data has a real migration,** tested against the schema Room
  exports, which is checked into Git with each version.
- **`fallbackToDestructiveMigration` is never used.** It drops the whole database, and would drop
  the user's data with the cache once there is some.

### What each ticket builds

- **library/03:** Room and the schema; pass 1 with its triggers; the permission in the manifest;
  `observeStatus()` and the permission check again; the URI from the index; deletes the sample.
- **library/04:** the device's rows for each status, and the permission request.
- **library/05:** the parser and pass 2; the fallbacks of the folder and the name.
- **library/06:** the grouping rules, and the albums' and artists' keys.
- **library/07:** the `ContentObserver`, the comparison of MediaStore's generation at start-up, and
  the dirty flag. No WorkManager.
- **music/02:** the genre, read by the parser and added to the songs table as a change to cache
  tables.

### What was not decided

- **"Add a folder" through the Storage Access Framework.** Later, for files MediaStore misses.
- **A rescan the user starts,** from settings. Nothing needs it while the observer and the start-up
  check keep the index current.
- **Detecting the real code page of a badly encoded tag** (library/05).
- **Where a podcast episode's position is stored.** In a database of the podcasts' own, when they
  come (ADR-023, What was not decided).

## Alternatives considered

**Discovery.**

| | MediaStore with the audio permission | Folder picker (Storage Access Framework) |
| --- | --- | --- |
| Setup | One prompt, finds everything | The user picks each folder |
| Coverage | What the system's scanner indexed. It skips `.nomedia` folders and some formats | Every file in the picked folders |
| Speed | Fast queries | Slow to walk thousands of files |
| Change detection | `ContentObserver` | None. Rescan |
| After a reinstall | Works again once the permission is granted | The grants are lost, and the user picks the folders again |
| Metadata | The system scanner's, often wrong on messy files | None. Rolabox reads the tags |

MediaStore wins on every row but coverage, and the folder picker can be added later for what it
misses.

**Storage for the index.**

- **SQLDelight.** SQL first, generated Kotlin, `Flow`s, and a JVM driver for tests. As good a fit
  technically. But ADR-002 names Room, Room's migration testing and schema export are what the
  cache-or-data split relies on, and Room is what an Android reader expects.
- **Files through `:common:storage`,** the songs serialized on DataStore. No new dependency. But no
  queries, no indexes, and rewriting 10,000 songs on each batch of pass 2.
- **No index: query MediaStore when a screen opens.** ADR-002 already rejected it: it can't hold the
  tags Rolabox reads, the sources, or later the favorites, and the system's metadata is what this
  phase sets out to improve.

**Where the database lives.**

- **One database in `:common:database`, for every part,** as ADR-002's text read ("holds the music
  library and all user data"). Joins across parts, and one schema to migrate. But that module would
  know the tables of every part, `:common` would know the library's entities against ADR-020's
  rule 1, and ADR-022's local sub-parts would be empty. A join across parts happens in the `:impl`
  that needs it, through each part's `:api`.
- **The database in `:device:library:impl`,** with no local sub-part. One module fewer. But the
  repository and Room would be in one module, which is the boundary ADR-022 was written to draw,
  and the scanner couldn't be tested without Room.

**Reading the tags.**

- **jaudiotagger.** Many formats, and mature. But it is LGPL, about 1 MB, written for `java.io.File`,
  which scoped storage doesn't give for a `content://` URI, and slower, since it parses more than
  the tags.
- **Media3's `MetadataRetriever`.** The same extractors as playback, and many formats. But it would
  revise ADR-023's rule 9 to put Media3 in the library, it prepares a source for each file, which is
  slow on 10,000 files, and its API is still marked unstable.
- **`MediaMetadataRetriever`.** In the platform, with no dependency. But it is slow per file, has
  fewer fields, and decodes non-UTF-8 text badly, which is the messy case this phase is about. It is
  also roughly where MediaStore's values come from, so it would add little to them.
- **TagLib, through JNI.** Fast, and every format. But a native library per ABI, LGPL, and a JNI
  layer to keep, for formats this phase doesn't test.

**Identity.**

- **`SongId` as the MediaStore id.** No matching. But the id changes when the system rebuilds its
  database or a file is moved by a file manager, and everything keyed on it (the restored queue, the
  favorites) would point at nothing.
- **A hash of the file's content.** Survives every move. But hashing 10,000 files is a scan of every
  byte, and editing the tags changes the hash.

**Scanning.**

- **One pass, tags first.** Nothing regroups once it is shown. But a large library would take a long
  time before anything can be browsed, and the device would show a scanning state for all of it.
- **Scanning in WorkManager.** Survives the process, and can be constrained. But the scan is needed
  now, while the app is open, and the start-up check already covers what changed while it wasn't.
- **The repository scans when a screen observes it.** No triggers to manage. But a screen would wait
  on files, and two screens would start two scans.

**The library's status.**

- **New `LibraryError` cases** for a missing permission or an empty library, in the existing flows.
  No new method. But a `Left` ends a flow, so a screen would have to subscribe again after the user
  grants the permission, and "empty" and "scanning" aren't errors.

**Changing the schema.**

- **A real migration for every change, the cache's included.** Nothing is ever rebuilt. But each
  migration of the cache is work that a rescan already does, and pass 2 is the expensive part.
- **`fallbackToDestructiveMigration` until there is user data.** The least work today. But it is easy
  to forget when the first table of the user's data arrives, and that is when it loses data.

## Consequences

- The device shows the phone's music, with the best metadata Rolabox can find, and each value says
  where it came from.
- The library is browsable seconds after the first scan starts, and keeps improving while the tags
  are read. Rows moving between albums during pass 2 is a cost the user can see.
- Room and KSP's Room compiler are new in the stack. The build gets an annotation processor in one
  more module.
- The parser is Rolabox's to maintain, and its bugs are Rolabox's. Its tests are the safety net,
  and the formats past MP3 and M4A rely on MediaStore's values.
- A badly encoded Latin-1 tag still shows the wrong characters until library/05 decides otherwise.
- Ids are stable through rescans and the system's own rebuilds, at the price of a matching step in
  pass 1.
- Each part with relational data has a database of its own. A screen that combines two parts'
  data combines it in Kotlin, not in SQL.
- `LibraryRepository` gains a status and a permission check, `FakeLibraryRepository` follows, and
  the music screens can show the permission and empty rows.
- library/07 needs no WorkManager, and library/03 ships before the tags are read, which changes
  those tickets' scope.
- Every schema change has to say whether it touches the cache or the user's data.

## Rules

1. `[convention]` The library finds files through MediaStore, and never copies, moves or writes
   them. The index holds their metadata and their `content://` URI.
2. `[enforced]` Only `:device:library:local:impl` uses `android.provider.MediaStore`.
3. `[convention]` The storage permission (`READ_MEDIA_AUDIO`, and `READ_EXTERNAL_STORAGE` up to
   API 32) is declared in `:device:library:local:impl`'s manifest, and in no other module.
4. `[enforced]` Room and SQLite (`androidx.room`, `androidx.sqlite`) are dependencies of
   `:local:impl` modules only.
5. `[convention]` A part's relational data is in a Room database of its own, in its `:local:impl`.
   No database is shared between parts.
6. `[convention]` The screens and other parts read the library only from the index, through
   `LibraryRepository`. Only the scanner reads MediaStore and the files, through `:local:api`.
7. `[convention]` Each field of a song takes its value from the first source that has one: the tags
   Rolabox reads, MediaStore, the folder, the file name, then none (for the duration, MediaStore
   before the parser). The index stores the source with the value, and "unknown" is never stored as
   text.
8. `[convention]` `SongId`, `AlbumId` and `ArtistId` are the index's own ids. A rescan matches a file
   by its MediaStore id, then its path, then its name and size, and an album or an artist by its
   grouping key. No MediaStore id is in the part's `:api`.
9. `[convention]` A scan runs in the application scope on the IO dispatcher, one at a time, writes
   in batches of one transaction each, and doesn't use WorkManager.
10. `[convention]` Every table is cache or the user's data. A change to cache tables drops and
    recreates them, and a change to a table of the user's data has a migration tested against the
    exported schema, which is in Git.
11. `[enforced]` `fallbackToDestructiveMigration` is never called. It is in detekt's
    `ForbiddenMethodCall`.

**Conformance.** Rule 2 is the detekt rule `MediaStoreOutsideLibrary` in `build-logic/detekt-rules`,
with a violating and a passing case, which reports an import of `android.provider.MediaStore` or a
qualified use of it. That it runs everywhere but `:device:library:local:impl` is its `excludes` in
`config/detekt/detekt.yml`. Rule 4 is checked by `ModuleRules.kt` when the build is configured, on
the `androidx.room` and `androidx.sqlite` groups, with a violating and a passing case in
`ModuleRulesTest`. Rule 11 runs with type resolution, in `detektMain`, which `check` runs, and
library/03 saw it report a call. The rest are reviewed. Rules 7 and 8 are behavior,
covered by the tests of the scanner in `:device:library:impl` (library/03, library/05 and library/06),
and rule 10 by Room's migration tests once there is a table of the user's data.
