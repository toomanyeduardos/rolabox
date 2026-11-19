# ADR-033: Firestore is provided once, by `:common:firestore`, which also turns its errors into `RemoteError`

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-022](022-data-source-sub-parts.md) (rule 3, which gains `:common:firestore`)
  and [ADR-020](020-modules-by-product-area.md) (rule 5, which gains an exception for
  `:network:impl` modules, and rule 9, whose list of single modules gains `:common:firestore`)

## Context

[podcasts/10](https://trello.com/c/YOaut7LJ) syncs follows through Firestore
([ADR-031](031-podcasts.md), Syncing follows), so `:device:podcasts:network:impl` will be the second
`:network:impl` that uses Firestore. The first, `:common:userdata:network:impl`, holds two things
that aren't about preferences:

- **The provider of `FirebaseFirestore`,** a `@Provides` in `PreferencesNetworkModule`. Hilt builds
  one graph for the app, so a second `@Provides FirebaseFirestore` in another module fails the
  build with a duplicate binding. A second `:network:impl` can only inject the one that is there.
- **The conversion of Firestore's exceptions,** `asRemoteError` and `asLoggedRemoteError` in
  `FirestoreErrors.kt`, which are `internal`. They decide which codes can pass and which are
  rejected ([ADR-022](022-data-source-sub-parts.md), `RemoteError`), log each one with Firestore's
  code ([ADR-029](029-sync-failures-are-logged.md), rule 2), and report a rejection to Crashlytics
  ([ADR-030](030-crash-reporting.md)). Two copies could disagree on what counts as offline.

ADR-022 left this for later: "Every `:network:impl` that uses Firestore turns its exceptions into
errors. There is one today, and where that is shared is decided with the second." It also turned
down a `:common:firestore` module that *wraps* Firestore, with each part giving it a mapping,
because a generic document API either shows Firestore's types or rebuilds them, and the
transaction that merges is specific to each kind of data. That reason still holds for the
documents. It doesn't apply to the instance and the errors, which are the same for every part.

What the decision has to fit:

- **Firestore is created only when a sync calls it** ([ADR-008](008-one-app-with-an-optional-account.md),
  rule 8). No-account mode never creates it.
- **An `:api` is a JVM module** ([ADR-020](020-modules-by-product-area.md), rule 6), and Firestore
  is an Android library. A module that shows `FirebaseFirestore` can't be an `:api`.
- **An `:impl` depends only on `:api` modules, `:common:util` and `:common:designsystem`**
  (ADR-020, rule 5), and **only `:app` depends on an `:impl`** (rule 4).

## Decision

We will add **`:common:firestore`**, a single Android module that **provides `FirebaseFirestore`**
and **turns Firestore's exceptions into `RemoteError`**. Only `:network:impl` modules depend on it.
It knows no path, document, field or transaction: those stay in each part's `:network:impl`.

### What it holds

- **The provider,** moved from `PreferencesNetworkModule`: `FirebaseFirestore.getInstance()`, in a
  Hilt module of its own. The `:network:impl` modules inject it as `Lazy<FirebaseFirestore>`, as
  `FirestorePreferencesNetworkDataSource` does today, so nothing creates it until a sync calls it
  (ADR-008, rule 8). Each `:network:impl` keeps its own test of that.
- **The conversion,** moved from `FirestoreErrors.kt` and made public: which Firestore codes are
  `RemoteError.Unavailable` and which are `RemoteError.Rejected`, the log line with the code, and
  the report of a rejection. Its tests move with it.
- **Nothing else.** It has no interface of its own and wraps no Firestore type. A `:network:impl`
  still uses Firestore's own API for its documents and transactions, and still declares the
  Firestore dependency it compiles against.

### Where it sits

- **A single module, like `:common:util`** (ADR-020, rule 9). Splitting it would give an `:api`
  that can't hold Firestore's types, and an `:impl` that `:network:impl` modules can't depend on.
- **Under `:common`,** since its users are in `:common` and in `:device`, and it depends only on
  `:common:sync:api`, for `RemoteError`, and `:common:util`, for `NonFatalReport` (ADR-020, rule 1).
- **Only `:network:impl` modules depend on it.** It's the one exception to ADR-020's rule 5, and
  it's narrow: an `:impl` that isn't a `:network:impl` still can't. `:app` gets its Hilt module
  through the `:network:impl` modules it already lists, and names it nowhere.
- **Firestore is a dependency of `:network:impl` modules and `:common:firestore`.** This widens
  ADR-022's rule 3 by one module. That no-account mode makes no Firebase request still rests on
  ADR-022's rule 6: only a `Syncer` calls a `:network:api` of synced data.

### The move

podcasts/10 does it, before the follows use Firestore:

1. Add `:common:firestore`, with the provider and the conversion, and their tests.
2. `:common:userdata:network:impl` depends on it, and loses its provider and `FirestoreErrors.kt`.
3. `ModuleRules.kt` allows Firestore in `:common:firestore`, allows `:network:impl` modules to
   depend on it, and refuses every other module that does, with a violating and a passing case each
   in `ModuleRulesTest`.
4. `:device:podcasts:network:impl` uses it for the follows.

## Alternatives considered

- **Reuse the provider without saying so, and copy the conversion.** No new module. Podcasts'
  `:network:impl` injects the `FirebaseFirestore` that userdata's module provides, and keeps its own
  copy of about 40 lines. But podcasts would break, with a missing binding, the day userdata's
  `:network:impl` stops providing it, and nothing in podcasts' build file says it needs it. Two
  copies of which codes are offline can drift, and ADR-029 and ADR-030 rely on them agreeing.
- **Move the provider to `:app`.** `:app` already assembles the graph. But `:app` would know
  Firestore, which only a `:network:impl` may, and the conversion still has no home.
- **A `:common:firestore` that wraps Firestore,** as ADR-022 turned it down: a generic document API
  that each part gives a mapping. Firestore would be in one module. But the API either shows
  Firestore's types or rebuilds them, and each kind of data merges in a transaction of its own.
  This ADR shares only what is the same for every part.
- **Split it into `:common:firestore:{api,impl}`.** It would look like every other part. But the
  `:api` can't hold `FirebaseFirestore` (ADR-020, rule 6), so the `:network:impl` modules would need
  the `:impl` anyway, which rule 4 forbids for anyone but `:app`.
- **Put the conversion in `:common:sync:api`,** next to `RemoteError`. One module fewer. But
  `:common:sync:api` is a JVM module, and the conversion needs Firestore's exception class and
  Android's `Log`.

## Consequences

- A third part that syncs through Firestore depends on `:common:firestore` and writes only its own
  mapping.
- Which Firestore codes are offline, and which are reported, is decided in one place.
- The app has one more module, and ADR-020's rule 5 has one more exception, which the build checks.
- Firestore is a dependency of two kinds of module, not one: the `:network:impl` modules and
  `:common:firestore`.
- `:common:firestore` compiles against Firestore and `:common:sync:api`, so a change to
  `RemoteError` rebuilds it, and every `:network:impl` that uses it.

## Rules

1. `[enforced]` `FirebaseFirestore` is provided only by `:common:firestore`. A second provider fails
   the Hilt graph of `:app` with a duplicate binding.
2. `[enforced]` Only `:network:impl` modules depend on `:common:firestore`. This is an exception to
   ADR-020's rule 5 for them alone.
3. `[enforced]` Firestore is a dependency of `:network:impl` modules and `:common:firestore` only.
   This replaces ADR-022's rule 3.
4. `[convention]` `:common:firestore` holds the provider of `FirebaseFirestore` and the conversion of
   its exceptions to `RemoteError`, and nothing else: no path, document, field or transaction.
5. `[convention]` A `:network:impl` turns every Firestore exception it converts with
   `:common:firestore`'s conversion, which logs it (ADR-029, rule 2) and reports a rejection
   (ADR-030), and injects `FirebaseFirestore` through `Lazy` or `Provider` (ADR-008, rule 8).

**Conformance.** Rules 2 and 3 are checked by `ModuleRules.kt` when the build is configured, with a
violating and a passing case each in `ModuleRulesTest` in `build-logic`. Rule 1 is checked by Hilt
when `:app`'s graph is compiled. Rules 4 and 5 are reviewed. That Firestore is created only when it
is called stays tested in each `:network:impl`: `FirestorePreferencesNetworkDataSourceTest`, and the
test of the follows' data source that podcasts/10 adds.
