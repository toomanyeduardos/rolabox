# ADR-010: Firestore for synced data, with per-user security rules tested against the emulator

- **Status:** Accepted
- **Date:** 2026-09-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-28:** `SyncManager` was renamed `SyncRepository`, and the first synced data now
  lives in `users/{uid}/settings/preferences` ([ADR-011](011-preferences-sync.md)) (Decision, rule 7).
  Preferences are synced from DataStore, not Room, so rule 7 names local storage instead of Room.
  The `users/{uid}` placeholder is unchanged. The decision is unchanged.

## Context

[ADR-002](002-offline-first-data-flow.md) allows an optional remote backend for user data, and
[ADR-008](008-offline-and-cloud-flavors.md) made that backend Firebase but left open which Firebase
product stores synced data. That choice is needed now, together with how the stored data is
protected.

Rolabox is public, and the Firebase config can't be kept from anyone:

- The cloud APK contains the project ID and API key, and anyone can pull them out of it. Keeping
  `google-services.json` out of the repo (ADR-008, rule 7) stops forks from building against the
  maintainer's project, but it doesn't hide the config.
- A mobile client talks to Firebase directly, with no server in between. Anyone with the config can
  call the same APIs the app does, with any payload, while signed in as themselves.

That makes Firebase security rules the only real protection for the data. The app's own checks can
always be skipped, so the rules must enforce who may read and write each document and what a
valid document looks like.

Rules are code: they have conditions, helper functions and edge cases. Rules that are edited in the
console and never tested fail silently in one of two ways: they let in more than intended, or they
block the app. Firebase ships a local emulator that runs the real rules engine. It accepts
`demo-` project IDs, which need no Firebase project, credentials or network.

Nothing syncs yet (the cloud `SyncRepository` is still a no-op), so the data model is only a
placeholder for now.

## Decision

We will store synced user data in **Cloud Firestore**, and protect it with security rules that are
kept in the repo and tested against the emulator in CI.

- **All user data lives under `users/{uid}`.** Each user has one document, `users/{uid}`, and any
  later collections go under it. A signed-in user may read and write only the document whose ID
  is their own auth UID. There are no shared or top-level collections.
- **Deny by default.** A path is reachable only if a `match` block in the rules allows it.
  Subcollections under `users/{uid}` are denied too until they get their own `match` block, because
  a wildcard would bypass schema validation.
- **Every write is validated.** Each allowed document type has an allowlist of fields (`hasOnly`),
  plus type and size checks for each field. Unknown fields are rejected.
- **The `users/{uid}` schema is a placeholder** until the first sync ticket defines real data:
  `createdAt` (required: a timestamp equal to the server time on create, and never changed after
  that) and `displayName` (optional: a string of 1 to 100 characters). Later tickets change it
  along with its tests. The first synced data, preferences, lives in its own document,
  `users/{uid}/settings/preferences`, with its own `match` block
  ([ADR-011](011-preferences-sync.md)).
- **Rules live in `firebase/`:** `firestore.rules`, `firebase.json`, and a small Node project with
  the tests, which use `@firebase/rules-unit-testing` and Node's built-in test runner.
  `firebase-tools` is a pinned dev dependency, so local runs and CI use the same emulator version.
- **Tests run against a `demo-rolabox` project.** `npm test` starts the Firestore emulator with
  `firebase emulators:exec`, runs the tests, and stops the emulator. A `firestore-rules` job in CI
  does the same. It needs no secrets, so PRs from forks run it too.
- **Rules are deployed only from the repo:**
  `firebase deploy --only firestore:rules --project <id>`, from `firebase/`. The project ID is
  passed on the command line, and `.firebaserc` is git-ignored for the same reason as
  `google-services.json`.
- **Firestore is not the source of truth on the device.** Local storage (Room, or DataStore for
  preferences) stays the single source of truth (ADR-002). The cloud `SyncRepository` is the only code that reads and writes Firestore, and the UI
  never observes Firestore directly.

## Alternatives considered

- **Realtime Database.** Firebase's older database also has security rules, but it stores one big
  JSON tree. Its rules language checks fields one node at a time and has no `hasOnly`, so
  rejecting unknown fields and validating whole documents is clumsier. Firestore's per-document
  model fits "one user, a few collections" better, and its free tier is enough for this app.
- **A non-Firebase backend (Supabase, or our own API).** Row-level security or a server would work
  just as well, but auth is already Firebase (ADR-008). A second vendor would mean two consoles,
  two SDKs, and trusting one vendor's auth tokens in the other.
- **Validate in the app, and keep rules to ownership checks only.** Less code in the rules. But the
  client is public and anyone can call the API without it, so client-side validation protects
  nothing. The rules must hold the schema.
- **Cloud Functions as the only writer, with rules denying all client writes.** Server-side
  validation in a general-purpose language. But it's another deployed service, it needs the paid
  Blaze plan, it adds cold-start latency, and Firestore's offline writes would no longer work.
  That's a lot to add for the data this app has.
- **Rules tests on the JVM, calling the emulator's REST API from Gradle.** Keeps the repo
  Kotlin-only. But there is no official JVM library for rules tests: we'd write our own auth-token
  and request handling, and `firebase-tools` would still need Node to start the emulator anyway.
- **Edit rules in the Firebase console, with no tests.** Fastest to start. But the rules would have
  no history and no review, and nothing would catch a regression. That's the failure this ADR
  exists to prevent.

## Consequences

- Access control and validation are reviewed in PRs, and a regression fails CI before it can be
  deployed.
- The repo now has a Node project in `firebase/`, so contributors who work on rules need Node and a
  JDK 21+ for the emulator. Android work doesn't need either of them.
- CI has a third job. It's short, but it downloads the emulator on every run.
- Every new synced document type needs a rules change and tests in the same PR, which slows down
  adding data to sync. That's intended.
- Deny-by-default means a new collection won't work until its rules are written. Forgetting them
  shows up as `PERMISSION_DENIED` in development, not as an open database in production.
- Data can't be shared between users. Sharing would need a new ADR.
- Deploying the rules is still manual, and nothing checks that the deployed rules match the repo.

## Rules

1. `[enforced]` Any path without a `match` block in `firebase/firestore.rules` is denied, including
   subcollections under `users/{uid}`. The rules tests check this.
2. `[enforced]` A document under `users/{uid}` can be read or written only by the user whose auth
   UID is `{uid}`. The rules tests check this.
3. `[convention]` Each allowed document type has a field allowlist (`hasOnly`) and type and size
   checks for each field, with emulator tests for the allowed and denied cases, added in the same
   PR as the rule.
4. `[convention]` All synced data lives under `users/{uid}`. Shared or top-level collections need a
   new ADR.
5. `[convention]` Rules are changed only in `firebase/firestore.rules`, and deployed from there with
   the Firebase CLI, never edited in the console.
6. `[convention]` `.firebaserc` is never committed, just like `google-services.json` (ADR-008,
   rule 7).
7. `[convention]` Only the cloud `SyncRepository` reads or writes Firestore. Local storage (Room, or
   DataStore for preferences) stays the single source of truth on the device (ADR-002).

**Conformance.** Rules 1 and 2 are checked by `firebase/test/firestore.rules.test.mjs`, which the
`firestore-rules` job in [CI](../../.github/workflows/ci.yml) runs against the emulator.
