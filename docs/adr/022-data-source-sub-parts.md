# ADR-022: A data part's local and network sides are sub-parts, and the part that owns synced data syncs it

- **Status:** Accepted
- **Date:** 2026-10-12
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-010](010-firestore-security-rules.md) (rule 7) and
  [ADR-011](011-preferences-sync.md) (rule 5, and where the `Syncer` and the Firestore mapping
  live)
- **Revised 2026-10-26:** The migration is done (39.05a). Rules 1, 3 and 4 are `[enforced]`, with
  their checks named under Conformance, and Target state and Consequences say so. The `Syncer`'s
  errors include `RemoteError`, which each `:network:impl` maps its SDK's exceptions to, so a
  `:network:api` and the runner share it through `:common:sync:api`. The decision is unchanged.

## Context

39.05 asked where the device's settings are stored. The answer is in
[ADR-011](011-preferences-sync.md)'s revision of the same date: they are preferences, owned by
`:common:userdata`. Asking which module owns them showed that the part that owns the preferences
doesn't own the whole of their path:

- **The remote half is in another part.** The Firestore document of the preferences, and the loop
  that merges them, are in `:common:sync:impl`. A new preference changes two parts, sync depends on
  `:common:userdata:api`, and that `:api` carries a repository that only sync uses.
- **The local half has no boundary.** `:common:userdata:impl` has one class that is the repository
  and also knows the stored format: the keys, the timestamps and the atomic merge.
- **Sync can't keep every kind of data.** [ADR-010](010-firestore-security-rules.md)'s rule 7 made
  `:common:sync:impl` the only code that uses Firestore, which works for one kind of data. ADR-011
  expects favorites, playlists, subscriptions and playback positions, and favorites belong to the
  library, a part of the device. `:common` depends on nothing outside `:common`
  ([ADR-020](020-modules-by-product-area.md), rule 1), so sync can't hold the mapping of data a
  device part owns.
- **The chain of abstractions stops short.** [ADR-021](021-data-flow-through-layers.md) reaches
  every layer through that layer's `:api`. The remote source of the preferences has an interface,
  but it is internal to sync, and the local one has none of its own.

The settings are the second set of synced fields, and favorites will be the first data that
`:common` can't own. The layout is decided now, before the fields are written in the old places.

