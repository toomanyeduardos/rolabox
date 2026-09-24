# ADR-005: Use Hilt for dependency injection

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

The layered design in [ADR-001](001-layered-architecture.md) depends on interfaces: features ask
for a repository interface declared in `:core:domain`, and a data-layer module provides the
implementation. Something has to connect the two across module boundaries. Tests need to replace
real implementations with fakes, from unit tests to instrumented tests of the whole app.
Future Android parts (ViewModels, WorkManager workers for sync, a media playback service) also need
their dependencies supplied.

## Decision

We will use **Hilt** (built on Dagger), with annotation processing through **KSP**, applied through
the `rolabox.hilt` convention plugin ([ADR-004](004-convention-plugins.md)).

- **Constructor injection by default.** Classes declare their dependencies with `@Inject`
  constructors. Field injection is only used where Android creates the object (activities,
  services), through `@AndroidEntryPoint`.
- **Bindings live next to their implementations.** Each module that implements an interface binds
  it with `@Binds` in a Hilt module in its own `di` package, installed in the appropriate component.
  The implementation class and its binding function can then stay `internal` (see `DataModule`).
- **The domain layer stays free of Hilt.** `:core:domain` uses only `javax.inject` annotations
  (`@Inject`) and declares no Hilt modules, so it stays pure Kotlin and testable without Hilt.
- **ViewModels** are annotated `@HiltViewModel` and obtained in Compose with `hiltViewModel()`.
- **Tests replace production bindings** with `@TestInstallIn` modules in `:core:testing`, which bind
  fakes in place of the production modules (see `TestModules.kt`). Unit tests of individual classes
  pass fakes through the constructor and don't need Hilt at all.
- **Coroutine dispatchers are injected** with the `@Dispatcher` qualifier from `:core:common`, so
  tests can replace them with test dispatchers.

## Alternatives considered

- **Manual dependency injection (an app-level container).** No library and no generated code, and
  Google's documentation shows how. But every new binding means editing the container by hand,
  replacing bindings in tests needs its own mechanism, and Jetpack integration (ViewModels, workers)
  is all hand-written.
- **Koin.** Easy to set up, with no code generation, and it works with Kotlin Multiplatform. But it
  resolves dependencies at runtime, so a missing binding shows up as a crash instead of a build
  error.
- **Dagger without Hilt.** The same checks when the app is built, but components, scopes and Android
  entry points all have to be defined by hand. Hilt is the standard set of those definitions.
- **kotlin-inject or Metro.** Newer tools that also check the graph at build time and are friendlier
  to Kotlin Multiplatform. They're not yet as widely used on Android as Hilt, and don't integrate
  with Jetpack (ViewModels, WorkManager, testing) as closely.

## Consequences

- A missing or duplicated binding fails the build, not the running app.
- Jetpack integrations come ready to use: `@HiltViewModel` now, and `@HiltWorker` for sync work later.
- Replacing whole modules in tests is built in, and `:core:testing` already uses it.
- Hilt is familiar to most Android developers, which lowers the cost of reading the codebase.
- Code generation adds build time (reduced by using KSP instead of kapt), and errors can be verbose.
- Hilt is Android-only. Moving to Kotlin Multiplatform would need a new ADR superseding this one.
- `:app` must be able to see every module that contributes bindings, so it's the composition root
  ([ADR-003](003-module-boundaries.md)).

## Rules

1. `[convention]` Dependencies are supplied through `@Inject` constructors. Field injection is used
   only in Android entry points.
2. `[convention]` Interfaces are bound with `@Binds` in a Hilt module in the implementing module's
   `di` package. Implementations are `internal` where possible.
3. `[enforced]` `:core:domain` depends only on `javax.inject` for injection, and contains no Hilt
   modules.
4. `[convention]` Tests replace production bindings with `@TestInstallIn` modules in `:core:testing`,
   not by editing production modules.
5. `[enforced]` Coroutine dispatchers are injected with `@Dispatcher`, and never referenced
   directly (such as `Dispatchers.IO`) outside `@Provides` functions (detekt `InjectDispatcher`).
6. `[convention]` Modules that need Hilt apply the `rolabox.hilt` convention plugin instead of
   adding Hilt dependencies by hand.
