# ADR-011: Sync preferences through Firestore, field by field, with last-write-wins

- **Status:** Accepted
- **Date:** 2026-09-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

[ADR-002](002-offline-first-data-flow.md) left three things to later ADRs: the sync schedule and
mechanism, and the conflict rules for each kind of data. [ADR-010](010-firestore-security-rules.md)
chose Firestore to store synced data. The first data to sync is the user's preferences: the dark
theme setting and the accent color. Favorite artists, playlists, subscriptions and playback
positions come later, so the mechanism has to take more kinds of data without being redesigned.

The constraints:

- **Local storage stays the source of truth.** Preferences live in DataStore, behind
  `PreferencesStore` (ADR-002). Screens read them from there and never wait on the network.
- **Devices are offline for long stretches.** A phone on a plane and a tablet at home can both
  change the theme before either one syncs. Sync has to settle both changes the same way on both
  devices, and a change made offline has to reach the cloud once there's a connection, even if the
  app was killed in between.
- **There's no server.** Devices talk to Firestore directly (ADR-010), so the merge runs on each
  device, and the security rules are the only check on what a device writes.
- **Preferences are independent of each other.** Changing the theme on one device and the accent
  color on another are two changes, not a conflict.
- **Signing in is optional** (ADR-002, rule 9). Preferences set while signed out still count.

## Decision

We will sync each preference **field by field, with last-write-wins on a timestamp** that the
changing device records. Sync runs in the cloud flavor, as WorkManager work.

**Every synced field carries when it last changed.** `SyncedValue<T>(value, updatedAt)` in
`:core:sync:api` pairs a value with a `SyncTimestamp`: epoch milliseconds from the clock of the
device that made the change. The timestamp is stored next to the value, locally (DataStore keys
`<key>` and `<key>_updated_at`, written in one atomic update) and remotely. Milliseconds, because
Firestore keeps microseconds, and a finer local value would never compare equal after a round trip.

**Last-write-wins, per field.** `LastWriteWins.resolve` in `:core:sync:api` keeps the more recent
of the local and remote values:

- A field **never set** on a device has no `SyncedValue` (null), and never wins. A new device
  can't overwrite the cloud with its defaults.
- A value stored **before timestamps existed** counts as `SyncTimestamp.Epoch`, older than any real
  change.
- On a **tie**, the remote value wins, so every device that merges the same pair keeps the same
  one.

**Collections merge item by item.** `LastWriteWins.mergeById` resolves each item on its own, keyed
by id. A removed item is kept as a **tombstone** (a null value with the time it was removed), so a
removal beats an older add, and an add beats an older removal. Favorite artists, playlists and
subscriptions will use it. Replacing a whole collection with last-write-wins would drop an item
added on one device while another was offline.

**How a sync runs.** Each kind of data has a `Syncer` in `:core:sync:impl`, bound into a Hilt set.
`PreferencesSyncer`:

1. reads the local `SyncedPreferences` from `SyncedPreferencesRepository` (`:core:userdata:api`);
2. merges them with the remote copy in a **Firestore transaction**, writing back only the fields
   the remote copy lost. If another device writes in between, the transaction starts over;
3. applies the merged result locally. `applySyncedPreferences` resolves each field against what is
   stored at that moment, in the same atomic update as the write, so a change the user makes while
   a sync is running is never overwritten by an older value.

**Where data lives in Firestore.** Preferences are one document, `users/{uid}/settings/preferences`,
with a map per field: `{ value: <enum name>, updatedAt: <timestamp> }`. Values are the enum names,
which are stored on devices and in the cloud, so they are never renamed. The rules
(`firebase/firestore.rules`) allow only known fields and values, and reject an `updatedAt` more
than a day ahead of the server, a field moved back in time, or a field removed. Later kinds of
data get their own collections under `users/{uid}`, each with rules and tests (ADR-010, rule 3).
Favorite artists wait for the library area, which will own their identity (`ArtistId`,
[ADR-003](003-module-boundaries.md) rule 12), and for Room, where ADR-002 keeps favorites.

**When sync runs.** `SyncRepository.start()`, called from `Application.onCreate`, requests a sync:

- **on sign-in**, including a user already signed in when the app starts;
- **after local changes**, once they have settled for two seconds (debounced);
- **when the app comes to the foreground** (`ProcessLifecycleOwner`), to pick up changes from other
  devices.

A request enqueues unique WorkManager work that waits for a network connection and **replaces**
pending or running work. Syncing is idempotent, so a cancelled run loses nothing, and the new one
reads the latest local state. Failures that can pass (network, contention, storage unavailable)
are retried with WorkManager's backoff. Others (rules rejecting a write, corrupted storage) fail
until the next trigger.

**The offline queue is local storage itself.** A change made offline is already in DataStore with
its timestamp. When the pending work runs, the merge pushes whatever is newer locally. There is no
separate queue of operations to persist or replay, and the pending work survives the process being
killed.

**The worker gets its dependencies from a Hilt entry point.** WorkManager creates workers itself.
`@HiltWorker` would need `:app` to provide WorkManager's configuration, in `src/main`, which would
put WorkManager in the offline app too. The worker is created by WorkManager like an Activity is by
Android, so it's an Android entry point in the sense of [ADR-005](005-hilt-dependency-injection.md)
rule 1.

