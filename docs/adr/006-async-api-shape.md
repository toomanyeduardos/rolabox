# ADR-006: Async API shape: `Flow` for observed state, `suspend` for single operations

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Every repository and use case has to decide how it exposes asynchronous work: as a `Flow`, as a
`suspend` function, or as a blocking call. If each one decides differently, callers can't tell at a
glance whether a function returns a value once or keeps updating, which thread it may block, or
whether its work survives the screen that started it.

[ADR-002](002-offline-first-data-flow.md) sets the direction: the UI observes the local database and
never waits on the network. Not every read is an observation, though. Starting playback needs the
track's file once, and sync needs a snapshot of local changes. Forcing those to be `Flow`s leads to
`.first()` scattered through the code.

How failures appear in these signatures is decided separately in
[ADR-007](007-error-handling.md). The examples below include it.

## Decision

**Observed state is returned as a `Flow`, from a function that isn't `suspend`.** Anything the UI
displays and should update when the data changes:

```kotlin
fun observeFavorites(): Flow<Either<DatabaseError, List<Track>>>
```

A stream with no parameters may be a property instead (`val userData: Flow<…>`). Functions that
return a `Flow` are named `observe…`.

**Single lookups and writes are `suspend` functions.** A read whose value is needed once at that
moment, and every write:

```kotlin
suspend fun getTrack(id: TrackId): Either<DatabaseError, Track>
suspend fun setFavorite(id: TrackId): Either<DatabaseError, Unit>
```

A `Flow`-returning function is never `suspend`. Creating a cold `Flow` does no work, so the modifier
only misleads callers.

**Everything is main-safe.** Callers never need `withContext`. Repositories switch to the injected
dispatcher themselves ([ADR-005](005-hilt-dependency-injection.md)): `withContext` for `suspend`
functions, `flowOn` for flows.

**Repositories return cold flows; ViewModels share them.** A ViewModel turns a repository or use case
flow into UI state with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(…), initial)`.
Repositories don't cache hot flows themselves.

**Work that must finish runs outside the screen's scope.** `viewModelScope` is cancelled when the
user leaves the screen. A write that must finish regardless, such as recording a play event, runs in
an injected application-level `CoroutineScope`. Work that must survive the process dying, such as
sync, runs in WorkManager. `GlobalScope` is never used.

## Alternatives considered

- **Every read is a `Flow`.** One rule, but single lookups turn into `.first()` calls. That hides the
  intent, and it's easy to collect a flow that never completes by mistake.
- **Every read is a `suspend` function that returns a snapshot.** Simple for callers, but the UI no
  longer updates by itself when the data changes, which breaks the offline-first model in ADR-002.
- **Callbacks or blocking calls wrapped by callers.** Not idiomatic Kotlin, hard to cancel, and each
  caller has to know which thread is safe.
- **Hot `StateFlow`s cached in repositories.** Avoids repeated queries, but repositories then have to
  manage lifecycles and scopes. Sharing in the ViewModel keeps that tied to the screen that needs it.

## Consequences

- From a signature alone, a caller knows whether it gets one value or keeps receiving them, and that
  it can call it from the main thread.
- Deciding whether a read is "observed" or a "single lookup" is a judgment call, made visible by
  the `observe…`/`get…` naming.
- Each ViewModel needs its own `stateIn` call. That's a little repetition in exchange for clear
  ownership of the lifecycle.
- An application-level scope has to be provided through Hilt before the first write that must finish
  regardless of the screen.

## Rules

1. `[enforced]` No `suspend` function returns a `Flow` (detekt `SuspendFunWithFlowReturnType`).
2. `[convention]` Observed state is a non-`suspend` `Flow`. Single lookups and writes are `suspend`
   functions.
3. `[convention]` `Flow`-returning functions are named `observe…`. Single lookups are named `get…`.
4. `[convention]` Repository and use case functions are main-safe. They switch to an injected
   dispatcher internally.
5. `[convention]` Repositories return cold flows. ViewModels share them with `stateIn` and
   `WhileSubscribed`.
6. `[convention]` Work that must outlive a screen runs in the injected application scope or in
   WorkManager.
7. `[enforced]` `GlobalScope` is never used (detekt `GlobalCoroutineUsage`).