**Target state.** The code has this layout since [39.05a](https://trello.com/c/zFqeUhYB), and
rules 1, 3 and 4 below are `[enforced]`. Until then ADR-010's rule 7 and ADR-011's rule 5
described the code as it was.

## Decision

We will give a data part's **local side** and **network side** a sub-part each, and make **the part
that owns a kind of synced data the one that syncs it**. Sync becomes a mechanism that names no
data.

### The layout

```
:common:userdata:api             repositories and models: what screens and other parts see
:common:userdata:impl            the repositories, and the Syncer of the preferences
:common:userdata:testing         fakes of the three :api modules
:common:userdata:local:api       the local data source, as an interface
:common:userdata:local:impl      it, over PreferencesStore: the keys, the timestamps, the atomic apply
:common:userdata:network:api     the remote data source, as an interface
:common:userdata:network:impl    it, over Firestore: the document's mapping and the transaction
```

- **A sub-part is a part.** It is split the way ADR-020 splits every part: its `:api` is a JVM
  module with an interface, its `:impl` implements and binds it, and only `:app` depends on the
  `:impl`. ADR-020's rules 4, 5 and 6 hold as they are written, and there is no new kind of module.
- **`:local` knows how the data is stored on the device.** For the preferences that is a format on
  top of `PreferencesStore`. `:common:storage` stays the one place that uses DataStore. Where Room's
  database, entities and DAOs live is still open (ADR-020, Left open).
- **`:network` knows the remote SDK:** the paths, the document's fields and the transaction.
- **The part's `:impl` combines the two.** Its repositories read and write the local side only
  ([ADR-002](002-offline-first-data-flow.md), rules 1 and 2). Its `Syncer` uses both.
- **A sub-part's `:api` belongs to its part.** It is seen by the part's `:impl` and `:testing`, and
  by the sub-part's own `:impl`. Screens and other parts see the part's `:api` and nothing under it.
- **Types.** A sub-part's `:api` uses the models of the part's `:api`. SDK and storage types stay
  in the sub-part's `:impl` and are mapped at its boundary (ADR-021).
- **Only when there is such a side.** A part gets a `:local` or a `:network` when it has that side,
  and has no empty module (ADR-020, rule 9). `:common:userdata` is split now. `:auth:data:impl`
  calls Firebase Auth itself, and `:device:library:impl` is in memory: each is split by the ticket
  that needs it, and none is planned here.

### The owner of the data syncs it

- **`:common:sync:api` declares what a kind of synced data implements,** the `Syncer`, with the
  errors a sync can end in. A part with synced data implements it in its `:impl` and adds it to a
  Hilt set.
- **`:common:sync:impl` keeps the mechanism:** the triggers, the WorkManager work, the runner with
  its check of who to sync for, and last-write-wins. It declares the set, so that it may be empty,
  and it depends on no part whose data it syncs.
- **The dependency turns around.** Sync depended on `:common:userdata:api`. Now
  `:common:userdata:impl` depends on `:common:sync:api`, as it already does for `SyncedValue`.
- **Firestore is used only in `:network:impl` modules.** This replaces ADR-010's rule 7. The rest
  of ADR-010 holds: all data is under `users/{uid}`, and every document has rules and tests.
- **A `:network:api` of synced data is called only by its part's `Syncer`,** and the runner is the
  only caller of a `Syncer`. So the sync path is still the only path to Firebase, and offline mode
  still makes no request ([ADR-008](008-one-app-with-offline-mode.md), rule 6). Firestore is
  injected lazily in the `:network:impl` (ADR-008, rule 8).
- **ADR-011's mechanism is unchanged:** the timestamps, last-write-wins, the transaction, the
  atomic apply and the triggers.
- **A missing `Syncer` doesn't fail the build.** `:app`'s test of the assembled graph asserts the
  ones that should be there (ADR-020, rule 20).

### Migration ([39.05a](https://trello.com/c/zFqeUhYB))

Each step leaves the build green.

1. Move the `Syncer` contract and its errors to `:common:sync:api`. `:common:sync:impl` declares
   the set.
2. Add `:common:userdata:network`, and move the Firestore mapping of the preferences and the
   provider of Firestore into its `:impl`.
3. Add `:common:userdata:local`, and move the stored format out of the repository.
4. Move the `Syncer` of the preferences to `:common:userdata:impl`. `:common:userdata:api` loses
   the repository that only sync used, and `:common:sync:impl` its dependency on userdata.
5. Add the checks of rules 1, 3 and 4 to `ModuleRules.kt`, with a violating and a passing case each,
   and the `Syncer` to `:app`'s test of the assembled graph. The tests of ADR-008's rules 6 and 8
   move with the code they cover.
6. Update what names the old layout, as dated revisions: the module list of ADR-020, the
   conformance notes of ADR-002, ADR-008, ADR-010 and ADR-011, and `README.md`'s module table and
   graph.

## Alternatives considered

- **Keep it as it is:** the preferences in `:common:userdata`, their Firestore mapping in
  `:common:sync:impl`. No migration, and the settings would be five more fields. But every field
  is written in two parts, and the layout fails at the first synced data a device part owns.
- **`:local` and `:network` as single modules, with their interfaces in the part's `:api`.** Five
  modules, not seven, and ADR-020's rule 5 unchanged. But every module that sees the `:api`,
  screens included, would see the data sources.
- **`:local` and `:network` as single modules, each with a public interface and an internal
  implementation.** Five modules, and nothing under the part is public. But the part's `:impl`
  would depend on a module that isn't an `:api`, an exception to ADR-020's rule 5 that the build
  check has to know, and it would compile against a module that applies Firebase.
- **Only `:network` as a sub-part,** with the local interface in the part's `:api`. One module
  fewer. But the two sides would follow different rules for no reason a reader can see.
- **Keep the `Syncer` in `:common:sync:impl`, calling each part's `:network:api`.** Fewer moves,
  and it works while every synced part is in `:common`. But sync still grows with every kind of
  data, and it can't name a device part's `:api` at all.
- **One `:common:firestore` module that wraps Firestore,** as `:common:storage` wraps DataStore,
  with each part giving it a mapping. Firebase would stay in one module. But a generic document API
  either shows Firestore's types or rebuilds them, and the transaction that merges is specific to
  each kind. It can be looked at again when there is a second `:network:impl`.
- **Split every data part now.** One layout everywhere at once. But `:auth:data` and
  `:device:library` have no ticket that needs it, and it would be a large refactor ahead of the
  settings.

## Consequences

- A new preference changes one part, and the security rules.
- Sync can take data a device part owns, with no dependency from `:common` to the device.
- `:common:userdata:api` holds only what screens and other parts use.
- **One part is seven modules,** and the app is near forty. `:app` lists two more `:impl`
  modules for each split part, and configuration time and the IDE's project view grow.
- Data crosses two more boundaries, each with a mapping.
- **Firebase is no longer in one module, but in one kind of module.** That offline mode makes no
  request rests on rule 6 and on who may see a `:network:api`, where it rested on Firestore being
  in sync alone. Its tests are in two places: the runner runs nothing when there is nobody to sync
  for, and the `:network:impl` creates Firestore only when it is called.
- Every `:network:impl` that uses Firestore turns its exceptions into errors. There is one today,
  and where that is shared is decided with the second.
- **Two layouts live side by side.** `:auth:data:impl` still calls its SDK itself, and nothing says
  when that changes.
- Until 39.05a, two rules of other ADRs were superseded on paper and still true of the code.

## Rules

1. `[enforced]` A data part's local data source is `:<part>:local:{api,impl}` and its
   remote one is `:<part>:network:{api,impl}`. A sub-part's `:api` is depended on only by the
   `:impl` and the `:testing` of its part, and by its own `:impl`.
2. `[convention]` A part has a `:local` or a `:network` only when it has that side. Its
   repositories read and write the local side only, and only its `Syncer` uses both.
3. `[enforced]` Firestore is a dependency of `:network:impl` modules only.
4. `[enforced]` No `:common:sync` module depends on a `:common:userdata` module, or on any
   other part whose data it syncs.
5. `[convention]` Each kind of synced data is synced by a `Syncer` in the `:impl` of the part that
   owns it, added to the set that `:common:sync:impl` declares. Sync runs only through WorkManager
   work, requested by `SyncRepository`.
6. `[convention]` A `:network:api` of synced data is called only by its part's `Syncer`. An SDK
   object that can open a connection is injected through `Provider` or `Lazy` (ADR-008, rule 8).
7. `[convention]` A sub-part's `:api` uses the models of its part's `:api`. SDK and storage types
   stay in the sub-part's `:impl`.
8. `[convention]` `:app`'s test of the assembled graph asserts every `Syncer` that should be
   present (ADR-020, rule 20).

**Conformance.** Rules 1, 3 and 4 are checked by `ModuleRules.kt` when the build is configured:
rules 1 and 4 on project dependencies, and rule 3 on the Firestore artifact, each with a violating
and a passing case in `ModuleRulesTest` in `build-logic`. Rule 1 is checked by the path: a
dependency on `:<part>:local:api` or `:<part>:network:api` is allowed only from `:<part>:impl`,
`:<part>:testing` and the sub-part's own `:impl`. Rule 4 is checked for `:common:userdata`; ADR-020's
rule 1 already keeps sync from every part outside `:common`. Rules 2, 5, 6 and 7 are reviewed, and
rule 8 is `AssembledGraphTest` in `:app`, which checks that the set has the `Syncer` of the
preferences and no other. That Firestore is created only when it is called is
`FirestorePreferencesNetworkDataSourceTest` in `:common:userdata:network:impl`.
