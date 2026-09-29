# ADR-008: One app with Firebase built in, and offline mode as a user choice

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-26:** Updated module and class names for the per-area layout in
  [ADR-003](003-module-boundaries.md): `:core:auth` is now `:core:auth:impl`, its Hilt module is
  `core.auth.impl.di.AuthModule`, `AuthRepository` is in `:core:auth:api`, `:core:sync` is split
  into `:core:sync:api` and `:core:sync:impl`, and the `@TestInstallIn` modules are in `:app`'s
  androidTest sources. The decision is unchanged.
- **Revised 2026-09-28:** `SyncManager` was renamed `SyncRepository`, and the cloud flavor now binds
  a real implementation ([ADR-011](011-preferences-sync.md)): `NoOpSyncRepository` in offline,
  `WorkManagerSyncRepository` in cloud. [ADR-010](010-firestore-security-rules.md) chose Firestore
  for synced data (Consequences). The decision is unchanged.
- **Revised 2026-09-28:** The `offline` and `cloud` build flavors are replaced by one app that
  always includes Firebase and the account features. Offline is now a choice the user makes in the
  app, not a build variant. The repo checks in a placeholder `google-services.json` that points at
  no real project, so every build works with no setup, and a real one stays local and git-ignored.
  Offline mode makes no Firebase requests, and this ADR now says who enforces that. The title and
  file name changed (was "Offline and cloud build flavors, with Firebase only in cloud",
  `008-offline-and-cloud-flavors.md`), and every section was rewritten. Why: Rolabox is one product
  with an optional account, and the flavors didn't match that. The default build never showed the
  sign-in flow, and every backend-specific module had two source sets and two CI jobs to keep in
  step. The two-flavor design is kept under Alternatives considered.
- **Revised 2026-09-29:** Conformance no longer says the Sign in screen is a placeholder, since email
  and password sign-in is built. The decision is unchanged.
- **Revised 2026-09-28:** The flavors are gone (`m1/flavor-removal`), so rules 1, 2, 4, 5, 6, 8 and
  10 now hold, and their tags say how each is checked. Rule 7 waits for the sign-in UI, which
  doesn't exist yet, so it points at that ticket now. Migration and Conformance describe what was
  done. The decision is unchanged.
- **Revised 2026-09-29:** Rule 7 is built, so it is no longer `[planned]`. Google sign-in turns
  offline mode off before the Credential Manager step ([ADR-009](009-ui-bound-sdks.md), rule 5),
  and `SignInFlowTest` covers it. Migration step 8 and
  Conformance now say so. The decision is unchanged.
- **Revised 2026-09-29:** A sign-in now turns offline mode off right before its first Firebase
  request, not before the provider's account picker, and turns it back on if the sign-in fails
  (Decision, rule 7, Migration step 8, Conformance). The picker runs while offline mode may still be
  chosen: it's a Play services call the user started, not a Firebase request, so rule 6 still holds.
  Why: signing in from offline mode is part of v1. With the old order, a user who cancelled the
  picker was left signed out with offline mode off, so the app opened on Sign in the next time.
  Every sign-in (Google, email and password, and creating an account) goes through the same rule,
  where before only Google sign-in turned offline mode off.

## Context

Rolabox is one product. A user can sign in, so their preferences follow them across devices
([ADR-011](011-preferences-sync.md)), or use the app without an account. That second path is the
"offline mode chosen" preference (`UserDataRepository.observeOfflineModeChosen`), set from the
"Use offline without an account" link on the Sign in screen. Both paths are in the same app, and
the user picks one at runtime.

The first version of this ADR modeled the two paths as two builds: an `offline` flavor with no
Firebase code, and a `cloud` flavor with Firebase and the account screens. That kept Firebase out of
a fresh clone's build, but it cost more than expected:

- **The default build hid the product.** `offline` was the default flavor in the IDE and in CI, and
  it never shows Sign in. The flow most users see first was only visible after adding a
  `google-services.json` and switching flavors.
- **Everything backend-specific existed twice.** `:app`, `:core:auth:impl` and `:core:sync:impl`
  each had `src/offline` and `src/cloud`, with Hilt modules declared twice under the same name.
  `ResolveStartDestinationUseCase` needed an `accountsAvailable` flag only because of the build.
- **CI and tasks doubled.** Every Android module built both flavors, and CI ran one job per flavor.

Firebase still has the constraints that led to the flavors:

- The `com.google.gms.google-services` Gradle plugin needs a `google-services.json`. Without one,
  the build fails.
- The file names a Firebase project. It isn't secret (it ships in every APK, see
  [ADR-010](010-firestore-security-rules.md)), but a committed real file would make every clone and
  fork build against the maintainer's project and quota without noticing.
- CI has no Firebase config, and shouldn't need a secret to build a pull request.

