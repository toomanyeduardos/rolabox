# ADR-029: Sync failures are logged, with the backend's reason where the exception is converted

- **Status:** Accepted
- **Date:** 2026-11-11
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Sync runs in WorkManager, out of the user's sight, and [ADR-011](011-preferences-sync.md) accepted
that its failures were invisible: no status in the UI, and no logging. A failed sync showed up only as
WorkManager's `Worker result FAILURE` line.

On 2026-11-10, the smoke test of R8 found that every sync failed within about 100 ms, in debug and
release builds, for every signed-in user. Nothing said why. `SyncRunner` folds each `SyncError` into
an outcome without reporting it, and the exception behind a `RemoteError` is dropped where the
`:network:impl` converts it ([ADR-007](007-error-handling.md)), so the reason was gone before anyone
could see it. The cause turned out to be outside the code: the Firebase project had no Firestore
database. Firestore answered `NOT_FOUND`, which the mapping didn't name, so the exception was
rethrown and WorkManager quietly recorded a failure.

A typed error says what to do about a failure (try again or not), not why it happened. The why is in
the exception, and only the code that converts it ever has it.

## Decision

We will log every sync failure to logcat, in every build type, in two places:

- **Why, where the exception is converted.** Each sync `:network:impl` logs, with `Log.w` and the
  exception, every exception it turns into a `RemoteError`. The line holds the backend's code (for
  Firestore, `FirebaseFirestoreException.Code`), and the exception carries the SDK's message and
  stack trace.
- **Which outcome, in the runner.** `SyncRunner` reports every `SyncError` a `Syncer` returns, with
  the outcome it gives the run (`Done`, `Retry` or `Failed`), through `SyncFailureLog`. Its
  production implementation writes one `Log.w` line. It's an interface so that `SyncRunnerTest` can
  check that no error goes unreported.

A line holds error types, backend codes and the SDK's message. It never holds the user ID, an email,
or a synced value. Error types are logged through their `toString()`, which R8 keeps, since it's
generated from string constants; class names are not, since R8 renames them.

Firestore's `NOT_FOUND` becomes `RemoteError.Rejected`. A document that doesn't exist reads as an
empty snapshot, so in a sync data source `NOT_FOUND` means the database itself is missing: a setup
mistake that trying again won't fix.

This covers sync only. Other places that convert exceptions into typed errors are left as they are.

## Alternatives considered

- **Keep sync silent (ADR-011 as accepted).** Nothing to maintain, but a failure costs a debugging
  session to explain, as this one did, and the same silence hides the next setup mistake.
- **Log in debug builds only.** Keeps release logcat quiet, but the failures that matter most happen
  in builds others install, and the lines hold nothing private.
- **Carry the exception or its message in `RemoteError`.** The runner could then log everything in
  one place. But `RemoteError` would hold an SDK type or a free-form string, which ADR-007 rule 2
  keeps out of error types, and equality between errors would depend on a message.
- **A sync status in the UI.** Shows the user that sync failed, but a user can't act on "the database
  doesn't exist". It may come later, for failures the user can act on.
- **Every conversion point in the data layer logs, not only sync's.** A cleaner rule, but it reaches
  DataStore, Room and Firebase Auth, which this change doesn't need. It's left for its own decision.

## Consequences

- A failed sync explains itself in logcat: a `FirestoreSync` line with the code and the SDK's message,
  and a `Sync` line with the error and the outcome.
- A local storage error is logged by type only (for example `Local(cause=Corrupted)`), since storage
  converts its exceptions outside sync, and this ADR doesn't reach there.
- Offline devices log a `Firestore failed with UNAVAILABLE` line each time sync runs and retries.
  That's accepted: it's rare, and it's a warning, not an error.
- `SyncRunner` has one more dependency, `SyncFailureLog`, bound in `SyncersModule`.
- ADR-011's consequence "Sync failures are invisible" no longer holds; ADR-011 is revised to say so.

## Rules

1. `[enforced]` `SyncRunner` reports every `SyncError` a `Syncer` returns to `SyncFailureLog`, with
   the run's outcome. `SyncRunnerTest` checks this.
2. `[convention]` Each sync `:network:impl` logs, with `Log.w` and the exception, every exception it
   converts into a `RemoteError`, at the place where it converts it.
3. `[convention]` A sync log line never holds the user ID, an email, or a synced value.
4. `[convention]` Sync logs are on in every build type.
