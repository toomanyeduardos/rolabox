# ADR-036: Android backs up only the preferences and the podcasts' database, by an include-list, the same for cloud backup and device transfer

- **Status:** Accepted
- **Date:** 2026-11-20
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-027](027-artwork-from-the-internet.md) (rule 6, across a backup and restore:
  the album artwork record isn't carried to a restored or new phone)

## Context

Rolabox is going onto Google Play (launch/00). The manifest sets `allowBackup="true"` and names
`@xml/data_extraction_rules` (Android 12 and up) and `@xml/backup_rules` (Android 10 and 11, the
minimum SDK being 29). Both files were still Android Studio's templates, with no rule in them, so
Android's Auto Backup and device-to-device transfer copied everything in the app's data:

- **The preferences,** one DataStore file, `files/datastore/user_preferences.preferences_pb`
  (`:common:storage`). Most are the user's choices, some synced (ADR-011); a few are about this
  phone only: whether the audio permission was asked, and where playback stopped.
- **The library's database,** `library-index.db` (ADR-024). Every table is cache rebuilt by a
  rescan, except `album_artwork`, the record of each downloaded cover (ADR-027, rule 6), whose
  images are in `files/artwork/`. The cache describes this phone's MediaStore, whose ids mean
  nothing on another phone.
- **The podcasts' database,** `podcasts.db` (ADR-031, rule 5). Every table is the user's data: shows,
  episodes, positions, played state and follows. Nothing on the phone can rebuild it.
- **Episode audio,** Media3's cache in `files/episodes/`, and Media3's own database
  (`exoplayer_internal.db`), which is the only record of what is downloaded (ADR-031, Downloads). It
  isn't in the cache directory, so Android backs it up unless told not to, and it can be gigabytes.
- **Podcast artwork** in `files/podcasts/artwork/`, downloaded again when it is missing.
- **What SDKs keep:** Firebase Auth's signed-in user, Firebase Installations' id, Firestore's local
  cache, WorkManager's database and Crashlytics' reports.

Restoring all of it is wrong in several ways. A restore arrives signed in with tokens copied from
another install, which Firebase may or may not accept. Firestore's cache and WorkManager's queue describe another install.
The library index describes another phone's files. Episode audio fills the backup quota (25 MB for
cloud backup), so Android skips the whole backup and the user's data isn't saved at all.

## Decision

We will back up **only the user's data that can't be rebuilt**: the preferences and the podcasts'
database. The rules are an **include-list**: Android backs up only what is named, so anything a
part or an SDK stores later is left out until a decision adds it.

| Domain | Path | Why |
| --- | --- | --- |
| `file` | `datastore/user_preferences.preferences_pb` | The user's settings |
| `database` | `podcasts.db`, `podcasts.db-wal`, `podcasts.db-shm` | The podcasts' user data (ADR-031) |

Room's `-wal` and `-shm` files are named with their database, so a backup taken while a write is in
the log restores whole.

Everything else is left out, on purpose:

| Left out | Why |
| --- | --- |
| `library-index.db` and its `-wal` and `-shm` | Cache of this phone's MediaStore. Rebuilt by the first scan after a restore |
| `album_artwork` (in `library-index.db`) and `files/artwork/` | Downloaded again. A table can't be backed up apart from its database, and a record without its image would claim a cover that isn't there |
| `files/episodes/` and `exoplayer_internal.db` | Downloaded again, on demand. Left out together, so no restored record claims audio that isn't there |
| `files/podcasts/artwork/` | Downloaded again |
| Firebase Auth and Installations, Firestore, WorkManager, Crashlytics | Belong to the install. The user signs in again after a restore, and their synced data comes back from Firestore |

The same list is used in three places: `<cloud-backup>` and `<device-transfer>` in
`data_extraction_rules.xml`, and `<full-backup-content>` in `backup_rules.xml`. Device transfer
copies no more than cloud backup does, even though it has no quota: a new phone is the case where
another phone's index and SDK state are most wrong.