And the product adds one: a user who chose offline mode expects the app to stay off the network.
With Firebase always in the APK, that can't come from the build anymore. It has to come from the
code.

## Decision

We will build **one app**. Firebase, Play services and the account features are always in it.
Whether the app talks to Firebase is decided at runtime by the user's choice, and the app doesn't
know at build time whether it will be used offline.

**No product flavors.** The `backend` flavor dimension and the `offline` and `cloud` flavors are
removed from the convention plugins ([ADR-004](004-convention-plugins.md)). Each module has only the
`debug` and `release` build types, so task names are `testDebugUnitTest` and `lintDebug` everywhere.

**Firebase is an ordinary dependency.** Modules that use Firebase or Play services declare them with
`implementation`, like any other library. `:app` depends on `:feature:account` with
`implementation`. There are no `src/offline` or `src/cloud` source sets: code that differed by
flavor becomes one implementation in `src/main`. `:core:auth:impl` binds `FirebaseAuthRepository`,
and `:core:sync:impl` binds `WorkManagerSyncRepository`. `SignedOutAuthRepository`,
`NoOpSyncRepository` and the offline `SignInContent` go away.

**The start destination is decided at runtime.** `ResolveStartDestinationUseCase` loses its
`accountsAvailable` parameter:

- signed in → Home;
- offline mode chosen → Home;
- otherwise → Sign in.

**`google-services.json`: a committed placeholder, and an optional local real file.**

| File | Path | In git | Points at |
| --- | --- | --- | --- |
| Placeholder | `app/google-services.placeholder.json` | Yes | `demo-rolabox`, no real project |
| Real config | `app/google-services.json` | Never | The developer's own Firebase project |

- **The placeholder makes every build work.** It's a valid config for the package name
  `com.eduardoflores.rolabox`, with project ID `demo-rolabox`, an all-zero project number and app
  ID, and an API key that is obviously fake. Firebase reserves `demo-` project IDs for local
  emulators, so they can never belong to a real project. It's the same project ID the rules tests
  use ([ADR-010](010-firestore-security-rules.md)). It also has a fake web OAuth client, so the
  `default_web_client_id` resource that sign-in needs ([ADR-009](009-ui-bound-sdks.md)) is
  generated and the code compiles.
- **Its name keeps the two files apart.** The google-services plugin only looks for files named
  `google-services.json`, so it never picks up the placeholder by itself. And the placeholder
  doesn't match the `google-services.json` ignore pattern, so it stays tracked. A developer adding
  their real file creates a new, ignored file. They never edit a tracked one, so `git commit -a`
  can't publish it.
- **The build picks the real file when it's there.** The `rolabox.android.application.firebase`
  convention plugin checks for `app/google-services.json`. If it exists, the google-services plugin
  uses it as usual. If it doesn't, the convention plugin hands the placeholder to the
  `process<Variant>GoogleServices` tasks. It never copies the placeholder into `app/`, where it
  would look like a real config. The build logs which of the two it used. `app/google-services.json`
  is the only place the real file is read from: the build fails if it finds a
  `google-services.json` under `app/src/`, instead of silently ignoring it.
- **The real file is kept out of git twice.** `.gitignore` covers `google-services.json` at any
  path, and CI fails if any tracked file has that name. The ignore rule stops an accidental
  `git add`, and the CI check catches a forced one.
- **With the placeholder, the app works offline and sign-in fails.** Firebase initializes from the
  placeholder, so the app builds, starts, and works fully in offline mode. Any Firebase request
  fails, because there's no backend behind `demo-rolabox`. Sign-in shows an error on the Sign in
  screen, and sync never runs because nobody is signed in. To use accounts, a developer adds their
  own real file (README, "Building with Firebase").

**Offline mode makes no Firebase requests.** While offline mode is chosen, and while the user is
signed out, nothing in the app talks to Firebase: nothing is fetched, nothing is saved to the
cloud, and no calls are made or received. Preferences are still saved locally, to DataStore. The
code guarantees this in four places:

- **Sync checks before it runs.** `SyncRunner` runs its `Syncer`s only when the user is signed in
  and offline mode isn't chosen. It checks both, not just one. The sync triggers request no work
  in offline mode. Sync already does nothing while signed out ([ADR-011](011-preferences-sync.md)).
- **Offline mode and a signed-in user never coexist.** Offline mode can only be chosen while signed
  out, from the Sign in screen. A sign-in the user starts turns offline mode off right before its
  first Firebase request, and turns it back on if that sign-in fails, so a user who was offline
  stays offline. A provider's account picker that comes first, such as Credential Manager's
  ([ADR-009](009-ui-bound-sdks.md)), runs while offline mode may still be chosen, and cancelling it
  changes nothing. So Firebase Auth never holds a user in offline mode, and never refreshes a token.
