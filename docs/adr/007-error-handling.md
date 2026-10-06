# ADR-007: Typed errors with Arrow `Either` for every fallible operation

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-26:** Every error type is now declared in the `:api` module of the area that
  owns it ([ADR-003](003-module-boundaries.md)), instead of all in `:core:domain`. Local storage
  errors became `StorageError` in `:core:storage:api` (it was `DatabaseError`, but storage isn't
  necessarily a database). The shared `FetchError` parent was removed. A sealed parent forces every
  infrastructure error into one module, and that module would have had no owner. Nothing relied on
  the parent. `AuthRepository.observeCurrentUser()` now returns a plain `Flow`, because reading the
  cached auth state can't fail. The exception-to-error helpers are in `:core:common`, and fakes are
  in each area's `:testing` module.
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). `StorageError` is
  in `:common:storage:api`, and the exception-to-error helpers in `:common:util` (Decision,
  Consequences). The decision is unchanged.

## Context

Kotlin has only unchecked exceptions. Nothing in a function's signature says whether it can fail,
so callers have to *remember* which functions throw. In practice, that memory fails: a call that
"never throws" throws after a refactor, an unhandled exception reaches the UI, and the app crashes.
The fix is to put failure in the type, so the compiler makes every caller deal with it.

Kotlin's standard `Result` type doesn't solve this. Its error type is always `Throwable`, so it says
nothing about *which* failures can happen. Its companion `runCatching` also catches
`CancellationException`, which breaks coroutine cancellation.

Rolabox's failures fall into two groups:

- **Infrastructure failures:** local storage (full, corrupted, unavailable) and, later, the
  network.
- **Area-specific failures:** a playlist name that's already taken, a track whose file is missing,
  a sign-in the user cancelled.

## Decision

We will use **Arrow's `Either`**, with a **specific sealed error type** on the left, for every
operation that can fail. That includes reads, writes and observed flows.

```kotlin
fun observeFavorites(): Flow<Either<StorageError, List<Track>>>
suspend fun setFavorite(id: TrackId): Either<StorageError, Unit>
suspend fun createPlaylist(name: String): Either<PlaylistError, PlaylistId>
```

A write with nothing to return succeeds with `Unit`. Every call site still unwraps the result and
handles the error. That's the point: no caller has to remember whether a function can fail.

**Where error types live.** Error types are sealed interfaces, each declared in the `:api` module of
the area that owns it ([ADR-003](003-module-boundaries.md)), next to the repository interfaces whose
signatures use them. Infrastructure is an area like any other: local storage errors are owned by
`:common:storage:api`, and network errors will be owned by the network's `:api`.

```kotlin
sealed interface StorageError {                    // :common:storage:api, whatever backs the storage
    data object Full : StorageError
    data object Corrupted : StorageError
    data object Unavailable : StorageError
}

sealed interface NetworkError { … }                // the network's :api, when a remote backend exists

sealed interface PlaylistError {                   // :core:playlists:api, one sealed type per area
    data object NameBlank : PlaylistError
    data object NameTaken : PlaylistError
    data class Storage(val cause: StorageError) : PlaylistError
}
```

- **Infrastructure errors don't share a parent type.** Each one is its own sealed type. A repository
  whose operations can fail in more than one way uses an area error that wraps them.
- **Area errors wrap infrastructure errors rather than inherit from them.** A case such as
  `PlaylistError.Storage(cause)` keeps the original cause while the caller sees one type.
  (Inheriting isn't possible anyway: a sealed interface's subtypes must be in the same package and
  Gradle module.) Inside `either { }` blocks, use cases map errors with Arrow's `withError { }`.

**Where exceptions become errors.** The data layer is where exceptions are turned into typed errors.
It converts only the specific exceptions it knows how to name (for example `SQLiteFullException`
becomes `StorageError.Full`):
- `suspend` functions use `Either.catch` followed by mapping the exception;
- flows map each value to `Right`, and use `Flow.catch` to emit a `Left` for known exceptions and
  rethrow anything else.

Exceptions that aren't converted are bugs (`IllegalStateException`, a null pointer). They're
deliberately left to crash, so they show up in development and in crash reports instead of being
hidden inside a vague error.

