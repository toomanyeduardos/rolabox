# ADR-011: Sync preferences through Firestore, field by field, with last-write-wins

- **Status:** Accepted. Rule 5, and where the `Syncer` and the Firestore mapping live, superseded by
  [ADR-022](022-data-source-sub-parts.md)
- **Date:** 2026-09-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-28:** [ADR-008](008-one-app-with-offline-mode.md) replaced the offline and cloud
  flavors with one app, where offline is a choice the user makes. Sync now runs for a signed-in user
  who hasn't chosen offline mode, instead of "in the cloud flavor", and `SyncRunner` checks both
  (Decision, new rule 8). The reason given for the Hilt entry point, keeping WorkManager out of the
  offline app, no longer applies. The entry point stays, since it works, and moving to `@HiltWorker`
  would be a separate change (Decision, Alternatives considered).
- **Revised 2026-09-28:** Sync now checks offline mode (`m1/flavor-removal`), so rule 8 holds. The
  syncers are bound in their own Hilt module, `SyncersModule`, so a test that replaces the
  `SyncRepository` keeps the worker's graph complete (rule 5). The triggers are observed only while
  sync is allowed, so a local read error stops the "after local changes" trigger until the next
  sign-in, not until the app restarts (Consequences). The decision is unchanged.
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). Conformance:
  whether sync may run is no longer decided by a function inside sync. `:common:sync:api` declares
  who to sync for, and `:auth:data:impl` provides it, so rule 8 is tested in both. The decision is
  unchanged.
- **Revised 2026-10-12:** The device's settings are preferences (Decision, Alternatives,
  Consequences, rules 9 to 11). 39.05 asked where Clicker, the main menu's rows, and later Shuffle
  and Repeat are stored. They are more fields of the same document, written locally first like the
  theme, and all of them sync. A fixed set of switches, such as the main menu's rows, is a field
  each and not a collection. The mechanism is unchanged. The fields are not in the code until
  39.05b. Which module holds the `Syncer` and the Firestore mapping changed on the same date, in
  [ADR-022](022-data-source-sub-parts.md): the text below is left as it was written.

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
changing device records. Sync runs as WorkManager work, for a signed-in user who hasn't chosen
offline mode.

**Every synced field carries when it last changed.** `SyncedValue<T>(value, updatedAt)` in
`:common:sync:api` pairs a value with a `SyncTimestamp`: epoch milliseconds from the clock of the
device that made the change. The timestamp is stored next to the value, locally (DataStore keys
`<key>` and `<key>_updated_at`, written in one atomic update) and remotely. Milliseconds, because
Firestore keeps microseconds, and a finer local value would never compare equal after a round trip.

**Last-write-wins, per field.** `LastWriteWins.resolve` in `:common:sync:api` keeps the more recent
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

**How a sync runs.** Each kind of data has a `Syncer` in `:common:sync:impl`, bound into a Hilt set.
`PreferencesSyncer`:

1. reads the local `SyncedPreferences` from `SyncedPreferencesRepository` (`:common:userdata:api`);
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

**The device's settings are preferences.** The settings in the display
([ADR-018](018-device-navigation.md)) are stored like the theme: `:common:userdata` owns them, they
are written to DataStore first, with their timestamp, and they sync as fields of the same document.
So they work signed out and in offline mode, where nothing syncs
([ADR-008](008-one-app-with-offline-mode.md)). `:device:settings` has the screens that change them
and stores nothing. The parts of the device that use a setting read it from
`:common:userdata:api`.

| Field | Values | Never set reads as |
| --- | --- | --- |
| `darkThemeConfig` | `FOLLOW_SYSTEM`, `LIGHT`, `DARK` | `FOLLOW_SYSTEM` |
| `accentColor` | `BLUE`, `GREEN`, `PURPLE`, `PINK` | `BLUE` |
| `clicker` | `ON`, `OFF` | `ON` |
| `mainMenuMusic` | `SHOWN`, `HIDDEN` | `SHOWN` |
| `mainMenuPodcasts` | `SHOWN`, `HIDDEN` | `SHOWN` |
| `mainMenuAudiobooks` | `SHOWN`, `HIDDEN` | `SHOWN` |
| `mainMenuShuffleSongs` | `SHOWN`, `HIDDEN` | `SHOWN` |

Every field is the same map, `{ value: <enum name>, updatedAt: <timestamp> }`, a switch too. So the
rules and the two mappings have one kind of value, and a switch can gain a third value without
changing its type. Shuffle and Repeat follow the account and will be two more fields of this
document, added when their values are decided (39.11).

**A fixed set of switches is a field each, not a collection.** The main menu's rows are a list on
the screen, but the user adds and removes nothing: the rows are fixed
([ADR-020](020-modules-by-product-area.md), rule 15), and each one is shown or hidden. So each row
that can be hidden is a field, resolved on its own. Hiding Podcasts on one phone and Audiobooks on
another, both offline, keeps both. There are no tombstones, and a row added by a later version is a
new field that reads as shown until it is set. `mergeById` stays for collections whose items the
user creates.

**A preference syncs unless it belongs to this installation.** Clicker syncs: it is a taste, like
the theme, and the click is silent anyway when the phone is. The one value that doesn't sync is
whether offline mode was chosen, which says how this installation is used and not what the user
likes. A value like that has no timestamp and is never part of what sync reads.

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
The worker is created by WorkManager like an Activity is by Android, so it's an Android entry point
in the sense of [ADR-005](005-hilt-dependency-injection.md) rule 1. It was chosen over `@HiltWorker`
to keep WorkManager out of the offline flavor, which no longer exists
([ADR-008](008-one-app-with-offline-mode.md)). Moving to `@HiltWorker` is now possible, and is left
to a separate change.