- **Connections are created lazily.** Initializing the SDK isn't a request. `FirebaseInitProvider`
  creates `FirebaseApp` from the config at process start, and that only reads resources. Creating
  `FirebaseAuth` and reading its cached user, which the start destination and the sync triggers do,
  only reads local storage. Both are allowed in offline mode. Anything that can open a connection
  (today, `FirebaseFirestore`) is injected through `Provider` or `Lazy`, and created only by a sync
  that passed the checks above.
- **Only Firebase products that wait to be called.** Auth and Firestore make requests only when our
  code calls them. A product that sends data by itself (Analytics, Crashlytics, Cloud Messaging,
  Remote Config, Performance Monitoring) needs its own ADR, or a revision of this one, saying how it
  stays silent in offline mode.

Messages between the app and Google Play services on the device, such as checking that Play services
is available, aren't network requests and aren't covered. Credential Manager's Google sign-in does
reach the network, but it isn't a Firebase request, and it only runs when the user starts it. The
Firebase sign-in that follows it is what follows the sign-in rule above.

**One CI build.** CI builds, tests and lints the one app with the placeholder, with no secrets, so
pull requests from forks run everything. Hilt checks the full graph, Firebase bindings included,
on every run.

## Alternatives considered

- **Two build flavors, `offline` and `cloud` (this ADR's first version).** Chosen because it kept
  Firebase and Play services out of the default APK, let a fresh clone build with no config at all,
  and made "this build has no accounts" a fact of the classpath that CI could check. We changed
  because it doesn't match the product: Rolabox is one app where offline is a user choice. The
  default build hid the sign-in flow, backend code lived in two source sets per module, the start
  destination needed a build flag, and every module built and tested twice.
- **One app, with account features hidden when the placeholder config is detected at runtime.**
  Forks without a config would see no broken sign-in. But the app would behave differently depending
  on how it was built, which is the kind of split this revision removes, and "is this config real?"
  would be a runtime state to detect and test. A failing sign-in in a build with no backend is
  honest enough.
- **Commit the maintainer's real `google-services.json`.** No placeholder, and sign-in works in
  every clone. But every clone and fork would build against the maintainer's Firebase project and
  quota without noticing.
- **The placeholder at `app/google-services.json`, overwritten locally with the real one.** No
  convention plugin logic. But the real file would show up as a change to a tracked file, and one
  `git commit -a` would publish it. `git update-index --skip-worktree` hides the change, but it's
  per clone and easy to forget.
- **Generate the placeholder during the build instead of committing it.** Nothing extra in the
  repo. But its contents would be hidden in build code, and a committed file is easier to read,
  review and diff.
- **No placeholder: the build fails without a real file, or warns
  (`missingGoogleServicesStrategy`).** Keeps fake config out of the repo. But a fresh clone
  wouldn't build, or would crash at startup when Firebase initializes. The goal is that every build
  works from the IDE with no setup.
- **A real `google-services.json` stored as a CI secret.** It adds nothing to a build check, since
  no test talks to Firebase, and secrets aren't available to pull requests from forks.
- **Turn off Firebase's automatic initialization, and initialize it only when the user signs in.**
  Stricter: no Firebase code would run in offline mode at all. But every Firebase entry point would
  have to wait for initialization. And initializing makes no requests, so the rule "no requests in
  offline mode" holds without it.

## Consequences

- One app, one set of source sets, and one CI build. The sign-in flow is part of the default build
  in the IDE, and `accountsAvailable` is gone.
- **Firebase and Play services are always in the APK.** The APK and its method count grow for every
  user, including those who never sign in.
- **There's no network-SDK-free build anymore.** A build with no Google libraries (for example, for
  F-Droid) would need a new decision.
- **Every build carries a `google-services.json`**, the placeholder or a real one. A build from a
  fork without its own config starts and works offline, but sign-in fails with an error.
- **"Offline mode makes no Firebase requests" is now a property of the code, not of the build.** CI
  used to prove the offline app had no Firebase on its classpath. Now the guarantee rests on the
  checks in the sync and sign-in paths, their tests, and code review. A new Firebase call added
  without the checks would break it, and only a test or a reviewer would notice.
- Devices without Google Play services get the same APK. Offline mode doesn't use Play services, so
  it should work there, and sign-in won't. Nobody has tested it yet.
- CI still proves only that the app builds and its Hilt graph is complete, not that it works
  against a real Firebase project. That needs a manual run with a real config.
- Because the placeholder uses the `demo-rolabox` project, a later change could point debug builds
  at the Firebase emulator. That's not part of this decision.
- Which Firebase product stores synced data, and how `SyncRepository` syncs it, are decided in
  [ADR-010](010-firestore-security-rules.md) and [ADR-011](011-preferences-sync.md).

**Migration (done in `m1/flavor-removal`).** The steps that removed the flavors, kept for the
record:

