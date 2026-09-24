# ADR-008: Offline and cloud build flavors, with Firebase only in cloud

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox is public, and anyone cloning it should be able to build and run it straight away. The
optional remote backend from [ADR-002](002-offline-first-data-flow.md) is going to be Firebase,
starting with Firebase Auth for accounts. Firebase has two requirements that conflict with that:

- The `com.google.gms.google-services` Gradle plugin needs a `google-services.json` file for the
  Firebase project. The file identifies the maintainer's project, so it isn't committed
  (`app/.gitignore`). With the plugin applied and the file missing, the build fails.
- Firebase libraries pull in Google Play services. An app that is meant to work fully offline
  ([ADR-002](002-offline-first-data-flow.md), rule 9) shouldn't have to ship them.

CI has no Firebase config either, and shouldn't need a secret just to build a pull request.

`:core:auth` and `:core:sync` already hide their implementations behind interfaces
(`AuthRepository` in `:core:domain`, following [ADR-001](001-layered-architecture.md), and
`SyncManager` in `:core:sync`), with no-op implementations bound through Hilt
([ADR-005](005-hilt-dependency-injection.md)). What's missing is a way to choose between a build
with Firebase and one without it.

## Decision

We will add one product flavor dimension, `backend`, with two flavors:

| Flavor | Backend | Account features | Needs `google-services.json` |
| --- | --- | --- | --- |
| `offline` (default) | None: no Firebase or Play services code in the APK | Hidden | No |
| `cloud` | Firebase | Shown | Yes |

- **Every Android module gets the dimension.** `rolabox.android.application` and
  `rolabox.android.library` configure it through `configureBackendFlavors()`
  ([ADR-004](004-convention-plugins.md)), with `offline` marked as the default. Variant and task
  names are then the same in every module (`testOfflineDebugUnitTest`, `lintOfflineDebug`), and no
  module has to declare `missingDimensionStrategy`. JVM modules have no variants and aren't
  affected.
- **Code that differs lives in flavor source sets.** A data-layer module puts its
  backend-specific implementation and Hilt module in `src/offline` and `src/cloud`, and keeps what
  both flavors share in `src/main`. Both flavors declare the Hilt module under the same fully
  qualified name (for example `core.auth.di.AuthModule`), so one `@TestInstallIn(replaces = …)` in
  `:core:testing` replaces it in either flavor.
- **Offline binds no-op implementations.** `:core:auth` binds `SignedOutAuthRepository` in offline
  and `FirebaseAuthRepository` in cloud. `:core:sync` binds `NoOpSyncManager` in both flavors until
  there is data to sync. The cloud binding is where the real implementation will go.
- **Firebase is declared only in cloud configurations** (`cloudImplementation`, with the Firebase
  BoM), by the modules that use it.
- **The google-services plugin is applied through `rolabox.android.application.firebase`.** A
  Gradle plugin can't be applied to one flavor, so this convention plugin applies it to `:app` and
  disables its `process<Offline…>GoogleServices` tasks. When no `google-services.json` exists, it
  disables `:app`'s cloud variants so a fresh clone still passes `./gradlew check`. When a cloud
  app task is requested explicitly, it fails with instructions instead.
- **Account UI is a cloud-only dependency.** `:app` depends on `:feature:account` through
  `cloudImplementation`, and each flavor provides its own `HomeContent` in `app/src/<flavor>`.
  Offline builds can't reach account screens, because the code isn't on their classpath.
- **Offline is the default everywhere.** It's the flavor the IDE selects, and the flavor in the
  README build instructions. CI's main job builds, tests and lints it with no Firebase config, the
  way a fresh clone would.
- **CI also builds cloud, against a placeholder config.** A second CI job writes a placeholder
  `google-services.json` (it only has to match the package name), then builds, tests and lints the
  cloud flavor. Hilt checks `:app`'s graph at compile time, so this catches a cloud binding that's
  missing, which compiling the libraries alone wouldn't. Nothing contacts Firebase, so the job
  needs no secrets and runs on pull requests from forks.