**A `Left` in a flow ends that flow.** Once the upstream fails, the flow emits the error and
completes. Retrying is explicit: the repository uses `retryWhen`, or the ViewModel collects again.

**The UI layer.** ViewModels `fold` each `Either` into UI state, and map each error case to what the
user sees. `Either` and domain error types don't appear in UI state classes or composables.

**Code that can't fail doesn't use `Either`.** Pure in-memory functions, such as formatting or
mapping, return plain values. So does observing state that's read from memory or a local cache and
can't fail, such as the signed-in user (`AuthRepository.observeCurrentUser(): Flow<AuthUser?>`).

## Alternatives considered

- **Exceptions only.** Idiomatic Kotlin and no extra noise, but every caller has to know which
  functions throw. That's the problem this ADR exists to solve.
- **Typed errors only for failures the user can act on, exceptions for I/O failures.** Less noise:
  `setFavorite` would return `Unit`. It was rejected because deciding "which failures are expected"
  is a judgment call. It brings back the need to remember which functions throw, and it makes reads
  and writes behave differently.
- **Plain `Flow<T>` for reads, with failures caught in the ViewModel.** Less noise on observed data,
  but reads and writes would handle failure in two different ways, and a missing `catch` crashes.
- **`kotlin.Result` / `runCatching`.** The error type is always `Throwable`, and it catches
  `CancellationException` (see Context).
- **A catch-all error type (`Failure`, `Either<Throwable, T>`).** Satisfies the type checker but
  tells the caller nothing, and it quietly becomes the default. Allowing it would empty this ADR of
  meaning.
- **Our own sealed `Result` type.** No dependency, but we'd rewrite and maintain `map`, `flatMap`,
  `zip` and the builder syntax that Arrow already provides and tests.
- **A shared sealed parent for infrastructure errors (`FetchError`, with `DatabaseError` and
  `NetworkError` under it).** This was the original design. `Either` is covariant in its error type,
  so an `Either<DatabaseError, T>` could be used wherever an `Either<FetchError, T>` was expected.
  But a sealed parent forces all its subtypes into one module, so storage and network errors would
  share a module that neither owns. Nothing used the covariance, and wrapping errors in an area
  error (rule 3) already covers functions that can fail in more than one way.
- **Arrow's `Raise` with context parameters.** Less wrapping, but context parameters aren't stable
  in Kotlin yet. `Either` is stable, and the `either { }` builder already uses `Raise` internally.

## Consequences

- Every function that can fail says so, and says how, in its signature. The compiler makes callers
  handle the failure.
- Signatures and call sites are noisier, including simple writes such as `setFavorite`. That's
  accepted in exchange for stability.
- Arrow becomes a dependency of `:common:util`, of the `:api` modules with operations that can fail,
  and of the data layer and features. It's pure Kotlin, so the `:api` modules stay JVM modules.
- Error types need care: too few cases and the UI can't react differently, too many and every
  `when` grows. New cases are added when the UI needs to react differently, not ahead of time.
- The data layer needs small shared helpers to convert exceptions to errors (for `suspend`
  functions and flows), so each repository doesn't repeat the same `try`/`catch`. They're the
  `catchNamed` functions in `:common:util`.
- Fakes in each area's `:testing` module must be able to return `Left`, so tests cover the error
  paths as well as success.

## Rules

1. `[convention]` Repository and use case functions that can fail return `Either<E, T>`
   (`suspend`) or `Flow<Either<E, T>>` (observed).
2. `[convention]` `E` is a specific sealed error type. It's never `Throwable`, `Exception`, `Any`,
   `String`, or a catch-all type such as `Failure`.
3. `[convention]` Area errors wrap infrastructure errors in a case that holds the cause. They don't
   inherit from them.
4. `[convention]` The data layer converts only specific, named exceptions. Nothing catches
   `Throwable` or `Exception` in general.
5. `[enforced]` `CancellationException` is never swallowed (detekt `SuspendFunSwallowedCancellation`).
6. `[convention]` `kotlin.Result` and `runCatching` are not used in production code.
7. `[convention]` Errors aren't discarded: no `getOrNull()` or `getOrElse { default }` that ignores
   the error case without a comment explaining why.
8. `[convention]` ViewModels fold `Either` into UI state. `Either` and domain error types don't
   appear in UI state classes or composables.