1. Remove `configureBackendFlavors()` and `Flavors.kt` from the convention plugins, the flavor
   handling in `RolaboxModuleGraphConventionPlugin` and `ModuleGraphTask`, and the
   `isCloudConfiguration` check (old rule 2) from `ModuleRules.kt`. `unitTest` runs
   `testDebugUnitTest`.
2. Change every `cloudImplementation` to `implementation` (Firebase BoM, Firebase Auth, Firestore,
   WorkManager, `lifecycle-process`, `kotlinx-coroutines-play-services`, `:feature:account`).
3. Move `src/cloud` code into `src/main` in `:app`, `:core:auth:impl` and `:core:sync:impl`. Delete
   `src/offline`: `SignedOutAuthRepository`, `NoOpSyncRepository`, the offline `SignInContent` and
   the duplicate Hilt modules.
4. Remove `accountsAvailable` from `ResolveStartDestinationUseCase` and `StartupModule`, and update
   their tests.
5. Add `app/google-services.placeholder.json` (the CI job's current placeholder, with project ID
   `demo-rolabox` and a fake web OAuth client). Change `rolabox.android.application.firebase` to use
   `app/google-services.json` when it exists and the placeholder otherwise, log which one it used,
   and fail on a `google-services.json` under `app/src/`. Remove its offline-task and
   missing-config handling.
6. Add `google-services.json` to the root `.gitignore`, and a CI step that fails when
   `git ls-files '*google-services.json'` lists anything.
7. CI: drop the "Offline app has no Firebase" step and the `cloud` job, and change the build job's
   tasks to `assembleDebug` and `lintDebug`. The `firestore-rules` job is unchanged.
8. Make offline mode make no Firebase requests, with tests: `SyncRunner` checks offline mode as well
   as auth (test: offline mode, no `Syncer` runs), the triggers request nothing in offline mode
   (test), `FirebaseFirestore` is injected lazily (test: offline mode, never created), and starting
   a sign-in turns offline mode off before its first Firebase request, and back on if it fails
   (test). The last part was built with the Google sign-in UI (rule 7).
9. Update README ("Building the cloud flavor" becomes "Building with Firebase") and the
   conformance notes of ADR-002 and this ADR.

## Rules

1. `[enforced]` There are no product flavors. The convention plugins configure none, and the build
   fails if a module declares a flavor dimension.
2. `[convention]` Firebase, Play services and the account features are ordinary `implementation`
   dependencies. Code isn't split by backend: there are no `src/offline` or `src/cloud` source sets.
3. `[convention]` The google-services plugin is applied only through
   `rolabox.android.application.firebase`.
4. `[convention]` The committed placeholder is `app/google-services.placeholder.json`, and its
   project ID is `demo-rolabox`, never a real project. The build uses `app/google-services.json`
   when it exists, and the placeholder otherwise.
5. `[enforced]` `google-services.json` is never committed. `.gitignore` covers it at any path, and
   CI fails if a tracked file has that name.
6. `[convention]` While offline mode is chosen, or while the user is signed out, the app makes no
   Firebase requests. `SyncRunner` runs `Syncer`s only for a signed-in user who hasn't chosen
   offline mode, and the sync triggers request nothing in offline mode. Tests cover both.
7. `[convention]` Offline mode can only be chosen while signed out. Every sign-in the user starts
   turns it off before its first Firebase request, and turns it back on if the sign-in fails.
8. `[convention]` SDK objects that can open a connection (`FirebaseFirestore`) are injected through
   `Provider` or `Lazy`, and are never created in offline mode. Initializing `FirebaseApp` and
   reading `FirebaseAuth`'s cached user are allowed at any time.
9. `[convention]` Only Firebase products that make requests when our code calls them are used. A
   product that sends data by itself needs an ADR saying how it stays silent in offline mode.
10. `[convention]` The start destination is Home when signed in or when offline mode is chosen, and
    Sign in otherwise. No build setting changes it.

**Conformance.** Rule 1 is checked by the Android convention plugins when the build is configured
(`enforceNoProductFlavors` in `ModuleRules.kt`). Rule 5 is checked by the "No Firebase config
committed" step in [CI](../../.github/workflows/ci.yml). `rolabox.android.application.firebase`
picks the config (rule 4), and fails the build on a `google-services.json` under `app/src/`. Rule 6
is covered by `SyncTriggersTest` and `SyncRunnerTest` in `:core:sync:impl`, and rule 8 by
`SyncRunnerTest.offlineMode_neverCreatesFirestore`. They check the sync path, which is the only code
that calls Firebase today. A new Firebase call elsewhere isn't covered by any test. Rule 7 is
covered by `SignInUseCaseTest` and `SignUpUseCaseTest` in `:core:domain`, which every sign-in goes
through. Offline mode is chosen only from the Sign in screen, which is shown only while signed out.