**A restore arrives signed out.** A user who had an account sees the sign-in screen, because the
restored preferences say they didn't choose no-account mode. A no-account user goes straight in.
Signing in merges the restored preferences and follows with Firestore by their timestamps, as any
sign-in does (ADR-011, ADR-031).

**The preferences that are about one phone** come back with the rest, because they share one file.
Each one has to be harmless on another phone:

- **Whether Android asked for the audio permission** is stored as the time this install was made,
  not as `true`. A value restored from another install doesn't match, so it reads as not asked, and
  Music shows Android's prompt. A restored `true` would read as asked, and with nothing to explain
  (Android has never asked this install), Music would send the user to Settings instead.
- **The resume point and play order** are handled like stale ones. Until the first scan, the library
  can't be read, so they're kept. After it, a song that's gone leaves the one at its place, or
  nothing (ADR-028, "What is restored").

## Alternatives considered

- **An exclude-list.** It would back up everything but the named paths. It's shorter today, but every
  new database, folder or SDK is backed up until someone remembers to exclude it. Firebase alone
  stores in several places that change between versions.
- **Back up the covers: the library's database and `files/artwork/`.** It keeps ADR-027's records
  across a restore, but with them comes the whole cache, stale and keyed by the old phone's
  MediaStore. The covers are free to download again, and a new phone may have different albums.
- **Move `album_artwork` to its own database, and back it up with its files.** It's the cleanest way
  to keep the covers. It's a new database and a data migration for something the user gets back by
  waiting, and the images may be over the quota with a large library.
- **Split the per-phone preferences into a second DataStore file that isn't backed up.** It's
  cleaner, but it moves keys of three parts and needs a migration of stored values, for keys that are
  already harmless on another phone. It's worth doing if one stops being harmless.
- **`allowBackup="false"`.** Simplest, but a user who changes phone without an account loses their
  podcasts and positions, which no-account mode has no other way to keep (ADR-008).

## Consequences

- A restore, or a new phone, keeps the user's settings and podcasts, and starts like a first run for
  everything else: a library scan, covers downloaded again, signed out.
- The backup is small, far below the cloud quota.
- Covers downloaded on the old phone are downloaded again, so ADR-027's record is the user's data
  on one phone only.
- Data added to `library-index.db` that is the user's (favorites later, ADR-024) isn't backed up.
  It needs its own database, or a revision of this decision.
- A new database, DataStore file or file folder needs a decision here before it ships, and the test
  of rule 3 fails until it has one.

## Rules

1. `[enforced]` The backup rules are an include-list. `<cloud-backup>`, `<device-transfer>` and
   `<full-backup-content>` include the same paths and have no `<exclude>`. The manifest names both
   files and sets `allowBackup="true"`.
2. `[enforced]` A Room database is included with its `-wal` and `-shm` files, or none of them is.
3. `[enforced]` Every Room database and DataStore file named in the code has a decision: included in
   the backup rules, or in this ADR's list of what is left out (and the test's).
4. `[convention]` A preference stored on one phone only, in the backed-up DataStore file, is
   harmless when restored on another phone, or moves to storage that isn't backed up.
5. `[convention]` Nothing an SDK stores, and no file that can be downloaded again, is backed up.

**Conformance.** Rules 1 to 3 are `BackupRulesTest` in `:app`'s unit tests, which parses both files
and the manifest, and reads the database and DataStore names from the main sources of every module,
with a violating and a passing case. Rule 4's audio permission is `PermissionAskedTest` in
`:device:library:local:impl`. Rules 4 and 5 are otherwise reviewed. The restore was checked by hand
with `adb shell bmgr` and a reinstall on an emulator: only the four included files come back, so the
settings, shows and follows do, and no artwork, episode audio, Media3, WorkManager or Firebase state;
the app starts cleanly, in no-account mode, and Music shows Android's prompt; the library index is
built again. Device-to-device transfer follows the same list, checked by the test, not by hand.
