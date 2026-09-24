# ADR-002: Offline-first data flow with the local database as the single source of truth

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox is a player for music files stored on the device. Playing a song must never depend on a
network connection. Some user data (favorites, playlists, play history such as "most played this
month") may later be synced to a remote backend so it follows the user across devices, but:

- which backend, and exactly what it stores, is not decided yet;
- the app must be fully usable without signing in;
- the audio files themselves are the user's and should never be uploaded.

Two technical facts shape the decision:

- **MediaStore IDs are device-local.** They differ between devices and can change after a rescan,
  so they can't identify a song across devices.
- **Display strings aren't stable identities.** "Artist - Title" breaks on re-tagging, on case and
  "feat." variations, and on the same song appearing on both an album and a compilation.

## Decision

**The local database is the single source of truth.** A Room database (a future
`:core:database` module) holds the music library and all user data. The UI only observes data
from it through `Flow`s exposed by repositories, and never waits on the network to render.

```
MediaStore ──scan──▶ Room ◀──sync (background)── remote backend (optional)
                      │
                      ▼ observe (Flow)
                 repositories ──▶ UI
                      │
                      ▼ content URI
               audio file, read at playback time
```

- **The library comes from the device.** Tracks are indexed from MediaStore into Room.
  Room stores metadata and the file's content URI, never the audio itself. Files are read from disk
  only when played.
- **Preferences are the exception.** User settings stay in DataStore (`:core:datastore`), which is
  their source of truth. They are key–value settings, not relational data.
- **Writes go to the local database first.** Favoriting a song, editing a playlist, or finishing a
  play updates Room immediately. Syncing happens afterwards in the background.
- **Sync writes into Room; it never sits on the read path.** The remote backend is reached only by
  the sync mechanism, which pulls remote changes into Room and pushes local changes out. No screen
  reads from the network directly.
- **Tracks have a portable identity key.** Every track gets an identity key that is the same on
  every device. It's the MusicBrainz recording ID when the file's tags include one, and otherwise a
  normalized combination of artist, album, title, track number and rounded duration. Remote data
  refers to tracks only by this key. IDs are Kotlin value classes (for example `TrackId`), so an
  identity key and a MediaStore ID can't be mixed up by accident.
- **Remote data only holds references.** Favorites, playlists and play history are synced as
  identity keys and metadata. Audio files never leave the device.
- **Unmatched references are kept.** A synced favorite or playlist entry for a track that isn't on
  this device is stored and shown as unavailable (or hidden). It isn't deleted, because deleting it
  would erase data the user created on another device.
- **Play history is a log of events.** Each play is recorded locally as an append-only event.
  Aggregates such as "most played this month" are computed from those events. When devices sync,
  their event logs are merged by union, so no plays are lost. A synced counter would lose plays when
  two devices are offline at the same time.
- **Signing in is optional.** `:core:auth` and `:core:sync` sit behind interfaces. When the user is
  signed out, sync does nothing and every feature still works.

Deferred to later ADRs: which remote backend to use, the sync schedule and mechanism, and the
detailed conflict rules for each type of data.

## Alternatives considered

- **Query MediaStore directly, with no local database.** It's already on the device, but it can't
  store favorites or play history, it can't be joined with our own tables, and the grouping queries
  a music library needs (albums by artist, and so on) are slow or impossible.
- **Network-first, with a cache for offline use.** The usual pattern for apps backed by a server,
  but it's the wrong fit when the files are local and the backend is optional. It would also put the
  network on the path that renders screens.
- **Fetch remote lists and resolve them against Room when a screen loads.** Simpler at first, but
  every screen that shows synced data would depend on the network being available and fast.
- **Match tracks by display strings, or by MediaStore ID.** Strings are fragile (see Context), and
  MediaStore IDs only mean something on the device that assigned them.
- **Upload audio files for cross-device playback.** Out of scope for an offline player, costly to
  store, and it raises licensing and privacy problems.

## Consequences

- The app behaves the same in airplane mode, and screens can be tested against a local database
  with no network fakes.
- Identity keys need a careful normalization function. That function is domain logic with its own
  tests, and changing it later needs a data migration.
- Two devices can briefly show different synced data until sync runs. We accept that eventual
  consistency.
- Keeping unmatched references means the UI must handle "a track that exists only on another
  device".
- A use case that combines several repositories (see [ADR-001](001-layered-architecture.md)) has to
  handle sources that are available at different times. Each part of a screen loads or fails on its
  own, and local parts never wait for remote ones.

## Rules

1. `[convention]` UI and ViewModels read data only through repositories backed by the local
   database (or DataStore for preferences), never from a network source.
2. `[convention]` User actions write to the local database first. Sync propagates the change later.
3. `[convention]` Only the sync mechanism talks to the remote backend.
4. `[convention]` Remote data refers to tracks by their portable identity key, never by MediaStore
   ID or display strings.
5. `[convention]` Track IDs are value classes, never raw `String`s or `Long`s, in domain and
   repository signatures.
6. `[convention]` Audio files are never uploaded or copied into the database.
7. `[convention]` Synced references to tracks that aren't present locally are kept, not deleted.
8. `[convention]` Play history is stored as append-only events, and aggregates are derived from them.
9. `[convention]` Every feature works while the user is signed out.

**Conformance.** Room, the library scan and a real sync implementation don't exist yet.
`SyncManager` has only a no-op implementation, and `AuthRepository` only a signed-out one. This ADR
sets the constraints those implementations must meet.