**Sync never runs in offline mode.** `SyncRunner` runs the `Syncer`s only when the user is signed
in and hasn't chosen offline mode, and the triggers request no work in offline mode. So offline
mode makes no Firebase requests ([ADR-008](008-one-app-with-offline-mode.md), rule 6).

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
  writes on its own. But then Firestore would be the source of truth, which contradicts ADR-002 and
  ADR-010.
- **Queue the operations (an outbox table) and replay them.** Records exactly what the user did. But
  replay order, deduplication and retries are more code, and for last-write-wins data the current
  value and its timestamp already carry everything a merge needs.
- **Firestore snapshot listeners while the app is open.** Changes from other devices would appear
  right away. It would keep a connection open, and costs a read for every change, for settings that
  rarely change. Syncing when the app comes to the foreground is enough for now.
- **`@HiltWorker` with a WorkManager configuration in `:app`.** The standard Hilt integration, and
  what ADR-005 expected for sync. It needs `Configuration.Provider` on the `Application` and the
  default initializer removed from the manifest, which put WorkManager in the offline app while
  that flavor existed. The entry point kept it in the cloud flavor, at the cost of one lookup in
  `doWork`. That reason is gone since ADR-008's 2026-09-28 revision.
- **A document of their own for the device's settings,** `users/{uid}/settings/device`. It keeps
  the theme and the device's settings apart in the cloud. But they are merged by the same code at
  the same moments, and a second document is a second read and a second transaction on every sync.
- **A data part of the device that owns its settings.** They would live with the device that shows
  them. But the theme and the accent color are set from the same menu and are already here, several
  parts of the device read them, and there would be two stores of preferences on the device.
- **The main menu's rows as a collection, merged by id.** It is the mechanism this ADR has for
  lists. But a row is never added or removed, so a tombstone would mean "hidden" and say it less
  plainly than a value does.
- **The main menu's rows as one field holding the list of hidden rows.** One field to validate.
  But it is last-write-wins on the whole list, the loss this ADR rejects for the document.
- **Booleans for the switches.** Closer to what On and Off are. But the rules and both mappings
  would have a second kind of value, and a switch that gains a third value, as Clicker did on later
  players, would have to change its type in a document where a field is never removed.
- **Clicker as a setting of this phone, never synced.** A phone and a tablet could differ. But
  silent mode already silences the click on one device, and a second kind of device setting needs a
  rule for telling the two apart on every new one.
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
- **A local read error stops the "after local changes" trigger** until the next sign-in, or until
  the app restarts. The observed flow ends on its first error (ADR-007), and resubscribing right
  away would loop on a corrupted file. Sign-in and foreground still trigger sync, and each sync
  reports the same error itself.
- **`:common:userdata:api` names things of the device:** the click, and the rows of the main menu
  that can be hidden. The main menu in `:device:ui:impl` is still the list of what the product has,
  and it maps its rows to these.
- **A row that leaves the main menu leaves its field behind.** A field is never removed (rule 6),
  so the document keeps it, unread.
- **The rules are deployed before the app that writes the new fields,** as for a new value. Until
  then that app's syncs are rejected, and its settings stay on the device.
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
5. `[convention]` Each kind of synced data is a `Syncer` bound into the set in `SyncersModule`.
   Sync runs only through WorkManager work, requested by `SyncRepository`.
6. `[enforced]` Rules reject a synced field with an unknown value, a timestamp more than a day ahead
   of the server, a timestamp older than the stored one, or a removed field. The rules tests check
   this.
7. `[convention]` Synced enum values are stored by name, and a name is never renamed or reused.
8. `[convention]` `SyncRunner` runs `Syncer`s only for a signed-in user who hasn't chosen offline
   mode, and the triggers request no work in offline mode (ADR-008, rule 6). Tests cover both.
9. `[convention]` A set of switches that the user can't add to or remove from, such as the rows of
   the main menu, is a synced field for each switch. `mergeById` is for collections whose items the
   user creates.
10. `[convention]` A preference syncs unless it describes this installation and not the user's
    choice, as offline mode does. A value that doesn't sync has no timestamp and isn't part of what
    sync reads.
11. `[planned]` (39.05b) The device's settings are preferences: `:common:userdata` stores them, and
    they sync as fields of `users/{uid}/settings/preferences`, with the names and values of the
    table in Decision. No `:device` module stores a setting. The rules tests cover each field as
    rule 6 says.

**Target state.** Since the revision of 2026-10-12, rule 11 is ahead of the code until
[39.05b](https://trello.com/c/gjuKXyIz): only
the theme and the accent color are stored and synced. Rule 5, and the modules this ADR names for
the `Syncer` and the Firestore mapping, describe the code until
[39.05a](https://trello.com/c/zFqeUhYB) moves it
([ADR-022](022-data-source-sub-parts.md)).

**Conformance.** Preferences (theme and accent color) sync. Collections have the merge function and
its tests, but no data uses it until favorite artists arrive with the library area. Rule 6 is
checked by `firebase/test/firestore.rules.test.mjs` in CI. Rule 8 is covered by `SyncTriggersTest`
and `SyncRunnerTest`, which obey the `SyncUserProvider` of `:common:sync:api`, and by
`AuthSyncUserProviderTest` in `:auth:data:impl`, which implements it. That is the one place that
decides whether sync may run ([ADR-020](020-modules-by-product-area.md), rule 18).