## Alternatives considered

- **Configure Firebase first, and commit `google-services.json`.** The file isn't secret in the way
  an API secret is, but committing it points every clone at the maintainer's Firebase project and
  quota, and forks would build against it without noticing. It also leaves the offline APK with
  Play services, which it doesn't need.
- **One build, with Firebase off at runtime when the config is missing.** No flavors to learn, but
  Firebase and Play services still ship in every APK. Whether an app has accounts would then be
  a runtime state to test, not a build-time fact. The google-services plugin still has to be
  worked around when the file is missing.
- **Flavors only on the modules that differ (`:app`, `:core:auth`, `:core:sync`).** Fewer variants
  to build. But `:core:testing` and every other consumer of those modules would need
  `missingDimensionStrategy`, and task names would differ between modules (`testDebugUnitTest` in
  some, `testOfflineDebugUnitTest` in others), so a single CI command could silently skip modules.
- **Separate Firebase modules (`:core:auth-firebase`) added with `cloudImplementation`.** Keeps
  flavors out of libraries. But the no-op bindings would need their own offline-only module to
  avoid duplicate bindings, which doubles the module count for each backend-specific module.
  Flavor source sets hold the same split inside the module that owns the responsibility
  ([ADR-003](003-module-boundaries.md): split modules only when there's a reason).
- **A real `google-services.json` stored as a CI secret.** It would add nothing to a build check,
  since no test talks to Firebase. And secrets aren't available to pull requests from forks, so
  those PRs couldn't run the cloud job.
- **Always apply the google-services plugin, with `missingGoogleServicesStrategy = WARN`.** A cloud
  build without the file would then succeed and crash at startup when Firebase initializes. A
  build failure with instructions is better.

## Consequences

- A fresh clone builds, runs and passes `./gradlew check` with no Firebase setup, and CI needs no
  secrets.
- The offline APK contains no Firebase or Play services code, and CI checks this on every run.
- Every Android module builds twice as many variants. `./gradlew check` runs tests and detekt for
  both flavors. CI runs each flavor's tasks in its own parallel job, so total time grows little.
- CI proves the cloud flavor builds and its Hilt graph is complete, but not that it works against a
  real Firebase project. That still needs a manual run with real config.
- Backend-specific code has two homes per module (`src/offline`, `src/cloud`). Code that's the same
  in both must stay in `src/main`, or it will drift.
- The cloud flavor of `:app` can only be built with a `google-services.json` for a Firebase project
  that registers `com.eduardoflores.rolabox`. Without one, `:app` has no cloud variants.
- Choosing which Firebase product stores synced data (and so what the cloud `SyncManager` does) is
  left to a later ADR.

## Rules

1. `[enforced]` Every Android module has the `backend` flavor dimension with `offline` (the
   default) and `cloud` flavors, configured by the base convention plugins.
2. `[enforced]` Firebase dependencies (`com.google.firebase`) are declared only in cloud
   configurations such as `cloudImplementation`.
3. `[enforced]` The offline app's runtime classpath contains no Firebase or Play services
   (`com.google.android.gms`) artifacts. CI fails otherwise.
4. `[convention]` The google-services plugin is applied only through
   `rolabox.android.application.firebase`.
5. `[convention]` Code that differs between backends lives in `src/offline` and `src/cloud`, and
   everything else stays in `src/main`. Hilt modules that differ keep the same fully qualified
   name in both flavors.
6. `[convention]` Features that need the backend are `cloudImplementation` dependencies of `:app`,
   and main source sets never reference them.
7. `[convention]` `google-services.json` is never committed.

**Conformance.** Rules 1 and 2 are checked by the convention plugins and `ModuleRules.kt` when the
build is configured. Rule 3 is checked by the "Offline app has no Firebase" step in
[CI](../../.github/workflows/ci.yml).