**Only the cloud flavor syncs.** WorkManager, `lifecycle-process` and Firestore are
`cloudImplementation` dependencies of `:core:sync:impl`. The offline flavor binds
`NoOpSyncRepository` ([ADR-008](008-offline-and-cloud-flavors.md)).

**Signing out keeps local preferences.** They're the device's settings. Signing in again, as the
same user or another one, merges them with that user's cloud copy.

## Alternatives considered

- **Last-write-wins on the whole preferences document.** Simpler: one timestamp, one comparison. But
  changing the theme on one device and the accent color on another, offline, would lose one of them.
  The fields are independent, so their timestamps are too.
- **Server timestamps (`FieldValue.serverTimestamp()`) instead of device clocks.** They're immune to
  wrong device clocks. But a change made offline would be stamped when it reaches the server, not
  when the user made it, so an old offline change would beat a newer online one. The timestamp also
  isn't known until the write lands, so the local copy couldn't compare against it.
- **Hybrid logical clocks, or version vectors.** Order changes correctly even with skewed clocks,
  and detect true concurrency. They're much more machinery than a handful of settings needs, and a
  user who changes a setting twice on two devices within seconds is hard to surprise either way. We
  can revisit this for data where ordering matters more.
- **CRDTs for collections (an OR-Set).** Merge adds and removes without tombstones that outlive the
  item. Per-item last-write-wins with tombstones gives the same result for favorites and
  subscriptions, and is easier to validate in security rules.
- **Firestore's offline persistence as the local store.** Firestore caches documents and queues
  writes on its own. But then Firestore would be the source of truth, and the offline flavor, which
  has no Firestore, would need a second implementation. That contradicts ADR-002 and ADR-010.
- **Queue the operations (an outbox table) and replay them.** Records exactly what the user did. But
  replay order, deduplication and retries are more code, and for last-write-wins data the current
  value and its timestamp already carry everything a merge needs.
- **Firestore snapshot listeners while the app is open.** Changes from other devices would appear
  right away. It would keep a connection open, and costs a read for every change, for settings that
  rarely change. Syncing when the app comes to the foreground is enough for now.
- **`@HiltWorker` with a WorkManager configuration in `:app`.** The standard Hilt integration, and
  what ADR-005 expected for sync. It needs `Configuration.Provider` on the `Application` and the
  default initializer removed from the manifest, which puts WorkManager in the offline app. The
  entry point keeps it in the cloud flavor, at the cost of one lookup in `doWork`.
- **Name the API `SyncManager`, as before.** It had one method, `requestSync()`. We renamed it to
  `SyncRepository` so that sync is named like every other area's API, and it now also starts the
  triggers.

## Consequences

- Preferences follow the user across devices. Screens don't change: they still observe
  `UserDataRepository`, and sync writes through the same storage.
- **A wrong device clock can make an old change win.** A clock that runs ahead makes that device's
  changes win until real time catches up. The rules cap how far ahead (a day), and reject writes
  beyond that, so a device with a badly wrong clock fails to sync until its clock is fixed.
- **The last change wins, not the most deliberate one.** Two changes to the same setting on two
  devices keep only the later one, without asking. That's acceptable for preferences, and may not be
  for richer data such as playlist edits.
- **Tombstones stay forever** for now. They're a few bytes per removed item. Pruning them safely
  needs to know every device has synced, so it waits until it's needed.
- **Switching accounts merges preferences.** Signing out of one account and into another carries
  the device's preferences into the second account's cloud copy wherever they're newer.
- **A value from a newer version** reads as the default on an older one, locally and remotely. The
  older version doesn't write that field back unless its own change is newer, and the rules only
  accept values they know, so a new value needs a rules deploy before the app release that writes
  it.
- Applying a remote change is itself a local change, so it triggers one more sync, which reads and
  finds nothing to write. That costs a Firestore read, not a write.
- Sync failures are invisible. There's no sync status in the UI, and no logging. A rejected write
  shows up only in WorkManager's state.
- **A local read error stops the "after local changes" trigger** until the app restarts. The
  observed flow ends on its first error (ADR-007), and resubscribing would loop on a corrupted
  file. Sign-in and foreground still trigger sync, and each sync reports the same error itself.
- Every kind of synced data needs a `Syncer`, a local repository that can store timestamps, a
  Firestore mapping, and rules with tests. That's the cost of adding data to sync, and it's intended.

## Rules

1. `[convention]` Every synced value carries an `updatedAt` from the device that changed it, stored
   in the same atomic write as the value, in milliseconds.
2. `[convention]` Conflicts are resolved with `LastWriteWins`: per field for records, and per item,
   with tombstones for removals, for collections. Merges run on the device.
3. `[convention]` Applying remote data locally resolves each field or item against what's stored at
   that moment, in the same atomic update as the write.
4. `[convention]` Remote writes happen in a Firestore transaction that merges with the current
   remote data, and write only what changed.
5. `[convention]` Each kind of synced data is a `Syncer` bound into the set in the cloud
   `SyncModule`. Sync runs only through WorkManager work, requested by `SyncRepository`.
6. `[enforced]` Rules reject a synced field with an unknown value, a timestamp more than a day ahead
   of the server, a timestamp older than the stored one, or a removed field. The rules tests check
   this.
7. `[convention]` Synced enum values are stored by name, and a name is never renamed or reused.

**Conformance.** Preferences (theme and accent color) sync. Collections have the merge function and
its tests, but no data uses it until favorite artists arrive with the library area. Rule 6 is
checked by `firebase/test/firestore.rules.test.mjs` in CI.
