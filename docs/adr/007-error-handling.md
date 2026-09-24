# ADR-007: Typed errors with Arrow `Either` for every fallible operation

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Kotlin has only unchecked exceptions. Nothing in a function's signature says whether it can fail,
so callers have to *remember* which functions throw. In practice, that memory fails: a call that
"never throws" throws after a refactor, an unhandled exception reaches the UI, and the app crashes.
The fix is to put failure in the type, so the compiler makes every caller deal with it.

Kotlin's standard `Result` type doesn't solve this. Its error type is always `Throwable`, so it says
nothing about *which* failures can happen. Its companion `runCatching` also catches
`CancellationException`, which breaks coroutine cancellation.

Rolabox's failures fall into two groups:

- **Infrastructure failures:** local storage (full, corrupted) and, later, the network.
- **Area-specific failures:** a playlist name that's already taken, a track whose file is missing,
  a sign-in the user cancelled.

## Decision

We will use **Arrow's `Either`**, with a **specific sealed error type** on the left, for every
operation that can fail. That includes reads, writes and observed flows.

```kotlin
fun observeFavorites(): Flow<Either<DatabaseError, List<Track>>>
suspend fun setFavorite(id: TrackId): Either<DatabaseError, Unit>
suspend fun createPlaylist(name: String): Either<PlaylistError, PlaylistId>
```

A write with nothing to return succeeds with `Unit`. Every call site still unwraps the result and
handles the error. That's the point: no caller has to remember whether a function can fail.

**The error hierarchy.** Error types are sealed interfaces declared in `:core:domain`, next to the
repository interfaces whose signatures use them.

```kotlin
sealed interface FetchError                        // parent type: "couldn't get the data"

sealed interface DatabaseError : FetchError {      // local storage
    data object StorageFull : DatabaseError
    data object Corrupted : DatabaseError
}

sealed interface NetworkError : FetchError { … }   // when a remote backend exists

sealed interface PlaylistError {                   // one sealed type per area
    data object NameBlank : PlaylistError
    data object NameTaken : PlaylistError
    data class Storage(val cause: DatabaseError) : PlaylistError
}
```

- **Infrastructure errors share a parent type.** `Either` is covariant in its error type, so an
  `Either<DatabaseError, T>` can be used wherever an `Either<FetchError, T>` is expected.
- **Area errors wrap infrastructure errors rather than inherit from them.** A case such as
  `PlaylistError.Storage(cause)` keeps the original cause while the caller sees one type.
  (Inheriting isn't possible anyway: a sealed interface's subtypes must be in the same package and
  Gradle module.) Inside `either { }` blocks, use cases map errors with Arrow's `withError { }`.

**Where exceptions become errors.** The data layer is where exceptions are turned into typed errors.
It converts only the specific exceptions it knows how to name (for example `SQLiteFullException`
becomes `DatabaseError.StorageFull`):
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
mapping, return plain values.

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
- **Arrow's `Raise` with context parameters.** Less wrapping, but context parameters aren't stable
  in Kotlin yet. `Either` is stable, and the `either { }` builder already uses `Raise` internally.

## Consequences

- Every function that can fail says so, and says how, in its signature. The compiler makes callers
  handle the failure.
- Signatures and call sites are noisier, including simple writes such as `setFavorite`. That's
  accepted in exchange for stability.
- Arrow becomes a dependency of `:core:domain`, the data layer and features. It's pure Kotlin, so
  `:core:domain` stays a JVM module.
- Error types need care: too few cases and the UI can't react differently, too many and every
  `when` grows. New cases are added when the UI needs to react differently, not ahead of time.
- The data layer needs small shared helpers to convert exceptions to errors (for `suspend`
  functions and flows), so each repository doesn't repeat the same `try`/`catch`.
- Fakes in `:core:testing` must be able to return `Left`, so tests cover the error paths as well as
  success.

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
